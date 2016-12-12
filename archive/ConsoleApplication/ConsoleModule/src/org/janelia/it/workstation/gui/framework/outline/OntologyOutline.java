package org.janelia.it.workstation.gui.framework.outline;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

import com.google.common.eventbus.Subscribe;
import org.janelia.it.jacs.model.entity.Entity;
import org.janelia.it.jacs.model.entity.EntityConstants;
import org.janelia.it.jacs.model.entity.EntityData;
import org.janelia.it.jacs.model.entity.ForbiddenEntity;
import org.janelia.it.jacs.model.ontology.OntologyAnnotation;
import org.janelia.it.jacs.model.ontology.OntologyElement;
import org.janelia.it.jacs.model.ontology.types.Category;
import org.janelia.it.jacs.model.ontology.types.Enum;
import org.janelia.it.jacs.model.ontology.types.EnumText;
import org.janelia.it.jacs.model.ontology.types.OntologyElementType;
import org.janelia.it.jacs.model.user_data.Subject;
import org.janelia.it.jacs.shared.utils.EntityUtils;
import org.janelia.it.workstation.api.entity_model.access.ModelMgrAdapter;
import org.janelia.it.workstation.api.entity_model.events.EntityChangeEvent;
import org.janelia.it.workstation.api.entity_model.events.EntityCreateEvent;
import org.janelia.it.workstation.api.entity_model.events.EntityInvalidationEvent;
import org.janelia.it.workstation.api.entity_model.events.EntityRemoveEvent;
import org.janelia.it.workstation.api.entity_model.management.EntitySelectionModel;
import org.janelia.it.workstation.api.entity_model.management.ModelMgr;
import org.janelia.it.workstation.api.entity_model.management.ModelMgrUtils;
import org.janelia.it.workstation.gui.dialogs.EntityDetailsDialog;
import org.janelia.it.workstation.gui.framework.actions.Action;
import org.janelia.it.workstation.gui.framework.actions.AnnotateAction;
import org.janelia.it.workstation.gui.framework.actions.CreateOntologyAction;
import org.janelia.it.workstation.gui.framework.actions.NavigateToNodeAction;
import org.janelia.it.workstation.gui.framework.actions.OntologyElementAction;
import org.janelia.it.workstation.gui.framework.outline.ontology.OntologyContextMenu;
import org.janelia.it.workstation.gui.framework.session_mgr.SessionMgr;
import org.janelia.it.workstation.gui.framework.tree.ExpansionState;
import org.janelia.it.workstation.gui.util.Icons;
import org.janelia.it.workstation.gui.util.JScrollPopupMenu;
import org.janelia.it.workstation.gui.util.MouseForwarder;
import org.janelia.it.workstation.model.entity.RootedEntity;
import org.janelia.it.workstation.shared.util.ConcurrentUtils;
import org.janelia.it.workstation.shared.workers.SimpleWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The right-hand ontology panel which displays all the ontologies that a user has access to.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public abstract class OntologyOutline extends EntityTree implements Refreshable, ActivatableView {

    private static final Logger log = LoggerFactory.getLogger(OntologyOutline.class);

    protected List<Entity> entityRootList;
    protected Entity root;
    private String currUniqueId;

    private final Map<String, Action> ontologyActionMap = new HashMap<>();

    public OntologyOutline() {

        // Listen for changes to the model
        ModelMgr.getModelMgr().addModelMgrObserver(new ModelMgrAdapter() {
            @Override
            public void ontologySelected(long rootId) {
                try {
                    loadOntology(rootId, null);
                }
                catch (Exception e) {
                    SessionMgr.getSessionMgr().handleException(e);
                }
            }
        });
    }

    @Override
    public void activate() {
        log.info("Activating");
        super.activate();
    }

    @Override
    public void deactivate() {
        log.info("Deactivating");
        super.deactivate();
    }
    
    /**
     * Override this method to load the root list. This method will be called in
     * a worker thread.
     *
     * @return
     */
    public abstract List<Entity> loadRootList() throws Exception;

    /**
     * Called after loadRootList is finished.
     *
     * @param entityRootList the shallow loaded list of ontology roots
     */
    public void init(List<Entity> entityRootList, Callable<Void> success) {

        if (entityRootList == null) {
            log.error("No ontology roots found");
            initializeTree(null);
            ConcurrentUtils.invokeAndHandleExceptions(success);
            return;
        }

        Long selectedId = ModelMgr.getModelMgr().getCurrentOntologyId();

        log.debug("Init outline with {} ontology roots. Selected id: {}", entityRootList.size(), selectedId);
        
        Entity selectedEntityRoot = null;

        this.entityRootList = new ArrayList<>();
        for (Entity entityRoot : entityRootList) {
            if (entityRoot.getId().equals(selectedId)) {
                selectedEntityRoot = entityRoot;
            }
            this.entityRootList.add(entityRoot);
        }

        if (selectedEntityRoot != null) {
            loadOntology(selectedEntityRoot.getId(), success);
        }
        else {
            this.root = null;
            initializeTree(null);
            ConcurrentUtils.invokeAndHandleExceptions(success);
        }
    }

    @Override
    public void initializeTree(Entity rootEntity) {
        super.initializeTree(rootEntity);

        getDynamicTree().add(getToolbar(), BorderLayout.PAGE_END);
    }

    /**
     * Load a single ontology into the outline.
     */
    public void loadOntology(final Long rootId, final Callable<Void> success) {

        showLoadingIndicator();

        Entity selectedEntityRoot = null;
        if (entityRootList != null) {
            for (Entity entityRoot : entityRootList) {
                if (entityRoot.getId().equals(rootId)) {
                    selectedEntityRoot = entityRoot;
                }
            }
        }

        if (selectedEntityRoot == null) {
            log.error("Ontology {} was not found in the ontology root list", rootId);
            initializeTree(null);
            return;
        }

        log.debug("Loading ontology {}", rootId);

        this.root = null;
        final Entity nextRoot = selectedEntityRoot;

        SimpleWorker worker = new SimpleWorker() {

            Entity tree = nextRoot;

            @Override
            protected void doStuff() throws Exception {
                tree = ModelMgr.getModelMgr().getEntityTree(nextRoot.getId());
            }

            @Override
            protected void hadSuccess() {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        showOntologyTree(tree);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                ConcurrentUtils.invokeAndHandleExceptions(success);
                            }
                        });
                    }
                });
            }

            @Override
            protected void hadError(Throwable error) {
                SessionMgr.getSessionMgr().handleException(error);
                initializeTree(null);
            }
        };

        worker.execute();
    }

    /**
     * Load a single ontology into the outline.
     */
    public void showOntologyTree(final Entity ontologyTree) {

        this.root = ontologyTree;
        log.debug("Showing ontology tree {}", root.getName());

        EntityData rootEd = new EntityData();
        rootEd.setChildEntity(root);

        initializeTree(root);

        // We've already loaded the entire tree
        selectedTree.setLazyLoading(false);

        // Build a lookup table of the action for each node
        ontologyActionMap.clear();
        populateActionMap(new RootedEntity(root));

        // Replace the cell renderer with one that knows about the outline so that it can retrieve key binds
        selectedTree.setCellRenderer(new OntologyTreeCellRenderer(OntologyOutline.this));

        JTree tree = getTree();

        // Replace the default key listener on the tree
        KeyListener defaultKeyListener = tree.getKeyListeners()[0];
        tree.removeKeyListener(defaultKeyListener);

        tree.setRootVisible(true);
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON_OR_INSERT);
        tree.setTransferHandler(new OntologyElementTransferHandler() {
            @Override
            public JComponent getDropTargetComponent() {
                return OntologyOutline.this;
            }
        });

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                // Expand the tree
                selectedTree.expandAll(true);
            }
        });
    }

    protected JToolBar getToolbar() {

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setRollover(true);

        if (entityRootList != null) {
            final JButton ontologyButton = new JButton("Open ontology...");
            ontologyButton.setIcon(Icons.getIcon("open_action.png"));
            ontologyButton.setToolTipText("Open ontology");
            ontologyButton.setFocusable(false);
            ontologyButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {

                    final JScrollPopupMenu ontologyListMenu = new JScrollPopupMenu();
                    ontologyListMenu.setMaximumVisibleRows(50);

                    for (final Entity entityRoot : entityRootList) {
                        Subject subject = null;
                        try {
                            subject = ModelMgr.getModelMgr().getSubjectByKey(entityRoot.getOwnerKey());
                        }
                        catch (Exception ex) {
                            log.error("Error getting subject: "+entityRoot.getOwnerKey(),ex);
                        }
                        String owner = subject==null?entityRoot.getOwnerKey():subject.getFullName();
                        JMenuItem roleMenuItem = new JCheckBoxMenuItem(entityRoot.getName() + " (" + owner + ")", root != null && entityRoot.getId().equals(root.getId()));
                        roleMenuItem.setIcon(Icons.getIcon(entityRoot));
                        roleMenuItem.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent e) {
                                ModelMgr.getModelMgr().setCurrentOntologyId(entityRoot.getId());
                            }
                        });
                        ontologyListMenu.add(roleMenuItem);
                    }

                    ontologyListMenu.add(new JSeparator());

                    JMenuItem addMenuItem = new JMenuItem("Create New Ontology...");
                    addMenuItem.setIcon(Icons.getIcon("folder_add.png"));
                    addMenuItem.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            Action action = new CreateOntologyAction();
                            action.doAction();
                        }
                    });
                    ontologyListMenu.add(addMenuItem);

                    ontologyListMenu.show(ontologyButton, 0, ontologyButton.getHeight());
                }
            });
            ontologyButton.addMouseListener(new MouseForwarder(toolBar, "OntologyButton->JToolBar"));
            toolBar.add(ontologyButton);
        }

        return toolBar;
    }
    
    private class OntologyOutlineContextMenu extends OntologyContextMenu {

        public OntologyOutlineContextMenu(DefaultMutableTreeNode node, String uniqueId) {
            super(new RootedEntity(uniqueId, getEntityData(node)), getOntologyElement(node));
        }

        public void addRootMenuItems() {
            add(getRootItem());
            add(getNewRootFolderItem());
        }

        protected JMenuItem getRootItem() {
            JMenuItem titleMenuItem = new JMenuItem("Ontologies");
            titleMenuItem.setEnabled(false);
            return titleMenuItem;
        }

        private JMenuItem getNewRootFolderItem() {
            Action action = new CreateOntologyAction("  Create New Ontology");
            return getActionItem(action);
        }
    }

    /**
     * Override this method to show a popup menu when the user right clicks a
     * node in the tree.
     *
     * @param e
     */
    @Override
    protected void showPopupMenu(final MouseEvent e) {

        // Clicked on what node?
        final DefaultMutableTreeNode node = selectedTree.getCurrentNode();

        // Create context menu
        final OntologyOutlineContextMenu popupMenu = new OntologyOutlineContextMenu(node, selectedTree.getUniqueId(node));

        if (node != null) {
            final Entity entity = getEntity(node);
            if (entity == null) {
                return;
            }
            popupMenu.addMenuItems();
        }
        else {
            popupMenu.addRootMenuItems();
        }

        popupMenu.show(selectedTree.getTree(), e.getX(), e.getY());
    }

    /**
     * Get the associated action for the given node.
     *
     * @param node
     * @return
     */
    public Action getActionForNode(DefaultMutableTreeNode node) {
        if (ontologyActionMap == null || selectedTree == null) {
            return null;
        }
        return ontologyActionMap.get(selectedTree.getUniqueId(node));
    }

    /**
     * Override this method to do something when the user left clicks a node.
     *
     * @param e
     */
    protected void nodeClicked(MouseEvent e) {
        selectNode(selectedTree.getCurrentNode());
    }

    /**
     * Override this method to do something when the user double clicks a node.
     *
     * @param e
     */
    @Override
    protected void nodeDoubleClicked(MouseEvent e) {
        Action action = getActionForNode(getDynamicTree().getCurrentNode());
        if (action != null && !(action instanceof NavigateToNodeAction)) {
            action.doAction();
        }
    }

    private synchronized void selectNode(final DefaultMutableTreeNode node) {

        if (node == null) {
            currUniqueId = null;
            return;
        }

        String uniqueId = getDynamicTree().getUniqueId(node);
        if (uniqueId.equals(currUniqueId)) {
            return;
        }

        this.currUniqueId = uniqueId;

        log.debug("Selecting node {}", uniqueId);
        ModelMgr.getModelMgr().getEntitySelectionModel().selectEntity(EntitySelectionModel.CATEGORY_ONTOLOGY, uniqueId + "", true);
    }

    /**
     * Register a corresponding Action for the given element, based on its term type. Recurses through the
     * element's children if there are any.
     */
    private void populateActionMap(RootedEntity rootedEntity) {

        // Define an action for this node
        OntologyElement element = new OntologyElement(rootedEntity.getEntityData().getParentEntity(), rootedEntity.getEntity());

        OntologyElementType type = element.getType();
        OntologyElementAction action = null;
        if (type instanceof Category || type instanceof Enum) {
            action = new NavigateToNodeAction();
        }
        else {
            action = new AnnotateAction();
        }

        log.trace("Associating element {} with path {}", element.getId(), rootedEntity.getUniqueId());

        action.init(rootedEntity.getUniqueId());
        ontologyActionMap.put(rootedEntity.getUniqueId(), action);

        for (RootedEntity rootedChild : rootedEntity.getRootedChildren()) {
            if (rootedChild.getEntity() instanceof ForbiddenEntity) {
                continue;
            }
            populateActionMap(rootedChild);
        }
    }

    @Subscribe
    public void entityCreated(EntityCreateEvent event) {

        Entity entity = event.getEntity();
        if (entity.getEntityTypeName().equals(EntityConstants.TYPE_ONTOLOGY_ROOT)) {
            log.debug("New ontology root detected: '{}'", entity.getName());

            if (entityRootList != null) {
                entityRootList.add(entity);
                Collections.sort(entityRootList, new EntityRootComparator());
            }
        }
    }

    @Subscribe
    @Override
    public void entityRemoved(EntityRemoveEvent event) {
        super.entityRemoved(event);

        Entity entity = event.getEntity();
        if (entity.getEntityTypeName().equals(EntityConstants.TYPE_ONTOLOGY_ROOT)) {
            log.debug("Ontology was deleted: '{}'", entity.getName());

            if (entityRootList != null) {
                Set<Entity> toRemove = new HashSet<>();
                for (Entity entityRoot : entityRootList) {
                    if (entityRoot.getId().equals(entity.getId())) {
                        // An ontology root was changed
                        toRemove.add(entityRoot);
                    }
                }

                for (Entity entityRoot : toRemove) {
                    log.debug("Removing ontology root: {}", EntityUtils.identify(entityRoot));
                    entityRootList.remove(entityRoot);
                    initializeTree(null);
                }
            }
        }
    }

    @Subscribe
    @Override
    public void entityChanged(EntityChangeEvent event) {
        super.entityChanged(event);
        final Entity entity = event.getEntity();

        // Ensure this runs after node updates
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                for (DefaultMutableTreeNode node : getNodesByEntityId(entity.getId())) {
                    String uniqueId = getDynamicTree().getUniqueId(node);
                    RootedEntity changedRe = getRootedEntity(uniqueId);

                    for (EntityData ed : ModelMgrUtils.getAccessibleEntityDatasWithChildren(changedRe.getEntity())) {
                        Entity child = ed.getChildEntity();
                        RootedEntity childRe = changedRe.getChild(ed);
                        populateActionMap(childRe);
                    }
                }
            }
        });

    }

    @Subscribe
    @Override
    public void entityInvalidated(EntityInvalidationEvent event) {
        super.entityInvalidated(event);
        if (event.isTotalInvalidation()) {
            refresh(false, true, null);
        }
        else {
            Collection<Entity> invalidated = event.getInvalidatedEntities();
            for (Entity entity : invalidated) {
                for (DefaultMutableTreeNode node : getNodesByEntityId(entity.getId())) {
                    String uniqueId = getDynamicTree().getUniqueId(node);
                    log.debug("Removing invalidate node from action map: " + uniqueId);
                    ontologyActionMap.remove(uniqueId);
                }
            }
        }
    }

    @Override
    public void refresh() {
        refresh(true, null);
    }

    @Override
    public void totalRefresh() {
        totalRefresh(true, null);
    }

    public void refresh(final boolean restoreState, final Callable<Void> success) {
        refresh(false, restoreState, success);
    }

    public void totalRefresh(final boolean restoreState, final Callable<Void> success) {
        refresh(true, restoreState, success);
    }

    public void refresh(final boolean invalidateCache, final boolean restoreState, final Callable<Void> success) {
        if (restoreState) {
            final ExpansionState expansionState = new ExpansionState();
            expansionState.storeExpansionState(getDynamicTree());
            refresh(invalidateCache, expansionState, success);
        }
        else {
            refresh(invalidateCache, null, success);
        }
    }

    private AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private Queue<Callable<Void>> callbacks = new ConcurrentLinkedQueue<>();

    private synchronized void executeCallBacks() {
        synchronized (this) {
            for (Iterator<Callable<Void>> iterator = callbacks.iterator(); iterator.hasNext();) {
                try {
                    iterator.next().call();
                }
                catch (Exception e) {
                    log.error("Error executing callback", e);
                }
                iterator.remove();
            }
        }
    }

    public void refresh(final boolean invalidateCache, final ExpansionState expansionState, final Callable<Void> success) {

        synchronized (this) {
            if (success != null) {
                callbacks.add(success);
            }
            if (refreshInProgress.getAndSet(true)) {
                log.debug("Skipping refresh, since there is one already in progress");
                return;
            }
        }

        log.debug("Starting whole tree refresh (invalidateCache={}, restoreState={})", invalidateCache, expansionState != null);

        showLoadingIndicator();
        ModelMgr.getModelMgr().unregisterOnEventBus(OntologyOutline.this);

        SimpleWorker entityOutlineLoadingWorker = new SimpleWorker() {

            private List<Entity> rootList;

            @Override
            protected void doStuff() throws Exception {
                if (invalidateCache && getRootEntity() != null) {
                    ModelMgr.getModelMgr().invalidateCache();
                }
                rootList = loadRootList();
                log.debug("Loaded {} ontology roots", rootList.size());
            }

            @Override
            protected void hadSuccess() {
                try {
                    ModelMgr.getModelMgr().registerOnEventBus(OntologyOutline.this);

                    init(rootList, new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {

                            refreshInProgress.set(false);

                            if (expansionState != null && getDynamicTree() != null && getRootEntity() != null) {
                                expansionState.restoreExpansionState(getDynamicTree(), true, new Callable<Void>() {
                                    @Override
                                    public Void call() throws Exception {
                                        showTree();
                                        executeCallBacks();
                                        log.debug("Tree refresh complete and expansion state restored");
                                        return null;
                                    }
                                });
                            }
                            else {
                                showTree();
                                executeCallBacks();
                                log.debug("Tree refresh complete");
                            }

                            return null;
                        }
                    });
                }
                catch (Exception e) {
                    hadError(e);
                }
            }

            @Override
            protected void hadError(Throwable error) {
                refreshInProgress.set(false);
                log.error("Ontology refresh encountered error", error);
                JOptionPane.showMessageDialog(OntologyOutline.this, "Error loading ontology outline", "Ontology Load Error",
                        JOptionPane.ERROR_MESSAGE);
                initializeTree(null);
            }
        };

        entityOutlineLoadingWorker.execute();
    }

    public static void viewAnnotationDetails(OntologyAnnotation tag) {
        new EntityDetailsDialog().showForRootedEntity(new RootedEntity(tag.getEntity()));
    }

    public Entity getCurrentOntology() {
        return root;
    }

    @Override
    public RootedEntity getRootedEntity(String uniqueId) {
        EntityData ed = getEntityDataByUniqueId(uniqueId);
        if (ed == null) {
            return null;
        }
        return new RootedEntity(uniqueId, ed);
    }

    public void navigateToOntologyElement(OntologyElement element) {
        Collection<DefaultMutableTreeNode> nodes = getNodesByEntityId(element.getEntity().getId());
        if (!nodes.isEmpty()) {
            selectedTree.navigateToNode(nodes.iterator().next());
        }
    }

    public OntologyElement getOntologyElement(DefaultMutableTreeNode node) {
        if (node == null) {
            return null;
        }
        return getOntologyElement(getEntityData(node));
    }

    public OntologyElement getOntologyElement(EntityData entityData) {

        OntologyElement element = new OntologyElement(entityData.getParentEntity(), entityData.getChildEntity());
        if (element.getType() instanceof EnumText) {
            EnumText enumText = (EnumText) element.getType();
            EntityData enumEd = entityData.getChildEntity().getEntityDataByAttributeName(EntityConstants.ATTRIBUTE_ONTOLOGY_TERM_TYPE_ENUMTEXT_ENUMID);
            if (enumEd.getChildEntity() != null) {
                enumText.init(getOntologyElement(enumEd));
            }
            else {
                log.warn("EnumText has no Enum Id child: {}", enumEd.getId());
            }
        }

        return element;
    }

    public OntologyElement getRootOntologyElement() {
        return getOntologyElement(getDynamicTree().getRootNode());
    }

    @Override
    public String toString() {
        return "OntologyOutline(" + root + ")";
    }
}