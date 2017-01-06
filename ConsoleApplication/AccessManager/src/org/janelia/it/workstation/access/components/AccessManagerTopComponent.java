package org.janelia.it.workstation.access.components;

import java.awt.BorderLayout;

import org.janelia.it.jacs.integration.FrameworkImplProvider;
import org.janelia.it.workstation.access.gui.UserGroupManagerPanel;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * Top component for managing users and groups.
 */
@ConvertAsProperties(
        dtd = "-//org.janelia.it.workstation.access.components//AccessManager//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = AccessManagerTopComponent.TC_NAME,
        iconBase = "images/group.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "editor", openAtStartup = false)
@ActionID(category = "Window", id = "org.janelia.it.workstation.access.components.AccessManagerTopComponent")
@ActionReference(path = "Menu/Window/Core", position=6)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_AccessManagerAction",
        preferredID = AccessManagerTopComponent.TC_NAME
)
@Messages({
    "CTL_AccessManagerAction=User/Group Manager",
    "CTL_AccessManagerTopComponent=User/Group Manager",
    "HINT_AccessManagerTopComponent=Management of users and groups"
})
public final class AccessManagerTopComponent extends TopComponent {

    public static final String TC_NAME = "AccessManagerTopComponent";

    private UserGroupManagerPanel mgrPanel;
    
    public AccessManagerTopComponent() {
        initComponents();
        setName(Bundle.CTL_AccessManagerTopComponent());
        setToolTipText(Bundle.HINT_AccessManagerTopComponent());

        mgrPanel = new UserGroupManagerPanel();
        add(mgrPanel, BorderLayout.CENTER);
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    @Override
    public void componentOpened() {
        try {
            mgrPanel.refresh();
        }
        catch (Exception e) {
            FrameworkImplProvider.handleException("Error getting user and groups", e);
        }
    }

    @Override
    public void componentClosed() {

    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }
}
