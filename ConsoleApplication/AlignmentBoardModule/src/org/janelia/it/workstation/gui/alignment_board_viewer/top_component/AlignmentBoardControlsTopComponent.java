/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.janelia.it.workstation.gui.alignment_board_viewer.top_component;

import java.awt.BorderLayout;
import java.util.Properties;
import javax.swing.GroupLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import org.janelia.it.workstation.gui.alignment_board.ab_mgr.AlignmentBoardMgr;
import org.janelia.it.workstation.gui.alignment_board_viewer.LayersPanel;
import org.janelia.it.workstation.gui.alignment_board_viewer.gui_elements.AlignmentBoardControlsPanel;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//org.janelia.it.workstation.gui.alignment_board_viewer.top_component//AlignmentBoardControls//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "AlignmentBoardControlsTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "properties", openAtStartup = false)
@ActionID(category = "Window", id = "AlignmentBoardControlsTopComponent")
@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_AlignmentBoardControlsAction",
        preferredID = "AlignmentBoardControlsTopComponent"
)
@Messages({
    "CTL_AlignmentBoardControlsAction=Alignment Board Controls",
    "CTL_AlignmentBoardControlsTopComponent=Alignment Board Controls",
    "HINT_AlignmentBoardControlsTopComponent=Modify alignment board characteristics"
})
public final class AlignmentBoardControlsTopComponent extends TopComponent {

    private Logger logger = LoggerFactory.getLogger( AlignmentBoardControlsTopComponent.class );
    private AlignmentBoardControlsPanel cPanel;
    private JSplitPane splitPane;
            
    public AlignmentBoardControlsTopComponent() {
        initComponents();
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent( new JLabel( "" ));
        splitPane.setBottomComponent( new JLabel( "" ));
        jPanel1.add( splitPane, BorderLayout.CENTER );

        setName(Bundle.CTL_AlignmentBoardControlsTopComponent());
        setToolTipText(Bundle.HINT_AlignmentBoardControlsTopComponent());
        putClientProperty(TopComponent.PROP_CLOSING_DISABLED, Boolean.FALSE);
        putClientProperty(TopComponent.PROP_DRAGGING_DISABLED, Boolean.FALSE);
        putClientProperty(TopComponent.PROP_MAXIMIZATION_DISABLED, Boolean.TRUE);
        putClientProperty(TopComponent.PROP_UNDOCKING_DISABLED, Boolean.TRUE);
        putClientProperty(TopComponent.PROP_KEEP_PREFERRED_SIZE_WHEN_SLIDED_IN, Boolean.FALSE);

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();

        setPreferredSize(new java.awt.Dimension(0, 0));

        jPanel1.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 300, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel jPanel1;
    // End of variables declaration//GEN-END:variables
    @Override
    public void componentOpened() {
        final LayersPanel layersPanel = AlignmentBoardMgr.getInstance().getLayersPanel();
        try {
            layersPanel.activate();
            splitPane.setTopComponent( layersPanel );
            this.validate();
            this.repaint();
        } catch ( Throwable th ) {
            logger.warn("Failed ot activate layers panel.  Not opening component.");
        }
    }

    @Override
    public void componentClosed() {
        splitPane.setTopComponent( null );
        splitPane.setBottomComponent( null );
        AlignmentBoardMgr.getInstance().getLayersPanel().deactivate();
        if ( cPanel != null )
            cPanel.dispose();
    }
    
    public void setControls( AlignmentBoardControlsPanel cPanel ) {
        this.cPanel = cPanel;
        splitPane.setBottomComponent(cPanel);
        this.validate();
        this.repaint();
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
