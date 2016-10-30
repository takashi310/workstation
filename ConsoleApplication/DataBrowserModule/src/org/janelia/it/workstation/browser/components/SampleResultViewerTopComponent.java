package org.janelia.it.workstation.browser.components;

import java.awt.BorderLayout;
import java.util.concurrent.Callable;

import javax.swing.JComponent;

import org.janelia.it.jacs.model.domain.interfaces.HasFileGroups;
import org.janelia.it.jacs.model.domain.sample.PipelineResult;
import org.janelia.it.jacs.model.domain.sample.SampleAlignmentResult;
import org.janelia.it.jacs.model.domain.sample.SampleProcessingResult;
import org.janelia.it.jacs.shared.utils.StringUtils;
import org.janelia.it.workstation.browser.ConsoleApp;
import org.janelia.it.workstation.browser.events.Events;
import org.janelia.it.workstation.browser.gui.editor.FileGroupEditorPanel;
import org.janelia.it.workstation.browser.gui.editor.NeuronSeparationEditorPanel;
import org.janelia.it.workstation.browser.gui.editor.SampleResultEditor;
import org.janelia.it.workstation.browser.gui.find.FindContext;
import org.janelia.it.workstation.browser.gui.find.FindContextActivator;
import org.janelia.it.workstation.browser.gui.find.FindContextManager;
import org.janelia.it.workstation.browser.gui.support.MouseForwarder;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.windows.TopComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top component which displays neuron separations for a single result. 
 * 
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
@ConvertAsProperties(
        dtd = "-//org.janelia.it.workstation.browser.components//SampleResultViewer//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = SampleResultViewerTopComponent.TC_NAME,
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "editor3", openAtStartup = false)
@ActionID(category = "Window", id = "org.janelia.it.workstation.browser.components.SampleResultViewerTopComponent")
//@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_SampleResultViewerAction",
        preferredID = SampleResultViewerTopComponent.TC_NAME
)
@Messages({
    "CTL_SampleResultViewerAction=Sample Result Viewer",
    "CTL_SampleResultViewerTopComponent=Sample Result Viewer",
    "HINT_SampleResultViewerTopComponent=Sample Result Viewer"
})
public final class SampleResultViewerTopComponent extends TopComponent implements FindContextActivator {

    private static final Logger log = LoggerFactory.getLogger(SampleResultViewerTopComponent.class);
    
    public static final String TC_NAME = "SampleResultViewerTopComponent";
    public static final String TC_VERSION = "1.0";
        
    /* Instance variables */
    
    private final InstanceContent content = new InstanceContent();
    private SampleResultEditor editor;
    private FindContext findContext;
    private boolean active = false;
    
    public SampleResultViewerTopComponent() {
        initComponents();
        setName(Bundle.CTL_SampleResultViewerTopComponent());
        setToolTipText(Bundle.HINT_SampleResultViewerTopComponent());
        associateLookup(new AbstractLookup(content));
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
        SampleResultViewerManager.getInstance().activate(this);
    }

    @Override
    public void componentClosed() {
    }
    
    @Override
    protected void componentActivated() {
        log.info("Activating sample result viewer");
        this.active = true;
        // Make this the active sample result viewer
        SampleResultViewerManager.getInstance().activate(this);
        // Make our ancestor editor the current find context
        if (findContext!=null) {
            FindContextManager.getInstance().activateContext((FindContext)findContext);
        }
        if (editor!=null) {
            editor.activate();
        }
    }
    
    @Override
    protected void componentDeactivated() {
        this.active = false;
        if (findContext!=null) {
            FindContextManager.getInstance().deactivateContext((FindContext)findContext);
        }
        if (editor!=null) {
            editor.deactivate();
        }
    }

    void writeProperties(java.util.Properties p) {
    }

    void readProperties(java.util.Properties p) {
    }
    
    // Custom methods

    @Override
    public void setFindContext(FindContext findContext) {
        this.findContext = findContext; 
        if (active) {
            FindContextManager.getInstance().activateContext(findContext);
        }
    }
    
    public PipelineResult getCurrent() {
        return getLookup().lookup(PipelineResult.class);
    }

    private boolean setCurrent(PipelineResult result) {
        PipelineResult curr = getCurrent();
        if (result!=null && curr!=null && result.getId().equals(curr.getId())) {
            return false;
        }
        if (curr!=null) {
            content.remove(curr);
        }
        content.add(result);
        return true;
    }

    public void setEditorClass(Class<? extends SampleResultEditor> editorClass) {
        try {
            if (editor!=null) {
                remove((JComponent)editor);
                Events.getInstance().unregisterOnEventBus(editor);
                Events.getInstance().unregisterOnEventBus(editor.getEventBusListener());
            }
            
            editor = editorClass.newInstance();
            Events.getInstance().registerOnEventBus(editor.getEventBusListener());
            Events.getInstance().registerOnEventBus(editor);
            
            JComponent editorComponent = (JComponent)editor;
            editorComponent.addMouseListener(new MouseForwarder(this, "DomainObjectSelectionEditor->DomainListViewTopComponent"));
            add(editorComponent, BorderLayout.CENTER);
            
        }
        catch (InstantiationException | IllegalAccessException e) {
            ConsoleApp.handleException(e);
        }
        setName(editor.getName());
    }
    
    public SampleResultEditor getEditor() {
        return editor;
    }

    public void loadSampleResult(PipelineResult result, boolean isUserDriven, Callable<Void> success) {

        // Can view display this object?
        final Class<? extends SampleResultEditor> editorClass = getEditorClass(result);
        if (editorClass==null) {
            log.info("No editor defined for result of type {}",result.getClass().getSimpleName());
            return;
        }

        // Do we already have the given node loaded?
        if (!setCurrent(result)) {
            return;
        }

        if (editor==null || !editor.getClass().equals(editorClass)) {
            setEditorClass(editorClass);
        }

        editor.loadSampleResult(result, isUserDriven, success);

        String sampleName = StringUtils.abbreviate(result.getParentRun().getParent().getParent().getName(), 18);
        if (editor instanceof NeuronSeparationEditorPanel) {
            setName("Neurons for " + sampleName);
        }
        else {
            setName("Results for " + sampleName);
        }
    }

    private static Class<? extends SampleResultEditor> getEditorClass(PipelineResult result) {
        if (result instanceof SampleAlignmentResult || result instanceof SampleProcessingResult) {
            return NeuronSeparationEditorPanel.class;
        }
        else if (result instanceof HasFileGroups) {
            return FileGroupEditorPanel.class;
        }
        return null;
    }
}