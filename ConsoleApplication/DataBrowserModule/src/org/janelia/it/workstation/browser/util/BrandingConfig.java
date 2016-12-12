package org.janelia.it.workstation.browser.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.janelia.it.jacs.shared.utils.StringUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.Places;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NetBeans provides a way for a user to keep a customized properties file which holds certain 
 * startup configuration options. By default, NetBeans will use the ${APPNAME}.conf file which is 
 * provided with the application distribution. The Workstation's version of this file is found 
 * at ConsoleApplication/harness/etc/app.conf
 * 
 * Within the Workstation, we provide a way for the user to change their max memory setting, 
 * which must be done in a customized ${APPNAME}.conf within the netbeans user directory.
 * 
 * This class is responsible for the following:
 * 
 * 1) When a user sets a custom setting override, this class will update the customized ${APPNAME}.conf
 *    file. If there is no customized file, then it copies over the system-level settings file first.
 * 2) Reads the custom settings whenever requested by the application or the user.
 * 3) Keeps the settings in sync when the system setting default are updated. During development, we might 
 *    change or add a system default. These types of changes must make it to each users' custom settings file, 
 *    without trashing their customized settings.
 *
 * This builds on earlier work by Les Foster, which lived in the SystemInfo class, and took care of 1 and 2, 
 * but not 3. This class attempts to implement 3, as well as abstract all access to the various levels of 
 * app configuration properties files. 
 * 
 * NOTE: This doesn't work as expected in development, because both system and custom files point to the same 
 * location. This only works if the application has been installed via one of the installers created by the build. 
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class BrandingConfig {

    private static final Logger log = LoggerFactory.getLogger(BrandingConfig.class);

    // Singleton
    private static BrandingConfig instance;
    public static synchronized BrandingConfig getBrandingConfig() {
        if (instance==null) {
            instance = new BrandingConfig();
        }
        return instance;
    }

    public static final String appnameToken = "JaneliaWorkstation";  // TODO: Get this from NetBeans framework somehow
    
    private static final String NETBEANS_IDE_SETTING_NAME_PREFIX = "netbeans_";
    private static final String MEMORY_SETTING_PREFIX = "-J-Xmx";
    private static final String DEFAULT_OPTIONS_PROP = "default_options";
    private static final String ETC_SUBPATH = "etc";

    private final Map<String,String> systemSettings = new LinkedHashMap<>();
    private final Map<String,String> brandingSettings = new LinkedHashMap<>();
    
    private final boolean devMode;
    private File fqBrandingConfig; 
    private int maxMemoryMB = -1;
    private boolean needsRestart = false;
    
    private BrandingConfig() {
        this.devMode = Places.getUserDirectory().toString().contains("testuserdir");
        if (devMode) {
            // TODO: It would be nice to get this working in development, but NetBeans does things very differently in dev mode, 
            // and it's not clear if it's even possible to have a branding config. Maybe someday we'll investigate this further. 
            log.info("Branding config is disabled in development. Memory preference will have no effect. "
                    + "To change the max memory in development, temporarily edit the nbproject/project.properties file directly.");
        }
        else {
            loadSystemConfig();
            loadBrandingConfig();
        }
    }

    /**
     * This loads the default configuration file.
     */
    private final void loadSystemConfig() {
        try {
            // Find the current bona-fide production config, which is copied from harness/etc/app.conf 
            // to ConsoleWrapper/release/config by build-runner.xml
            final String configFile = "config/app.conf";
            File sysWideConfig = InstalledFileLocator.getDefault().locate(configFile, "org.janelia.it.workstation", false);
            log.debug("Trying system config at {}", sysWideConfig);
                        
            if (sysWideConfig != null && sysWideConfig.canRead()) {
                loadProperties(sysWideConfig, systemSettings);
                log.info("Loaded {} properties from {}", systemSettings.size(), sysWideConfig);
            }
            else {
                log.error("Error locating system configuration in resources directory: "+configFile);
            }
        }
        catch (IOException e) {
            throw new IllegalStateException("Error loading system configuration", e);
        }
    }
    
    /**
     * This loads the user-customized configuration file.
     */
    private final void loadBrandingConfig() {
        
        try {
            File userSettingsDir = new File(Places.getUserDirectory(), ETC_SUBPATH);
            if (!userSettingsDir.exists()) {
                userSettingsDir.mkdirs();
            }
            final String configFile = appnameToken + ".conf";
            this.fqBrandingConfig = new File(userSettingsDir, configFile);
            log.debug("Trying branding config at {}", fqBrandingConfig);
    
            if (fqBrandingConfig.exists()) {
                loadProperties(fqBrandingConfig, brandingSettings);
                log.info("Loaded {} properties from {}", brandingSettings.size(), fqBrandingConfig);
                loadBrandingMemorySetting();
            }
        }
        catch (IOException e) {
            log.error("Error loading branding config",e);
        }
    }
    
    private final void loadBrandingMemorySetting() {
        String javaMemoryOption = getJavaMemOption();
        if (javaMemoryOption==null) return;
        log.info("Found existing memory option: "+javaMemoryOption);
        final int numberEndPt = javaMemoryOption.length() - 1;
        char rangeIndicator = javaMemoryOption.charAt( numberEndPt );
        final int numberStartPt = MEMORY_SETTING_PREFIX.length();
        if (rangeIndicator != 'm') {
            // Default of 8 GB 
            this.maxMemoryMB = 8 * 1024;
        }
        else {
            this.maxMemoryMB = Integer.parseInt(javaMemoryOption.substring(numberStartPt, numberEndPt));
        }
        log.info("Loaded existing branding memory setting: "+maxMemoryMB);
    }

    public boolean isNeedsRestart() {
        return needsRestart;
    }

    private final String getJavaMemOption() {
        String defaultOptions = brandingSettings.get(DEFAULT_OPTIONS_PROP);
        String[] defaultOptionsArr = defaultOptions == null ? new String[0] : defaultOptions.split(" ");
        for (String defaultOption : defaultOptionsArr) {
            if (defaultOption.startsWith(MEMORY_SETTING_PREFIX)) {
                return defaultOption;
            }
        }
        return null;
    }
    
    /**
     * This method should be called when the application starts in order to ensure that the branding configuration is
     * valid and synchronized. 
     */
    public void validateBrandingConfig() {

        if (devMode) return;
        
        log.info("Validating branding configuration...");
        
        try {
            boolean dirty = false;
            
            for(String systemKey : systemSettings.keySet()) {
                
                String brandingKey = systemKey;
                if (brandingKey.startsWith(NETBEANS_IDE_SETTING_NAME_PREFIX)) {
                    brandingKey = brandingKey.substring(NETBEANS_IDE_SETTING_NAME_PREFIX.length());
                }
                
                String systemValue = systemSettings.get(systemKey);
                String brandingValue = brandingSettings.get(brandingKey);
                
                if (DEFAULT_OPTIONS_PROP.equals(systemKey)) {
                    // Default options are treated differently than most. We take the system setting for everything except the 
                    // max memory, which can be customized by the user. 
                    // TODO: In the future, it would be nice to support customization of any property, but it requires rather 
                    // complicated command-line option parsing.
                    if (syncDefaultOptions(maxMemoryMB)) {
                        dirty = true;
                    }
                }
                else {
                    if (brandingValue==null) {
                        // For most options, we just ensure they're present.
                        log.info("Updating branding config for {}={} to {}", brandingKey, brandingValue, systemValue);
                        brandingSettings.put(brandingKey, systemValue);
                        dirty = true;
                    }   
                    else if (!StringUtils.areEqual(brandingValue, systemValue)) {
                        // We allow customized options. 
                        // TODO: Perhaps it would make sense to track if the default changes?
                        log.info("Branding config has customized value for {}={} (default: {})", brandingKey, brandingValue, systemValue);
                    }
                }   
            }
            
            if (dirty) {
                saveBrandingConfig();
            }

            log.info("Branding configuration validated.");
        }
        catch (Exception e) {
            log.error("Error validating branding config",e);
        }
    }

    private boolean syncDefaultOptions(int maxMemoryMB) {

        String systemValue = systemSettings.get(DEFAULT_OPTIONS_PROP);
        String brandingValue = brandingSettings.get(DEFAULT_OPTIONS_PROP);
        String customDefaultOpts = systemValue;
        
        // What should the default options be?
        if (maxMemoryMB > 0) {
            int optStart = systemValue.indexOf(MEMORY_SETTING_PREFIX) + MEMORY_SETTING_PREFIX.length();
            int optEnd = systemValue.indexOf(" ", optStart);
            if (optEnd<0) {
                optEnd = systemValue.indexOf("\"", optStart);
                if (optEnd<0) {
                    optEnd = systemValue.length();
                }
            }
            customDefaultOpts = systemValue.substring(0, optStart) + maxMemoryMB + "m" + systemValue.substring(optEnd);
        }

        if (!StringUtils.areEqual(brandingValue, customDefaultOpts)) {
            log.info("Updating branding config for {}={} to {}", DEFAULT_OPTIONS_PROP, brandingValue, customDefaultOpts);
            brandingSettings.put(DEFAULT_OPTIONS_PROP, customDefaultOpts);
            // If the branding value has changed, we'll need to restart to pick it up.
            if (brandingValue!=null) {
                needsRestart = true;
            }
            return true;
        }
        else {
            log.info("Branding config aready has correct default options");
            return false;
        }
    }

    public int getMemoryAllocationGB() {
        // Stored as megabytes. Presented to user as gigabytes.
        if (maxMemoryMB<0) return maxMemoryMB;
        log.debug("Got memory allocation = {} MB", maxMemoryMB);
        return maxMemoryMB / 1024;
    }
    
    public void setMemoryAllocationGB(int maxMemoryGB) throws IOException {
        int maxMemoryMB  = maxMemoryGB * 1024;
        if (fqBrandingConfig!=null) {
            if (syncDefaultOptions(maxMemoryMB)) {
                saveBrandingConfig();
            }
        }
        this.maxMemoryMB = maxMemoryMB;
        log.debug("Set memory allocation = {} MB", maxMemoryMB);
    }
    
    private void saveBrandingConfig() throws IOException {

        if (!fqBrandingConfig.getParentFile().exists()) {
            fqBrandingConfig.getParentFile().mkdir();
        }

        try (PrintWriter outfileWriter = new PrintWriter(new FileWriter(fqBrandingConfig))) {
            for(String brandingKey : brandingSettings.keySet()) {
                String brandingValue = brandingSettings.get(brandingKey);
                outfileWriter.print(brandingKey);
                outfileWriter.print("=");
                outfileWriter.println(brandingValue);
            }
        }
        
        log.info("Wrote updated branding config to {}", fqBrandingConfig);
    }
    
    private void loadProperties(File infile, Map<String,String> map) throws IOException {
        Properties props = new Properties();
        if (infile.exists()) {
            props.load(new FileInputStream(infile));
        }
        for (final String name: props.stringPropertyNames()) {
            map.put(name, props.getProperty(name));
        }
    }
}