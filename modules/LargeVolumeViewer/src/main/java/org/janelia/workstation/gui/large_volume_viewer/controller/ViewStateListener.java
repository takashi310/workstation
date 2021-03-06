package org.janelia.workstation.gui.large_volume_viewer.controller;

import org.janelia.workstation.geom.Vec3;
import org.janelia.model.domain.tiledMicroscope.TmColorModel;

/**
 * Implement this to handle changes to view state.
 * 
 * @author fosterl
 */
public interface ViewStateListener {
    void setCameraFocus(Vec3 focus);
    void centerNextParent();
    void loadColorModel(TmColorModel colorModel);
    void pathTraceRequested(Long neuronID, Long annotationId);
}
