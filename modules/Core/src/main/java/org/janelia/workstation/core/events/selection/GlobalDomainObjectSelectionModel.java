package org.janelia.workstation.core.events.selection;

import java.util.List;

import org.janelia.workstation.core.events.Events;
import org.janelia.model.domain.DomainObject;
import org.janelia.model.domain.Reference;

import com.google.common.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A selection model which tracks all domain object selections, globally across all other selection models. 
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class GlobalDomainObjectSelectionModel extends SelectionModel<DomainObject,Reference> {

    private static final Logger log = LoggerFactory.getLogger(GlobalDomainObjectSelectionModel.class);
    
    public static GlobalDomainObjectSelectionModel instance;
    
    private GlobalDomainObjectSelectionModel() {
    }
    
    public static GlobalDomainObjectSelectionModel getInstance() {
        if (instance==null) {
            instance = new GlobalDomainObjectSelectionModel();
            Events.getInstance().registerOnEventBus(instance);
        }
        return instance;
    }

    @Subscribe
    public void domainObjectSelected(DomainObjectSelectionEvent event) {
        if (!event.isUserDriven()) {
            // Events that are automatic should not change the global selection
            return;
        }
        log.trace("Applying to global: {}",event);
        if (event.isSelect()) {
            select(event.getDomainObjects(), event.isClearAll(), event.isUserDriven());
        }
        else {
            deselect(event.getDomainObjects(), event.isUserDriven());
        }
    }
    
    @Override
    protected void selectionChanged(List<DomainObject> domainObjects, boolean select, boolean clearAll, boolean isUserDriven) {
        // Since this is a meta-model, the relevant events were already on the bus, so this method 
        // does not need to do any additional work.
    }
    
    @Override
    public Reference getId(DomainObject domainObject) {
        return Reference.createFor(domainObject);
    }
}
