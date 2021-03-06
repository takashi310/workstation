package org.janelia.workstation.browser.gui.listview;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.JMenuItem;

import org.janelia.workstation.integration.util.FrameworkAccess;
import org.janelia.workstation.integration.spi.domain.DomainObjectAppender;
import org.janelia.workstation.integration.spi.domain.DomainObjectCreator;
import org.janelia.workstation.integration.spi.domain.ServiceAcceptorHelper;
import org.janelia.model.domain.DomainObject;
import org.janelia.model.domain.sample.ObjectiveSample;
import org.janelia.model.domain.sample.PipelineResult;
import org.janelia.model.domain.sample.Sample;
import org.janelia.model.domain.sample.SamplePipelineRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience delegate to make Menu Items for creating things which contain
 * domain objects.
 * 
 * @author fosterl
 */
public class WrapperCreatorItemFactory {
    
    private static final Logger log = LoggerFactory.getLogger( WrapperCreatorItemFactory.class );    

    /**
     * This allows an abstraction layer between making the action, and the thing
     * that is being carried out.
     * 
     * @param domainObject build another domain object around this; may be null.
     * @return the menu item suitable for add to menu.
     */
    public List<JMenuItem> makeWrapperCreatorItems(final DomainObject domainObject) {
        List<JMenuItem> rtnVal = new ArrayList<>();
        Collection<DomainObjectCreator> wrapperCreators
                = ServiceAcceptorHelper.findHandler(domainObject, DomainObjectCreator.class);
        for ( DomainObjectCreator wrapperCreator: wrapperCreators ) {
            JMenuItem wrapEntityItem = new JMenuItem(wrapperCreator.getActionLabel());
            wrapEntityItem.addActionListener(new WrapObjectActionListener(wrapperCreator, domainObject));
            rtnVal.add( wrapEntityItem );
        }
        return rtnVal;
    }
	
    /**
     * Resolves a pipeline result to its sample container/ancestor, and
     * hands off to other method.
     * 
     * @param pipelineResult which should belong to a sample.
     * @return any suitable menu items.
     */
    public List<JMenuItem> makeWrapperCreatorItems(final PipelineResult pipelineResult) {
        // Need to resolve pipeline results into their samples.
        DomainObject sample = getSampleContainer(pipelineResult);
        if (sample != null)
            return makeWrapperCreatorItems(sample);
        else
            return Collections.emptyList();
    }
	
	/**
	 * Allows an abstraction layer between the action and thing being carried
	 * out.  Here, we are letting things be appended to some other object, or
	 * "added to".
	 * 
	 * @param domainObjects list of things to be provided to some receiver.
	 * @return menu items suitable for the menu.
	 */
	public List<JMenuItem> makeObjectAppenderItems(final List<DomainObject> domainObjects) {
		List<JMenuItem> rtnVal = new ArrayList<>();
		Collection<DomainObjectAppender> objectUsers
				= ServiceAcceptorHelper.findHandler(domainObjects, DomainObjectAppender.class);
		for ( DomainObjectAppender appender: objectUsers ) {
			JMenuItem item = new JMenuItem(appender.getActionLabel());
			item.addActionListener(new AppenderActionListener( appender, domainObjects));
			rtnVal.add(item);
		}
		return rtnVal;
	}
    
    /**
     * Resolves a pipeline result to its sample container/ancestor, and
     * hands off to other method.
     * 
     * @param pipelineResult which should belong to a sample.
     * @return any suitable menu items.
     */
    public List<JMenuItem> makePipelineResultAppenderItems(PipelineResult pipelineResult) {
        List<DomainObject> objList = new ArrayList<>();
        // Need to resolve pipeline's containing sample.
        DomainObject sample = getSampleContainer(pipelineResult);
        if (sample != null) {
            objList.add(sample);
        }
        return makeObjectAppenderItems(objList);
    }
    
    /** Convenience method to resolve sample for pr. */
    private Sample getSampleContainer(PipelineResult pipelineResult) {
        Sample rtnVal = null;
        SamplePipelineRun spr = pipelineResult.getParentRun();
        if (spr != null) {
            ObjectiveSample os = spr.getParent();
            if (os != null) {
                rtnVal = os.getParent();                        
            }
        }
        return rtnVal;
    }
    
    class WrapObjectActionListener implements ActionListener {
        private DomainObjectCreator wrapperCreator;
        private DomainObject domainObject;
        public WrapObjectActionListener( DomainObjectCreator wrapperCreator, DomainObject domainObject ) {
            this.wrapperCreator = wrapperCreator;
            this.domainObject = domainObject;
        }
        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                if (wrapperCreator == null) {
                    log.warn("No service provider for this object.");
                } else {
                    wrapperCreator.useDomainObject(domainObject);
                }
            } catch (Exception ex) {
                FrameworkAccess.handleException(ex);
            }

        }
    }
	
	class AppenderActionListener implements ActionListener {
		private DomainObjectAppender appender;
		private List<DomainObject> domainObjects;
		public AppenderActionListener( DomainObjectAppender appender, List<DomainObject> domainObjects) {
			this.appender = appender;
			this.domainObjects = domainObjects;
		}
		
		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				if (appender == null) {
					log.warn("No service provider for this object list.");
				}
				else {
					appender.useDomainObjects(domainObjects);
				}
			} catch (Exception ex) {
				FrameworkAccess.handleException(ex);
			}
		}
	}

}
