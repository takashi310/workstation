package org.janelia.it.FlyWorkstation.gui.application;

import org.janelia.it.FlyWorkstation.api.entity_model.management.ModelMgr;
import org.janelia.it.FlyWorkstation.api.facade.concrete_facade.ejb.EJBFacadeManager;
import org.janelia.it.FlyWorkstation.api.facade.facade_mgr.FacadeManager;
import org.janelia.it.FlyWorkstation.gui.framework.console.ConsoleMenuBar;
import org.janelia.it.FlyWorkstation.gui.framework.exception_handlers.ExitHandler;
import org.janelia.it.FlyWorkstation.gui.framework.exception_handlers.UserNotificationExceptionHandler;
import org.janelia.it.FlyWorkstation.gui.framework.pref_controller.PrefController;
import org.janelia.it.FlyWorkstation.gui.framework.session_mgr.SessionMgr;
import org.janelia.it.FlyWorkstation.gui.util.ConsoleProperties;
import org.janelia.it.FlyWorkstation.gui.util.PathTranslator;
import org.janelia.it.FlyWorkstation.gui.util.SystemInfo;
import org.janelia.it.FlyWorkstation.gui.util.panels.ApplicationSettingsPanel;
import org.janelia.it.FlyWorkstation.gui.util.panels.DataSourceSettings;
import org.janelia.it.FlyWorkstation.gui.util.server_status.ServerStatusReportManager;
import org.janelia.it.FlyWorkstation.shared.util.Utils;

import javax.swing.*;
import java.util.MissingResourceException;

/**
 * Created by IntelliJ IDEA.
 * User: saffordt
 * Date: 2/8/11
 * Time: 12:10 PM
 * This is the main
 */
public class ConsoleApp {

    static {
        System.out.println("Java version: " + System.getProperty("java.version"));
        java.security.ProtectionDomain pd = ConsoleApp.class.getProtectionDomain();
        System.out.println("Code Source: " + pd.getCodeSource().getLocation());
        // Establish some OS-specific stuff
        // Set these, Mac may use - // take the menu bar off the jframe
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        // set the name of the application menu item
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", ConsoleProperties.getString("console.Title"));
    }

    public static void main(final String[] args) {
        newBrowser();
    }

    private static void newBrowser() {
        // Show the Splash Screen
//        final SplashScreen splash = new SplashScreen();
//        splash.setStatusText("Initializing Application...");
//        splash.setVisible(true);

        // Prime the tool-specific properties before the Session is invoked
        ConsoleProperties.load();
        final SessionMgr sessionMgr = SessionMgr.getSessionMgr();
        try {
            //Browser Setup
            final String versionString = ConsoleProperties.getString("console.versionNumber");
            final boolean internal = (versionString != null) && (versionString.toLowerCase().indexOf("internal") > -1);

            sessionMgr.setNewBrowserTitle(ConsoleProperties.getString("console.Title") + " " + ConsoleProperties.getString("console.versionNumber"));
            sessionMgr.setApplicationName(ConsoleProperties.getString("console.Title"));
            sessionMgr.setApplicationVersion(ConsoleProperties.getString("console.versionNumber"));
            sessionMgr.setNewBrowserImageIcon(Utils.getClasspathImage("flyscope.jpg"));
            sessionMgr.setNewBrowserSize(.8f);
            sessionMgr.setNewBrowserMenuBar(ConsoleMenuBar.class);
            sessionMgr.startExternalHttpListener(30000);
            sessionMgr.startAxisServer(30001);
            sessionMgr.setModelProperty("ShowInternalDataSourceInDialogs", new Boolean(internal));

            //Exception Handler Registration
//            sessionMgr.registerExceptionHandler(new PrintStackTraceHandler());
            sessionMgr.registerExceptionHandler(new UserNotificationExceptionHandler());
            sessionMgr.registerExceptionHandler(new ExitHandler()); //should be last so that other handlers can complete first.
        	
            // Protocol Registration - Adding more than one type should automatically switch over to the Aggregate Facade
            final ModelMgr modelMgr = ModelMgr.getModelMgr();
            modelMgr.registerFacadeManagerForProtocol(FacadeManager.getEJBProtocolString(), EJBFacadeManager.class, "JACS EJB Facade Manager");
            
            // Model Observers
            modelMgr.addModelMgrObserver(sessionMgr.getAxisServer());
            
            // Editor Registration
            //      sessionMgr.registerEditorForType(api.entity_model.model.genetics.Species.class,
            //        client.gui.components.assembly.genome_view.GenomeView.class,"Genome View", "ejb");
            //      sessionMgr.registerEditorForType(api.entity_model.model.assembly.GenomicAxis.class,
            //        client.gui.components.annotation.debug_view.DebugView.class,"Annotation Debug View", "ejb");
//            splash.setStatusText("Initializing Visualization Components...");
//            final Class vizardEditor = client.gui.components.annotation.axis_annotation.GenomicAxisAnnotationEditor.class;
//            // OMIT for CONVERSION
//            sessionMgr.registerEditorForType(
//                    api.entity_model.model.assembly.GenomicAxis.class,
//                    vizardEditor, "Genomic Axis Annotation", "xmlgenomicaxis", true);

//            final Class[] editorClasses = new Class[]{vizardEditor};
//            Class editorClass;
//            for (int i = 0; i < editorClasses.length; i++) {
//                editorClass = editorClasses[i];
//                //Sub-Editor Registration
////                sessionMgr.registerSubEditorForMainEditor(editorClass,
////                        client.gui.components.annotation.consensus_sequence_view.ConsensusSequenceView.class);
//            }

            // This is for Preference Controller panels
//            sessionMgr.registerPreferenceInterface(
//                    client.gui.other.panels.BackupPanel.class,
//                    client.gui.other.panels.BackupPanel.class);
            sessionMgr.registerPreferenceInterface(ApplicationSettingsPanel.class, ApplicationSettingsPanel.class);
//            sessionMgr.registerPreferenceInterface(
//                    client.gui.other.panels.ViewSettingsPanel.class,
//                    client.gui.other.panels.ViewSettingsPanel.class);
            sessionMgr.registerPreferenceInterface(DataSourceSettings.class, DataSourceSettings.class);
//            sessionMgr.registerPreferenceInterface(
//                    client.gui.other.panels.TransTransPanel.class,
//                    client.gui.other.panels.TransTransPanel.class);
//
//            sessionMgr.registerPreferenceInterface(
//                    GroupSettingsPanel.class,
//                    GroupSettingsPanel.class);


            ServerStatusReportManager.getReportManager().startCheckingForReport();

//            sessionMgr.setSplashPanel(new SplashPanel());

//            splash.setStatusText("Connecting to Remote Data Sources...");
            FacadeManager.addProtocolToUseList(FacadeManager.getEJBProtocolString());
//            FacadeManager.addProtocolToUseList("sage");

//            splash.setVisible(false);
            if (null==sessionMgr.getModelProperty(SessionMgr.USER_NAME)  || "".equals(sessionMgr.getModelProperty(SessionMgr.USER_NAME))
                /*&& modelMgr.getNumberOfLoadedGenomeVersions() == 0*/) {
                final int answer = JOptionPane.showConfirmDialog(null, "Please enter your login information.", "Information Required", JOptionPane.OK_CANCEL_OPTION);
                if (answer != JOptionPane.CANCEL_OPTION) {
                    PrefController.getPrefController().getPrefInterface(DataSourceSettings.class, null);
                }
            }

        	// Make sure we can access the data mount
        	if (!PathTranslator.isMounted()) {
        		String message = SystemInfo.isMac ? 
        					"The jacsData volume is not mounted. From Finder choose 'Go' and 'Connect to Server' " +
        					"then enter '"+PathTranslator.JACS_DATA_MOUNT_MAC+"' and press Connect."
        				  : "The data is not mounted as "+PathTranslator.JACS_DATA_PATH_LINUX;
        		throw new MissingResourceException(message, ConsoleApp.class.getName(), "Missing Data Mount");
        	}
        	
            //Start First Browser
            sessionMgr.newBrowser();
            
//            splash.setStatusText("Connected.");
            
        }
        catch (Exception ex) {
            SessionMgr.getSessionMgr().handleException(ex);
        }
        finally {
//            splash.setVisible(false);
//            splash.dispose();
        }
    }
}
