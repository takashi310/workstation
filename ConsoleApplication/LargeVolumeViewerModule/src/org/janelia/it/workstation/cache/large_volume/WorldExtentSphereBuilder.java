/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.janelia.it.workstation.cache.large_volume;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import org.janelia.it.workstation.geom.CoordinateAxis;
import org.janelia.it.workstation.geom.Vec3;
import org.janelia.it.workstation.gui.large_volume_viewer.OctreeMetadataSniffer;
import org.janelia.it.workstation.gui.large_volume_viewer.SharedVolumeImage;
import org.janelia.it.workstation.gui.large_volume_viewer.TileBoundingBox;
import org.janelia.it.workstation.gui.large_volume_viewer.TileFormat;
import org.janelia.it.workstation.gui.large_volume_viewer.TileIndex;
import org.janelia.it.workstation.gui.large_volume_viewer.ViewBoundingBox;
import org.janelia.it.workstation.gui.large_volume_viewer.compression.CompressedFileResolver;
import org.janelia.it.workstation.gui.viewer3d.BoundingBox3d;
import org.janelia.it.workstation.octree.ZoomLevel;
import org.janelia.it.workstation.octree.ZoomedVoxelIndex;                
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This neighborhood builder, delineates the environ files by testing their
 * micron distance from the focus.  Nearby ones are included.
 * 
 * @author fosterl
 */
public class WorldExtentSphereBuilder implements GeometricNeighborhoodBuilder {
    
    private CompressedFileResolver resolver = new CompressedFileResolver();
    private CompressedFileResolver.CompressedFileNamer namer;
    private double radiusInMicrons;
    private double[] tileHalfSize;
    private int[] dimensions;
    private File topFolder;
    private TileFormatSource tileFormatSource;
    private double[] focusPlusZoom = null;
    private static Logger log = LoggerFactory.getLogger(WorldExtentSphereBuilder.class);

    /**
     * Convenience method for finding the center micron locations of tiles.
     * Suitable for testing.
     * 
     * @param tileFormat applicable to tile
     * @param tileIndex tile of interest
     * @param tileHalfSize "shape" geometry of containing tile collection.
     * @return location of centerpoint.
     */
    public static double[] findTileCenterInMicrons(TileFormat tileFormat, TileIndex tileIndex, double[] tileHalfSize) {
        final CoordinateAxis sliceAxis = tileIndex.getSliceAxis();
        final ZoomLevel zoomLevel = new ZoomLevel(tileIndex.getZoom());
        // @todo get better center approximation in Z plane.
        final int tileZ = tileIndex.getZ();

        final int minTileZ = tileZ - (tileZ % tileFormat.getTileSize()[2]);
        final TileFormat.TileXyz tileXyz = new TileFormat.TileXyz(tileIndex.getX(), tileIndex.getY(), minTileZ);
        final ZoomedVoxelIndex zvi = tileFormat.zoomedVoxelIndexForTileXyz(tileXyz, zoomLevel, sliceAxis);
        log.debug("Found zoomed voxel index of {},{},{}.", zvi.getX(), zvi.getY(), zvi.getZ());
        final TileFormat.VoxelXyz voxXyz = tileFormat.voxelXyzForZoomedVoxelIndex(zvi, sliceAxis);
        log.debug("Found voxXYZ of {},{},{}.", voxXyz.getX(), voxXyz.getY(), voxXyz.getZ());
        
        final TileFormat.MicrometerXyz mxyz = tileFormat.micrometerXyzForVoxelXyz(voxXyz, sliceAxis);
        //TileFormat.MicrometerXyz mxyz = tileFormat.micrometerXyzForZoomedVoxelIndex(zvi, CoordinateAxis.Z);
        final double[] tileCenter = new double[]{
            mxyz.getX() + tileHalfSize[0], // / tileFormat.getVoxelMicrometers()[0],
            mxyz.getY() + tileHalfSize[1], // / tileFormat.getVoxelMicrometers()[1],
            mxyz.getZ() + tileHalfSize[2], // / tileFormat.getVoxelMicrometers()[2]
        };
        return tileCenter;
    }
    
    public static double[] findTileCenterInMicronsAssumedSize(TileFormat tileFormat, TileIndex tileIndex) {
        return findTileCenterInMicrons(tileFormat, tileIndex, getTileHalfSize(tileFormat));
    }

    /**
     * Construct with all info require to describe a sphere.  That sphere will
     * have a radius of at least that provided.  However, its extent in all
     * directions will be at the resolution of the image stacks that make up
     * the repository.
     * 
     * @param tileFormat used to calculate the extents in all directions.
     * @param topFolderURL base point for all files in repo.
     * @param radius lower-bound micrometers to extend.
     */
    public WorldExtentSphereBuilder(SharedVolumeImage sharedVolumeImage, URL topFolderURL, double radius) throws URISyntaxException {
        setTileFormatSource( new SVITileFormatSource(sharedVolumeImage));
        
        this.radiusInMicrons = radius;
        this.topFolder = new File(topFolderURL.toURI());
    }
    
    /**
     * Simplistic constructor, helpful for testing.
     * 
     * @see #WorldExtentSphereBuilder(org.janelia.it.workstation.gui.large_volume_viewer.SharedVolumeImage, java.net.URL, double) 
     * @param tileFormat
     * @param topFolder
     * @param radius 
     */
    public WorldExtentSphereBuilder(final TileFormat tileFormat, File topFolder, double radius) {
        setTileFormatSource( new TileFormatSource() {
            public TileFormat getTileFormat() {
                return tileFormat;
            }
        });
        this.radiusInMicrons = radius;
        this.topFolder = topFolder;
    }
    
    public final void setTileFormatSource(TileFormatSource tfs) {
        this.tileFormatSource = tfs;
    }
    
	/**
     * Wish to produce the list of all files needed to fill in the
     * spherical neighborhood centered at the focus.
     * 
	 * @see GeometricNeighborhoodBuilder#buildNeighborhood(double[], double, double)
	 */
    @Override
    public GeometricNeighborhood buildNeighborhood(double[] focus, Double zoom, double pixelsPerSceneUnit) {
        double[] newFnZ = new double[4];
        newFnZ[0] = focus[0];
        newFnZ[1] = focus[1];
        newFnZ[2] = focus[2];
        newFnZ[3] = zoom;
        if (focusPlusZoom == null) {
            focusPlusZoom = newFnZ;
        } else {
            boolean sameAsBefore = true;
            for (int i = 0; i < 4; i++) {
                if (focusPlusZoom[i] != newFnZ[i]) {
                    sameAsBefore = false;
                }
            }
            if (sameAsBefore) {
                log.debug("Bailing...same as before.");
                return null;
            } else {
                focusPlusZoom = newFnZ;
            }
        }

        log.info("Building neighborhood at zoom {}, focus {},{},{}", zoom, focus[0], focus[1], focus[2] );
        WorldExtentSphere neighborhood = new WorldExtentSphere();
        neighborhood.setFocus(focus);
        neighborhood.setZoom(zoom);
        
        TileFormat tileFormat = tileFormatSource.getTileFormat();
        if (tileHalfSize == null) {
            tileHalfSize = getTileHalfSize(tileFormat);
        }
        // In order to find neighborhood, must figure out what is the center
        // tile, and must find all additional tiles, out to a certain point.
        // Will wish to ensure that a proper distance-from-focus has been
        // calculated, so that ordering / priority is given to near tiles.        
        Vec3 center = new Vec3(focus[0], focus[1], focus[2]);
//        if (dimensions == null) {
        dimensions = new int[]{(int)radiusInMicrons,(int)radiusInMicrons,(int)radiusInMicrons};
//        dimensions = new int[]{
//            (int) (radiusInMicrons / tileFormat.getVoxelMicrometers()[0]),
//            (int) (radiusInMicrons / tileFormat.getVoxelMicrometers()[1]),
//            (int) (radiusInMicrons / tileFormat.getVoxelMicrometers()[2]), //4000,4000,4000
//        };
//        }
        log.info("Dimensions in voxels are: {},{},{}.", dimensions[0], dimensions[1], dimensions[2]);
        
        log.info("Micron volume in cache extends from\n    {},{},{}\n  to\n    {},{},{}\nin um.",
                 center.getX() - radiusInMicrons, center.getY() - radiusInMicrons, center.getZ() - radiusInMicrons,
                 center.getX() + radiusInMicrons, center.getY() + radiusInMicrons, center.getZ() + radiusInMicrons
        );

        // Establish a collection with required order and guaranteed uniqueness.
        FocusProximityComparator comparator = new FocusProximityComparator();
        String tiffBase = OctreeMetadataSniffer.getTiffBase(CoordinateAxis.Z);
        Set<String> tileFilePaths = new HashSet<>();
        for (TileIndex tileIndex: getCenteredTileSet(tileFormat, center, pixelsPerSceneUnit, dimensions, zoom)) {
            File tilePath = OctreeMetadataSniffer.getOctreeFilePath(tileIndex, tileFormat, true);  
            if (tilePath == null) {
                log.warn("Null octree file path for {}.", tileIndex);
            }
            else {
                tilePath = new File(topFolder, tilePath.toString());
                double[] tileCenter = findTileCenterInMicrons(tileFormat, tileIndex);
                double sigmaSquare = 0.0;
                for (int i = 0; i < 3; i++) {
                    double absDist = Math.abs(tileCenter[i] - focus[i]);
                    sigmaSquare += absDist;
                }
                double distanceFromFocus = Math.sqrt(sigmaSquare);
                for (int channel = 0; channel < tileFormat.getChannelCount(); channel++) {
                    String fileName = OctreeMetadataSniffer.getFilenameForChannel(tiffBase, channel);
                    File tileFile = new File(tilePath, fileName);
                    // Work out what needs to be uncompressed, here.
                    if (namer == null) {
                        namer = resolver.getNamer(tileFile);
                    }
                    tileFile = namer.getCompressedName(tileFile);
                    String fullTilePath = tileFile.getAbsolutePath();
                    // With the comparator in use, this test is necessary.
                    if (!tileFilePaths.contains(fullTilePath)) {
                        comparator.addFile(tileFile, distanceFromFocus);
                        tileFilePaths.add(fullTilePath);
                        log.debug("Adding file {} to neighborhood {}.", fullTilePath, neighborhood.getId());
                    }
                }
            }
        }
        Set<File> tileFiles = new TreeSet<>(comparator);
        for ( String tileFilePath: tileFilePaths) {
            tileFiles.add(new File(tileFilePath));
        }
        neighborhood.setFiles(Collections.synchronizedSet(tileFiles));
        log.info("Neighborhood contains {} files.", tileFiles.size());
        return neighborhood;
    }
    
    private static double[] getTileHalfSize(TileFormat tileFormat) {
        int[] tileSize = tileFormat.getTileSize();
        log.debug("Tile Size is : {},{},{}.", tileSize[0], tileSize[1], tileSize[2]);
        return new double[] {
            tileSize[0] / 2.0,
            tileSize[1] / 2.0,
            tileSize[2] / 2.0
        };
    }
   
    private double[] findTileCenterInMicrons(TileFormat tileFormat, TileIndex tileIndex) {
        return findTileCenterInMicrons(tileFormat, tileIndex, tileHalfSize);
    }

    /**
     * Find the tile set that fills the voxel dimensions of the neighborhood.
     * 
     * Borrowed from Sub-volume class.
     */
    private Set<TileIndex> getCenteredTileSet(TileFormat tileFormat, Vec3 center, double pixelsPerSceneUnit, int[] dimensions, Double zoom) {
        // Ensure dimensions are divisible evenly, by two.
        for (int i = 0; i < dimensions.length; i++) {
            if (dimensions[i] % 2 == 1) {
                dimensions[i] ++;
            }
        }
        
        if (pixelsPerSceneUnit < 0.000001) {
            log.warn("Zero value in pixelsPerSceneUnit.  Resetting to 1.");
            pixelsPerSceneUnit = 1.0;
        }
        else {
            log.info("PixelsPerSceneUnit={}.", pixelsPerSceneUnit);
        }
        
        int[] xyzFromWhd = new int[]{0, 1, 2};
        CoordinateAxis sliceAxis = CoordinateAxis.Z;
        ViewBoundingBox voxelBounds = tileFormat.findViewBounds(
                dimensions[0], dimensions[1], center, pixelsPerSceneUnit, xyzFromWhd
        );
        TileBoundingBox tileBoundingBox = tileFormat.viewBoundsToTileBounds(xyzFromWhd, voxelBounds, zoom.intValue());
        
        // Now I have the tile outline.  Can just iterate over that, and for all
        // required depth.
        TileIndex.IndexStyle indexStyle = tileFormat.getIndexStyle();
        int zoomMax = tileFormat.getZoomLevelCount() - 1;

        double halfDepth = dimensions[2] / 2.0;
        BoundingBox3d bb = tileFormat.calcBoundingBox();
        int maxDepth = this.calcZCoord(bb, xyzFromWhd, tileFormat, (int) (center.getZ() + halfDepth));
        int minDepth = maxDepth - dimensions[xyzFromWhd[2]];
        if (minDepth < 0){
            minDepth = 0;
        }
        
        // NEED: to figure out why neighborhood is so huge.
        System.out.println("Tile BoundingBox span. Width: " + tileBoundingBox.getwMin() + ":" 
                           + tileBoundingBox.getwMax() + " Height: " + tileBoundingBox.gethMin()
                           + ":" + tileBoundingBox.gethMax() + " Depth:" + minDepth + ":" + maxDepth);

        log.info("Voxel volume in cache extends from\n\t{},{},{}\nto\t\n\t{},{},{}\nin voxels.\nDifference of {},{},{}.",
            voxelBounds.getwFMin(), voxelBounds.gethFMin(), minDepth, voxelBounds.getwFMax(), voxelBounds.gethFMax(), maxDepth,
            voxelBounds.getwFMax()-voxelBounds.getwFMin(), voxelBounds.gethFMax()-voxelBounds.gethFMin(), maxDepth-minDepth
        );

        int minWidth = tileBoundingBox.getwMin();
        int maxWidth = tileBoundingBox.getwMax();
        int minHeight = tileBoundingBox.gethMin();
        int maxHeight = tileBoundingBox.gethMax();
        return createTileIndexesOverRanges(
                minDepth, maxDepth,
                minWidth, maxWidth,
                minHeight, maxHeight,
                xyzFromWhd,
                zoom.intValue(),
                zoomMax,
                indexStyle,
                sliceAxis);
    }

    private int calcZCoord(BoundingBox3d bb, int[] xyzFromWhd, TileFormat tileFormat, int focusDepth) {
        double zVoxMicrons = tileFormat.getVoxelMicrometers()[xyzFromWhd[2]];
        int dMin = (int) (bb.getMin().get(xyzFromWhd[2]) / zVoxMicrons + 0.5);
        int dMax = (int) (bb.getMax().get(xyzFromWhd[2]) / zVoxMicrons - 0.5);
        int absoluteTileDepth = (int) Math.round(focusDepth / zVoxMicrons - 0.5);
        absoluteTileDepth = Math.max(absoluteTileDepth, dMin);
        absoluteTileDepth = Math.min(absoluteTileDepth, dMax);
        return absoluteTileDepth - tileFormat.getOrigin()[xyzFromWhd[2]];
    }

    private Set<TileIndex> createTileIndexesOverRanges(
            int minDepth, int maxDepth,
            int minWidth, int maxWidth,
            int minHeight, int maxHeight,
            int[] xyzFromWhd,
            int zoom,
            int zoomMax,
            TileIndex.IndexStyle indexStyle,
            CoordinateAxis sliceAxis) {

        Set<TileIndex> neededTiles = new LinkedHashSet<>();
        for (int d = minDepth; d < maxDepth; d++) {
            for (int w = minWidth; w <= maxWidth; ++w) {
                for (int h = minHeight; h <= maxHeight; ++h) {
                    int whd[] = {w, h, d};
                    TileIndex key = new TileIndex(
                            whd[xyzFromWhd[0]],
                            whd[xyzFromWhd[1]],
                            whd[xyzFromWhd[2]],
                            zoom,
                            zoomMax, indexStyle, sliceAxis);
                    neededTiles.add(key);

                }
            }
        }
        // NEED: to figure out why neighborhood is so huge.
        System.out.println("Needed Tiles number " + neededTiles.size());
        return neededTiles;
    }

    public static interface TileFormatSource {
        TileFormat getTileFormat();
    }
    
    private static class SVITileFormatSource implements TileFormatSource {
        private SharedVolumeImage svi;
        public SVITileFormatSource(SharedVolumeImage svi) {
            this.svi = svi;
        }
        @Override
        public TileFormat getTileFormat() {
            TileFormat tileFormat = svi.getLoadAdapter().getTileFormat();
            return tileFormat;
        }
        
    }

}
