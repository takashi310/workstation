package org.janelia.it.workstation.gui.browser.components.viewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.UIManager;
import org.janelia.it.workstation.gui.browser.components.icongrid.DomainObjectIconGridViewer;
import org.janelia.it.workstation.gui.browser.search.ResultPage;
import org.janelia.it.workstation.gui.browser.search.SearchResults;
import org.janelia.it.workstation.gui.framework.session_mgr.SessionMgr;
import org.janelia.it.workstation.gui.util.Icons;
import org.janelia.it.workstation.shared.workers.SimpleWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A panel that builds pagination and selection features around an 
 * AnnotatedDomainObjectListViewer. 
 * 
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public abstract class PaginatedResultsPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(PaginatedResultsPanel.class);
    
    // Splash panel
    protected JLabel splashPanel;
    
    // Status bar
    protected final JPanel statusBar;
    protected final JLabel statusLabel;
    protected final JPanel selectionButtonContainer;
    protected final JButton prevPageButton;
    protected final JButton nextPageButton;
    protected final JButton endPageButton;
    protected final JButton startPageButton;
    protected final JButton selectAllButton;
    protected final JLabel pagingStatusLabel;
    
    // Result view
    protected AnnotatedDomainObjectListViewer resultsView;
    
    // Content
    protected SearchResults searchResults;
    protected int numPages = 0;
    protected int currPage = 0;
    
    // Hud dialog
//    protected Hud hud;
    
    public PaginatedResultsPanel() {
        
        setLayout(new BorderLayout());
        
        splashPanel = new JLabel(Icons.getIcon("workstation_logo_white.png"));
        add(splashPanel);
        
        prevPageButton = new JButton(Icons.getIcon("arrow_back.gif"));
        prevPageButton.setToolTipText("Back A Page");
        prevPageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                goPrevPage();
            }
        });

        nextPageButton = new JButton(Icons.getIcon("arrow_forward.gif"));
        nextPageButton.setToolTipText("Forward A Page");
        nextPageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                goNextPage();
            }
        });

        startPageButton = new JButton(Icons.getIcon("arrow_double_left.png"));
        startPageButton.setToolTipText("Jump To Start");
        startPageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                goStartPage();
            }
        });

        endPageButton = new JButton(Icons.getIcon("arrow_double_right.png"));
        endPageButton.setToolTipText("Jump To End");
        endPageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                goEndPage();
            }
        });

        selectAllButton = new JButton("Select All");
        selectAllButton.setToolTipText("Select all items on all pages");
        selectAllButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectAll();
            }
        });

        statusLabel = new JLabel("");
        pagingStatusLabel = new JLabel("");

        selectionButtonContainer = new JPanel();
        selectionButtonContainer.setLayout(new BoxLayout(selectionButtonContainer, BoxLayout.LINE_AXIS));

        selectionButtonContainer.add(selectAllButton);
        selectionButtonContainer.add(Box.createRigidArea(new Dimension(10, 20)));
        selectionButtonContainer.setVisible(false);

        statusBar = new JPanel();
        statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.LINE_AXIS));
        statusBar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, (Color) UIManager.get("windowBorder")), BorderFactory.createEmptyBorder(0, 5, 2, 5)));

        statusBar.add(Box.createRigidArea(new Dimension(10, 20)));
        statusBar.add(statusLabel);
        statusBar.add(Box.createRigidArea(new Dimension(10, 20)));
        statusBar.add(selectionButtonContainer);
        statusBar.add(new JSeparator(JSeparator.VERTICAL));
        statusBar.add(Box.createHorizontalGlue());
        statusBar.add(pagingStatusLabel);
        statusBar.add(Box.createRigidArea(new Dimension(10, 20)));
        statusBar.add(startPageButton);
        statusBar.add(prevPageButton);
        statusBar.add(nextPageButton);
        statusBar.add(endPageButton);

//        hud = Hud.getSingletonInstance();
//        hud.addKeyListener(keyListener);
        
        this.resultsView = new DomainObjectIconGridViewer();
    }
    
    private void updatePagingStatus() {
        startPageButton.setEnabled(currPage != 0);
        prevPageButton.setEnabled(currPage > 0);
        nextPageButton.setEnabled(currPage < numPages - 1);
        endPageButton.setEnabled(currPage != numPages - 1);
    }

    private synchronized void goPrevPage() {
        this.currPage -= 1;
        if (currPage < 0) {
            currPage = 0;
        }
        showPage(currPage);
    }

    private synchronized void goNextPage() {
        this.currPage += 1;
        if (currPage >= numPages) {
            currPage = numPages-1;
        }
        showPage(currPage);
    }

    private synchronized void goStartPage() {
        this.currPage = 0;
        showPage(currPage);
    }

    private synchronized void goEndPage() {
        this.currPage = numPages - 1;
        showPage(currPage);
    }

    private synchronized void selectAll() {
//        for (T imageObject : allImageObjects) {
//            ModelMgr.getModelMgr().getEntitySelectionModel().selectEntity(getSelectionCategory(), rootedEntity.getId(), false);
//        }
        selectionButtonContainer.setVisible(false);
    }
    
    public void showLoadingIndicator() {
        removeAll();
        add(new JLabel(Icons.getLoadingIcon()));
        updateUI();
    }
    
    public void showResultsView() {
        removeAll();
        add(resultsView.getViewerPanel(), BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        updateUI();
    }
    
    public void showNothing() {
        removeAll();
        add(splashPanel, BorderLayout.CENTER);
        updateUI();
    }
    
    public void showSearchResults(SearchResults searchResults) {
        this.searchResults = searchResults;
        numPages = searchResults.getNumTotalPages();
        this.currPage = 0;
        showPage(currPage);
    }
    
    protected void showPage(int page) {

        updatePagingStatus();
        showLoadingIndicator();
                
        if (searchResults==null) {
            throw new IllegalStateException("Cannot show page when there are no search results");
        }
        
        SimpleWorker worker = new SimpleWorker() {

            private ResultPage resultPage = null;
        
            @Override
            protected void doStuff() throws Exception {
                resultPage = getPage(searchResults, currPage);
            }

            @Override
            protected void hadSuccess() {
                showResultsView();
                resultsView.showDomainObjects(resultPage);
            }

            @Override
            protected void hadError(Throwable error) {
                showNothing();
                SessionMgr.getSessionMgr().handleException(error);
            }
        };
        
        worker.execute();
    }
    
    protected abstract ResultPage getPage(SearchResults searchResults, int page) throws Exception;
}
