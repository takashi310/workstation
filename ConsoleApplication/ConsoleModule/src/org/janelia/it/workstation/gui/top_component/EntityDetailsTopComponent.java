/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.janelia.it.workstation.gui.top_component;

import java.awt.BorderLayout;
import java.util.Properties;

import org.janelia.it.workstation.gui.framework.session_mgr.SessionMgr;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;

import javax.swing.GroupLayout;
import javax.swing.JPanel;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//org.janelia.it.workstation.gui.dialogs.nb//EntityDetails//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "EntityDetailsTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "appExplorerBtm", openAtStartup = true, position = 100)
@ActionID(category = "Window", id = "org.janelia.it.workstation.gui.dialogs.nb.EntityDetailsTopComponent")
@ActionReference(path = "Menu/Window", position = 100 )
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_EntityDetailsAction",
        preferredID = "EntityDetailsTopComponent"
)
@Messages({
    "CTL_EntityDetailsAction=Data Inspector",
    "CTL_EntityDetailsTopComponent=Data Inspector",
    "HINT_EntityDetailsTopComponent=See data details"
})
public final class EntityDetailsTopComponent extends TopComponent {

    public EntityDetailsTopComponent() {
        initComponents();
        setName(Bundle.CTL_EntityDetailsTopComponent());
        setToolTipText(Bundle.HINT_EntityDetailsTopComponent());
        jPanel1.setLayout( new BorderLayout() );
        jPanel1.add( SessionMgr.getBrowser().getEntityDetailsOutline(), BorderLayout.CENTER );
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new JPanel();

        GroupLayout jPanel1Layout = new GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, GroupLayout.Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, GroupLayout.Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JPanel jPanel1;
    // End of variables declaration//GEN-END:variables
    @Override
    public void componentOpened() {
        // TODO add custom code on component opening
    }

    @Override
    public void componentClosed() {
        // TODO add custom code on component closing
    }

    void writeProperties(Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }
}
