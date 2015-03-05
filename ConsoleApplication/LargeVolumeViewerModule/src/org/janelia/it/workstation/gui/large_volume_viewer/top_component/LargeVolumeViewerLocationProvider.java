/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.janelia.it.workstation.gui.large_volume_viewer.top_component;

import java.net.URL;
import org.janelia.console.viewerapi.Tiled3dSampleLocationProvider;
import org.janelia.it.workstation.gui.large_volume_viewer.LargeVolumeViewViewer;
import org.openide.util.lookup.ServiceProvider;

/**
 * Can answer queries about where the large volume viewer is pointing, and
 * on which sample.
 * 
 * @author fosterl
 */
@ServiceProvider(service = Tiled3dSampleLocationProvider.class, path=Tiled3dSampleLocationProvider.LOOKUP_PATH)
public class LargeVolumeViewerLocationProvider implements Tiled3dSampleLocationProvider {

    private static final String PROVIDER_UNIQUE_NAME = "LargeVolumeViewer";
    private static final String DESCRIPTION = "Large Volume Viewer";
    
    private LargeVolumeViewViewer viewer;
    public LargeVolumeViewerLocationProvider( LargeVolumeViewViewer viewer ) {
        this.viewer = viewer;
    }
    
    public LargeVolumeViewerLocationProvider() {
        // Need to find the viewer.
        LargeVolumeViewerTopComponent lvv = 
                LargeVolumeViewerTopComponent.findThisTopComponent();
        this.viewer = lvv.getLvvv();
    }
    
    @Override
    public URL getSampleUrl() {
        return viewer.getSampleUrl();
    }

    @Override
    public double[] getCoords() {
        return viewer.getCoords();
    }

    @Override
    public String getProviderUniqueName() {
        return PROVIDER_UNIQUE_NAME;
    }

    @Override
    public String getProviderDescription() {
        return DESCRIPTION;
    }

}
