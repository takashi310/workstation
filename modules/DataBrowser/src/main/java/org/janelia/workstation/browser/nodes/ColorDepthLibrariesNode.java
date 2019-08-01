package org.janelia.workstation.browser.nodes;

import java.awt.Image;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.janelia.model.domain.gui.cdmip.ColorDepthLibrary;
import org.janelia.model.domain.interfaces.HasIdentifier;
import org.janelia.workstation.common.gui.support.Icons;
import org.janelia.workstation.core.api.DomainMgr;
import org.janelia.workstation.integration.util.FrameworkAccess;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A node which shows the color depth libraries accessible to the current user.
 * 
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class ColorDepthLibrariesNode extends AbstractNode implements HasIdentifier {

    private final static Logger log = LoggerFactory.getLogger(ColorDepthLibrariesNode.class);

    private static final long NODE_ID = 20L; // This magic number means nothing, it just needs to be unique and different from GUID space.

    private final ColorDepthLibrariesChildFactory childFactory;

    ColorDepthLibrariesNode() {
        this(new ColorDepthLibrariesChildFactory());
    }

    private ColorDepthLibrariesNode(ColorDepthLibrariesChildFactory childFactory) {
        super(Children.create(childFactory, false));
        this.childFactory = childFactory;
    }

    @Override
    public Long getId() {
        return NODE_ID;
    }

    @Override
    public String getDisplayName() {
        return "Color Depth Libraries";
    }

    @Override
    public String getHtmlDisplayName() {
        String primary = getDisplayName();
        StringBuilder sb = new StringBuilder();
        if (primary!=null) {
            sb.append("<font color='!Label.foreground'>");
            sb.append(primary);
            sb.append("</font>");
        }
        return sb.toString();
    }
    
    @Override
    public Image getIcon(int type) {
        return Icons.getIcon("folder_database.png").getImage();
    }

    @Override
    public Image getOpenedIcon(int type) {
        return getIcon(type);
    }

    @Override
    public boolean canDestroy() {
        return false;
    }

    public void refreshChildren() {
        childFactory.refresh();
    }

    @Override
    public Action[] getActions(boolean context) {
        Collection<Action> actions = new ArrayList<>();
        actions.add(new PopupLabelAction());
        actions.add(new SearchAction());
        return actions.toArray(new Action[0]);
    }

    protected final class PopupLabelAction extends AbstractAction {

        PopupLabelAction() {
            putValue(NAME, getDisplayName());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    }

    protected final class SearchAction extends AbstractAction {

        SearchAction() {
            putValue(NAME, "Search Color Depth MIPs...");
        }

        @Override
        public void actionPerformed(ActionEvent e) {

        }
    }

    private static class ColorDepthLibrariesChildFactory extends ChildFactory<ColorDepthLibrary> {

        @Override
        protected boolean createKeys(List<ColorDepthLibrary> list) {
            try {
                log.debug("Creating children keys for ColorDepthLibrariesNode");
                list.addAll(DomainMgr.getDomainMgr().getModel().getColorDepthLibraries());
            }
            catch (Exception ex) {
                FrameworkAccess.handleException(ex);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(ColorDepthLibrary key) {
            try {
                return new ColorDepthLibraryNode(this, key);
            }
            catch (Exception e) {
                log.error("Error creating node for key " + key, e);
            }
            return null;
        }

        public void refresh() {
            log.debug("Refreshing child factory for "+getClass().getSimpleName());
            refresh(true);
        }
    }
}