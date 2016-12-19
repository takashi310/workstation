package org.janelia.it.workstation.browser.nb_action;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;

import org.janelia.it.jacs.model.domain.DomainObject;
import org.janelia.it.jacs.model.domain.Reference;
import org.janelia.it.jacs.model.domain.sample.Sample;
import org.janelia.it.workstation.browser.ConsoleApp;
import org.janelia.it.workstation.browser.api.DomainMgr;
import org.janelia.it.workstation.browser.events.selection.GlobalDomainObjectSelectionModel;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * Allows the user to bind the "set publishing name" action to a key or toolbar button.
 * 
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
@ActionID(
        category = "Core",
        id = "org.janelia.it.workstation.browser.nb_action.SetPublishingNameAction"
)
@ActionRegistration(
        displayName = "#CTL_SetPublishingNameAction"
)
@ActionReferences({
        @ActionReference(path = "Shortcuts", name = "D-P")
})
@Messages("CTL_SetPublishingNameAction=Choose Line Publishing Name")
public class SetPublishingNameAction extends AbstractAction {

    private List<Sample> samples;

    public SetPublishingNameAction() {
        super("Choose Line Publishing Name on Selected Samples");
    }
    
    public SetPublishingNameAction(List<Sample> samples) {
        super(getName(samples));
        this.samples = samples;
    }

    private static String getName(List<Sample> samples) {
        if (samples!=null) {
            if (samples.size()>1) {
                return "Choose Line Publishing Name on "+samples.size()+" Samples";
            }
        }
        return "Choose Line Publishing Name";
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            if (samples==null) {
                samples = new ArrayList<>();
                List<Reference> selectedIds = GlobalDomainObjectSelectionModel.getInstance().getSelectedIds();
                List<DomainObject> selected = DomainMgr.getDomainMgr().getModel().getDomainObjects(selectedIds);
                for(DomainObject domainObject : selected) {
                    if (domainObject instanceof Sample) {
                        samples.add((Sample)domainObject);
                    }
                }
            }
            SetPublishingNameActionListener actionListener = new SetPublishingNameActionListener(samples);
            actionListener.actionPerformed(null);
        }
        catch (Exception ex) {
            ConsoleApp.handleException(ex);
        }
    }
}
