/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.janelia.it.workstation.gui.full_skeleton_view.top_component;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.janelia.it.workstation.gui.full_skeleton_view.data_source.AnnotationSkeletonDataSourceI;
import org.janelia.it.workstation.gui.full_skeleton_view.viewer.AnnotationSkeletonPanel;
import org.janelia.it.workstation.gui.large_volume_viewer.QuadViewUi;
import org.janelia.it.workstation.gui.large_volume_viewer.skeleton.Skeleton;
import org.janelia.it.workstation.gui.large_volume_viewer.top_component.LargeVolumeViewerTopComponent;
import org.janelia.it.workstation.gui.large_volume_viewer.top_component.LargeVolumeViewerTopComponentDynamic;
import org.janelia.it.workstation.gui.util.WindowLocator;

/**
 * Takes on as much non-autogenerated behavior for the TopComponent, as is
 * possible.  The goal is to minimize impact of studio-regeneration of the
 * top component that is populated by this.
 *
 * @author fosterl
 */
public class TopComponentPopulator {
    private AnnotationSkeletonPanel skeletonPanel;
    
    public void populate(JPanel panel) {
        depopulate(panel);
        if (skeletonPanel == null) {
            skeletonPanel = new AnnotationSkeletonPanel( new SkeletonDataSource() );
        }
        panel.add(skeletonPanel, BorderLayout.CENTER);
    }
    
    public void depopulate(JPanel panel) {
        System.out.println("Depopulating the skeleton panel.");
        if (panel != null && skeletonPanel != null) {
            panel.remove(skeletonPanel);
            skeletonPanel.close();
            skeletonPanel = null;
        }
    }
    
    private static class SkeletonDataSource implements AnnotationSkeletonDataSourceI {

        private Skeleton cachedSkeleton;

        public SkeletonDataSource() {
        }
        
        @Override
        public Skeleton getSkeleton() {
            if (cachedSkeleton == null) {
                // Strategy: get the Large Volume Viewer View.
                LargeVolumeViewerTopComponent tc
                        = (LargeVolumeViewerTopComponent) WindowLocator.getByName(
                                LargeVolumeViewerTopComponentDynamic.LVV_PREFERRED_ID
                        );
                if (tc != null) {
                    QuadViewUi ui = tc.getLvvv().getQuadViewUi();
                    if (ui != null) {
                        cachedSkeleton = ui.getSkeleton();
                    }
                }
            }
            return cachedSkeleton;
        }
        
    }
}
