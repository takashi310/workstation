package org.janelia.it.workstation.gui.large_volume_viewer.activity_logging;

import java.util.Date;
import org.janelia.it.jacs.model.user_data.tiledMicroscope.TmGeoAnnotation;
import org.janelia.it.jacs.shared.annotation.metrics_logging.ActionString;
import org.janelia.it.jacs.shared.annotation.metrics_logging.CategoryString;
import org.janelia.it.workstation.geom.Vec3;
import org.janelia.it.workstation.gui.framework.session_mgr.SessionMgr;
import org.janelia.it.workstation.gui.large_volume_viewer.TileIndex;
import org.janelia.it.workstation.gui.large_volume_viewer.annotation.AnnotationModel;
import static org.janelia.it.workstation.gui.large_volume_viewer.top_component.LargeVolumeViewerTopComponentDynamic.LVV_LOGSTAMP_ID;

/**
 * Keep all the logging code in one place, to declutter.
 *
 * @author fosterl
 */
public class ActivityLogHelper {
    // These category strings are used similarly.  Lining them up spatially
    // makes it easier to see that they are all different.
    private static final CategoryString LIX_CATEGORY_STRING                     = new CategoryString("loadTileIndexToRam:elapsed");
    private static final CategoryString LONG_TILE_LOAD_CATEGORY_STRING          = new CategoryString("longRunningTileIndexLoad");
    private static final CategoryString LVV_SESSION_CATEGORY_STRING             = new CategoryString("openFolder");
    private static final CategoryString LVV_ADD_ANCHOR_CATEGORY_STRING          = new CategoryString("addAnchor:xyz");
    private static final CategoryString LVV_MERGE_NEURITES_CATEGORY_STRING      = new CategoryString("mergeNeurites:xyz");
    private static final CategoryString LVV_MOVE_NEURITE_CATEGORY_STRING        = new CategoryString("moveNeurite:xyz");
    private static final CategoryString LVV_SPLIT_NEURITE_CATEGORY_STRING       = new CategoryString("splitNeurite:xyz");
    private static final CategoryString LVV_SPLIT_ANNO_CATEGORY_STRING          = new CategoryString("splitAnnotation:xyz");
    private static final CategoryString LVV_DELETE_LINK_CATEGORY_STRING         = new CategoryString("deleteLink:xyz");
    private static final CategoryString LVV_DELETE_SUBTREE_CATEGORY_STRING      = new CategoryString("deleteSubTree:xyz");
    private static final CategoryString LVV_REROOT_NEURITE_CATEGORY_STRING      = new CategoryString("rerootNeurite");
    private static final CategoryString LVV_3D_LAUNCH_CATEGORY_STRING           = new CategoryString("launch3dBrickView");
    private static final CategoryString LVV_NAVIGATE_LANDMARK_CATEGORY_STRING   = new CategoryString("navigateInLandmarkView");
    
    private static final int LONG_TIME_LOAD_LOG_THRESHOLD = 5 * 1000;

    public void logTileLoad(int relativeSlice, TileIndex tileIndex, final double elapsedMs, long folderOpenTimestamp) {
        final ActionString actionString = new ActionString(
                folderOpenTimestamp + ":" + relativeSlice + ":" + tileIndex.toString() + ":elapsed_ms=" + elapsedMs
        );
        // Use the by-category granularity for these.
        SessionMgr.getSessionMgr().logToolEvent(
                LVV_LOGSTAMP_ID,
                LIX_CATEGORY_STRING,
                actionString,
                elapsedMs,
                Double.MAX_VALUE
        );
        // Use the elapsed cutoff for this parallel category.
        SessionMgr.getSessionMgr().logToolThresholdEvent(
                LVV_LOGSTAMP_ID,
                LONG_TILE_LOAD_CATEGORY_STRING,
                actionString,
                new Date().getTime(),
                elapsedMs,
                LONG_TIME_LOAD_LOG_THRESHOLD
        );
    }

    public void logFolderOpen(String remoteBasePath, long folderOpenTimestamp) {
        SessionMgr.getSessionMgr().logToolEvent(
                LVV_LOGSTAMP_ID, 
                LVV_SESSION_CATEGORY_STRING, 
                new ActionString(remoteBasePath + ":" + folderOpenTimestamp)
        );
    }
    
    public void logAddAnchor(Long sampleId, Vec3 location) {
        //  Change Vec3 to double[] if inconvenient.
        SessionMgr.getSessionMgr().logToolEvent(
                LVV_LOGSTAMP_ID, 
                LVV_ADD_ANCHOR_CATEGORY_STRING, 
                new ActionString(sampleId + ":" + location.getX() + "," + location.getY() + "," + location.getZ())
        );
    }
    
    public void logRerootNeurite(Long sampleID, Long neuronID) {
        SessionMgr.getSessionMgr().logToolEvent(
                LVV_LOGSTAMP_ID, 
                LVV_REROOT_NEURITE_CATEGORY_STRING, 
                new ActionString(sampleID + ":" + neuronID)
        );
    }
    
    public void logMergedNeurite(Long sampleID, TmGeoAnnotation source) {
        this.logGeometricEvent(sampleID, source, LVV_MERGE_NEURITES_CATEGORY_STRING);
    }
    
    public void logMovedNeurite(Long sampleID, TmGeoAnnotation source) {
        this.logGeometricEvent(sampleID, source, LVV_MOVE_NEURITE_CATEGORY_STRING);
    }
    
    public void logSplitNeurite(Long sampleID, TmGeoAnnotation source) {
        this.logGeometricEvent(sampleID, source, LVV_SPLIT_NEURITE_CATEGORY_STRING);
    }

    public void logSplitAnnotation(Long sampleID, TmGeoAnnotation source) {
        this.logGeometricEvent(sampleID, source, LVV_SPLIT_ANNO_CATEGORY_STRING);
    }
    
    public void logDeleteLink(Long sampleID, TmGeoAnnotation source) {
        this.logGeometricEvent(sampleID, source, LVV_DELETE_LINK_CATEGORY_STRING);
    }
    
    public void logDeleteSubTree(Long sampleID, TmGeoAnnotation source) {
        this.logGeometricEvent(sampleID, source, LVV_DELETE_SUBTREE_CATEGORY_STRING);        
    }
    
    public void logSnapshotLaunch(String labelText, Long workspaceId) {
        SessionMgr.getSessionMgr().logToolEvent(
                LVV_LOGSTAMP_ID, 
                LVV_3D_LAUNCH_CATEGORY_STRING,
                new ActionString(labelText + " workspaceId=" + workspaceId)
        );
    }
    
    public void logLandmarkViewPick(AnnotationModel annotationModel, Long annotationId) {
        String action = "Unknown";
        if (annotationModel != null
                && annotationModel.getCurrentWorkspace() != null
                && annotationModel.getCurrentWorkspace().getId() != null) {
            action = "Sample/Annotation:" + annotationModel.getCurrentWorkspace().getSampleID() + ":" + annotationId;
        }
        SessionMgr.getSessionMgr().logToolEvent(
                LVV_LOGSTAMP_ID, 
                LVV_NAVIGATE_LANDMARK_CATEGORY_STRING,
                new ActionString(action)
        );
    }
    
    private void logGeometricEvent(Long sampleID, TmGeoAnnotation anno, CategoryString category) {
            SessionMgr.getSessionMgr().logToolEvent(
                LVV_LOGSTAMP_ID, 
                category, 
                new ActionString(sampleID + ":" + anno.getX() + "," + anno.getY() + "," + anno.getZ())
        );
    }

}
