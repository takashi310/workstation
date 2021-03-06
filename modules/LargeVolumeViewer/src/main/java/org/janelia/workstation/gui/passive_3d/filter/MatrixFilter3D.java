package org.janelia.workstation.gui.passive_3d.filter;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.swing.ProgressMonitor;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.janelia.workstation.integration.util.FrameworkAccess;
import org.janelia.it.jacs.model.util.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Carries out a 3D filtering (for things like smoothing), against
 * some input byte array, which has N bytes per element.
 * 
 * NOTE: limited to byte array, which is constrained to an Integer.MAX_INT
 * divided by bytes-per-voxel, of filtered data.
 * 
 * @author fosterl
 */
public class MatrixFilter3D {
    // On MacPro with 64Gb, seeing load of 256x taking 17s with 30 threads. 
    // At 20 threads: 21s.  At 15 threads: 24s.  At 10 threads: 26s. LLF
    private static final int NUM_THREADS = 30;
    private static final double AVG_VAL = 1.0/27.0; 
    public static double[] AVG_MATRIX_3_3_3 = new double[] {
        AVG_VAL, AVG_VAL, AVG_VAL,
        AVG_VAL, AVG_VAL, AVG_VAL,
        AVG_VAL, AVG_VAL, AVG_VAL,

        AVG_VAL, AVG_VAL, AVG_VAL,
        AVG_VAL, AVG_VAL, AVG_VAL,
        AVG_VAL, AVG_VAL, AVG_VAL,

        AVG_VAL, AVG_VAL, AVG_VAL,
        AVG_VAL, AVG_VAL, AVG_VAL,
        AVG_VAL, AVG_VAL, AVG_VAL,
    };
    
    private static final double ROUND_DIVISOR = 82.0;
    public static double[] SPHERE_3_3_3 = new double[] {
        1/ROUND_DIVISOR, 3/ROUND_DIVISOR,  1/ROUND_DIVISOR,
        3/ROUND_DIVISOR, 5/ROUND_DIVISOR,  3/ROUND_DIVISOR,
        1/ROUND_DIVISOR, 3/ROUND_DIVISOR,  1/ROUND_DIVISOR,

        3/ROUND_DIVISOR, 5/ROUND_DIVISOR,  3/ROUND_DIVISOR,
        5/ROUND_DIVISOR, 8/ROUND_DIVISOR,  5/ROUND_DIVISOR,
        3/ROUND_DIVISOR, 5/ROUND_DIVISOR,  3/ROUND_DIVISOR,

        1/ROUND_DIVISOR, 3/ROUND_DIVISOR,  1/ROUND_DIVISOR,
        3/ROUND_DIVISOR, 5/ROUND_DIVISOR,  3/ROUND_DIVISOR,
        1/ROUND_DIVISOR, 3/ROUND_DIVISOR,  1/ROUND_DIVISOR,
    };
    
    private static final double ANULAR_DIVISOR = 131.0;
    public static double[] ANULUS_3_3_3 = new double[] {
        8/ANULAR_DIVISOR, 3/ANULAR_DIVISOR,  8/ANULAR_DIVISOR,
        3/ANULAR_DIVISOR, 5/ANULAR_DIVISOR,  3/ANULAR_DIVISOR,
        8/ANULAR_DIVISOR, 3/ANULAR_DIVISOR,  8/ANULAR_DIVISOR,

        3/ANULAR_DIVISOR, 5/ANULAR_DIVISOR,  3/ANULAR_DIVISOR,
        5/ANULAR_DIVISOR, 1/ANULAR_DIVISOR,  5/ANULAR_DIVISOR,
        3/ANULAR_DIVISOR, 5/ANULAR_DIVISOR,  3/ANULAR_DIVISOR,

        8/ANULAR_DIVISOR, 3/ANULAR_DIVISOR,  8/ANULAR_DIVISOR,
        3/ANULAR_DIVISOR, 5/ANULAR_DIVISOR,  3/ANULAR_DIVISOR,
        8/ANULAR_DIVISOR, 3/ANULAR_DIVISOR,  8/ANULAR_DIVISOR,
    };
    
    private static final double GAUSS_DIVISOR = 119.0;
    public static double[] GAUSS_5_5_5 = new double[] {

        0/GAUSS_DIVISOR, 0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 0/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 1/GAUSS_DIVISOR,  2/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 0/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,

        0/GAUSS_DIVISOR, 0/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 1/GAUSS_DIVISOR,  3/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        1/GAUSS_DIVISOR, 3/GAUSS_DIVISOR,  5/GAUSS_DIVISOR,  3/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 1/GAUSS_DIVISOR,  3/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 0/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,

        0/GAUSS_DIVISOR, 1/GAUSS_DIVISOR,  2/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        1/GAUSS_DIVISOR, 3/GAUSS_DIVISOR,  5/GAUSS_DIVISOR,  3/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,
        2/GAUSS_DIVISOR, 5/GAUSS_DIVISOR,  9/GAUSS_DIVISOR,  5/GAUSS_DIVISOR,  2/GAUSS_DIVISOR,
        1/GAUSS_DIVISOR, 3/GAUSS_DIVISOR,  5/GAUSS_DIVISOR,  3/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 1/GAUSS_DIVISOR,  2/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,

        0/GAUSS_DIVISOR, 0/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 1/GAUSS_DIVISOR,  3/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        1/GAUSS_DIVISOR, 3/GAUSS_DIVISOR,  5/GAUSS_DIVISOR,  3/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 1/GAUSS_DIVISOR,  3/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 0/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,

        0/GAUSS_DIVISOR, 0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 0/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 1/GAUSS_DIVISOR,  2/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 0/GAUSS_DIVISOR,  1/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,
        0/GAUSS_DIVISOR, 0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,  0/GAUSS_DIVISOR,

    };
    
    public static double[] GAUSS_65_85_85 = new double[]{
        0.0,               0.0,               0.0,               0.0,               0.0,
        0.000165485701763, 0.001319501594531, 0.002636086966363, 0.001319501594531, 0.000165485701763,
        0.000540403860351, 0.004308914593990, 0.008608306081225, 0.004308914593990, 0.000540403860351,
        0.000165485701763, 0.001319501594531, 0.002636086966363, 0.001319501594531, 0.000165485701763,
        0.0,               0.0,               0.0,               0.0,               0.0,
        
        0.0,               0.0,               0.0,               0.0,               0.0,
        0.001319501594531, 0.010521056740371, 0.021018860955232, 0.010521056740371, 0.001319501594531,
        0.004308914593990, 0.034357165706113, 0.068638398842737, 0.034357165706113, 0.004308914593990,
        0.001319501594531, 0.010521056740371, 0.021018860955232, 0.010521056740371, 0.001319501594531,
        0.0,               0.0,               0.0,               0.0,               0.0,
        
        0.0,               0.0,               0.0,               0.0,               0.0,
        0.002636086966363, 0.021018860955232, 0.041991268249713, 0.021018860955232, 0.002636086966363,
        0.008608306081225, 0.068638398842737, 0.137125100364620, 0.068638398842737, 0.008608306081225,
        0.002636086966363, 0.021018860955232, 0.041991268249713, 0.021018860955232, 0.002636086966363,
        0.0,               0.0,               0.0,               0.0,               0.0,
        
        0.0,               0.0,               0.0,               0.0,               0.0,
        0.001319501594531, 0.010521056740371, 0.021018860955232, 0.010521056740371, 0.001319501594531,
        0.004308914593990, 0.034357165706113, 0.068638398842737, 0.034357165706113, 0.004308914593990,
        0.001319501594531, 0.010521056740371, 0.021018860955232, 0.010521056740371, 0.001319501594531,
        0.0,               0.0,               0.0,               0.0,               0.0,
        
        0.0,               0.0,               0.0,               0.0,               0.0,
        0.000165485701763, 0.001319501594531, 0.002636086966363, 0.001319501594531, 0.000165485701763,
        0.000540403860351, 0.004308914593990, 0.008608306081225, 0.004308914593990, 0.000540403860351,
        0.000165485701763, 0.001319501594531, 0.002636086966363, 0.001319501594531, 0.000165485701763,
        0.0,               0.0,               0.0,               0.0,               0.0,
    };
    
    private final double[] matrix;
    private final int matrixCubicDim;
    private ByteOrder byteOrder;
    private int[] shiftDistance;
    private ProgressMonitor progressMonitor;
    
    private static final Logger logger = LoggerFactory.getLogger( MatrixFilter3D.class );
    
    public MatrixFilter3D( double[] matrix, ByteOrder byteOrder ) {
        this.matrix = matrix;
        matrixCubicDim = (int)Math.pow( matrix.length + 0.5, 1.0/3.0 );
        if ( matrixCubicDim * matrixCubicDim * matrixCubicDim != matrix.length ) {
            throw new IllegalArgumentException( "Matrix size not a cube." );
        }
        this.byteOrder = byteOrder;
    }
    
    public void setProgressMonitor( ProgressMonitor progressMonitor ) {
        this.progressMonitor = progressMonitor;
    }
    
    /**
     * Filter the input array using the supplied matrix.
     * 
     * @param inputBytes bytes of input data
     * @param bytesPerCell how many bytes make up the integer cell value (1..4)
     * @param channelCount how many separate channels in data (1..4)
     * @param sx length of x.
     * @param sy length of y.
     * @param sz length of z.
     * @return filtered version of original.
     */
    public byte[] filter( byte[] inputBytes, final int bytesPerCell, int channelCount, int sx, int sy, int sz ) {
        logger.info("Starting the filter run.");
        // one-time precalculate some values used in filtering operation.
        shiftDistance = new int[ bytesPerCell ];
        if ( byteOrder == ByteOrder.BIG_ENDIAN ) {
            for ( int i = 0; i < bytesPerCell; i++ ) {
                shiftDistance[ i ] = 8 * (bytesPerCell - i - 1);
            }
        }
        else if ( byteOrder == ByteOrder.LITTLE_ENDIAN ) {
            for ( int i = 0; i < bytesPerCell; i++ ) {
                shiftDistance[ i ] = 8 * i;
            }
        }

        final byte[] outputBytes = new byte[ inputBytes.length ];
        final FilteringParameter param = new FilteringParameter();
        param.setExtentX(matrixCubicDim);
        param.setExtentY(matrixCubicDim);
        param.setExtentZ(matrixCubicDim);
        param.setSx(sx);
        param.setSy(sy);
        param.setSz(sz);
        param.setVoxelBytes(bytesPerCell);
        param.setChannelCount(channelCount);
        param.setVolumeData(inputBytes);
        
        final int lineSize = sx * param.getStride();
        final int sheetSize = sy * lineSize;
        
        // First, let's change the bytes going into the neighborhoods, to
        // all long values.  One pass, rather than repeating that operation.
        final long[] inputLongs = convertToLong( param );          
        final ExecutorService executorService = ThreadUtils.establishExecutor(
                NUM_THREADS,
                new ThreadFactoryBuilder()
                        .setNameFormat("MatrixFilter-%03d")
                        .build());
        List<Future<Void>> callbacks = new ArrayList<>();
        for (int ch = 0; ch < channelCount; ch++) {
            for (int z = 0; z < sz; z++) {
                // Need final variables to pass into worker.
                final int zF = z;
                final int chF = ch;
                Callable<Void> runnable = new Callable() {
                    @Override
                    public Void call() {
                        try {
                            filterZ(param, inputLongs, zF, chF, outputBytes, sheetSize, lineSize);
                        } catch (Exception ex) {
                            // Exception implies useless output.
                            executorService.shutdownNow();
                            FrameworkAccess.handleException(ex);
                        }
                        return null;
                    }
                };
                callbacks.add( executorService.submit(runnable) );
                
                if (progressMonitor != null && progressMonitor.isCanceled()) {
                    // If user bails, the executor should go away.
                    executorService.shutdownNow();
                    return inputBytes;
                }
            }
        }
        try {
            // Now that everything has been queued, can send the shutdown
            // signal.  Then await termination, which in turn waits for all
            // the loads to complete.
            ThreadUtils.followUpExecution(executorService, callbacks, 5);
        } catch ( Exception ex ) {
            FrameworkAccess.handleException(ex);
        }
        logger.info("Ending the filter run.");
        return outputBytes;
    }

    /**
     * Convenience method for bearing the loads for multi-threaded smoothing
     * operation.
     * 
     * @param param has various things that apply to whole filter operation.
     * @param inputLongs pre-digested long versions of raw byte array.
     * @param z which "sheet" we will be working on.
     * @param ch which channel we will be working on.
     * @param outputBytes
     * @param sheetSize
     * @param lineSize 
     */
    private void filterZ(FilteringParameter param, long[] inputLongs, int z, int ch, byte[] outputBytes, int sheetSize, int lineSize) {
        int bytesPerCell = param.getVoxelBytes();
        for (int y = 0; y < param.getSy(); y++ ) {
            for (int x = 0; x < param.getSx(); x++ ) {
                long[] neighborhood = getNeighborhood(param, inputLongs, x, y, z, ch);
                long filtered = applyFilter(neighborhood);
                byte[] value = getArrayEquiv(filtered, bytesPerCell);
                for (int voxByte = 0; voxByte < bytesPerCell; voxByte++) {
                    outputBytes[ z * sheetSize + y * lineSize + (x * param.getStride()) + (ch * param.getVoxelBytes()) + voxByte] = value[ voxByte ];
                }
            }
        }
    }
    
    private long applyFilter( long[] neighborhood ) {
        assert neighborhood.length == matrix.length : "Matrix and neighborhood length must match.";
        long rtnVal = 0;
        int i = 0;
        for ( int sheet = 0; sheet < matrixCubicDim; sheet++ ) {
            for ( int row = 0; row < matrixCubicDim; row++ ) {
                for ( int col = 0; col < matrixCubicDim; col++ ) {                    
                    rtnVal += matrix[ i ] * neighborhood[ i ];
                    i++;
                }
            }
        }
        return rtnVal;
    }
    
    /**
     * Turns the entire byte array into a long array of equivalent values.
     * 
     * @param fparam all info required.
     * @return array of longs, to hold volume specified in fparam.
     */
    private long[] convertToLong(FilteringParameter fparam) {
        long[] returnValue = new long[ fparam.getSx() * fparam.getSy() * fparam.getSz() * fparam.getChannelCount() ];

        byte[] byteVolume = fparam.getVolume();
        byte[] voxelVal = new byte[fparam.getVoxelBytes()];
        int k = 0;
        for ( int i = 0; i < byteVolume.length; i += fparam.getVoxelBytes() ) {
            for ( int j = 0; j < fparam.getVoxelBytes(); j++ ) {
                voxelVal[ j ] = byteVolume[ i + j ];
            }
            long equivalentValue = getIntEquiv(voxelVal);
            returnValue[ k++ ] = equivalentValue;
        }
        return returnValue;
    }

    /**
     * Finds the neighborhood surrounding the input point (x,y,z), as unsigned
     * integer array.  All edge cases (near end, near beginning) are handled
     * by using only partial neighborhoods which are truncated there.
     *
     * @param fparam metadata about the slice being calculated.
     * @param y input location under study.
     * @param x input location under study.
     * @param z input location under study.
     * @param channel channel number under study.
     * @return computed value: all bytes of the voxel.
     */
    private long[] getNeighborhood(
            FilteringParameter fparam, long[] inputVol, int x, int y, int z, int channel
    ) {

        // Neighborhood starts at the x,y,z values of the loops.  There will be one
        // such neighborhood for each of these sets: x,y,z
        final int sz = fparam.getSz();
        final int sy = fparam.getSy();
        final int sx = fparam.getSx();
        final int extentX = fparam.getExtentX();
        final int extentY = fparam.getExtentY();
        final int extentZ = fparam.getExtentZ();
        
        long[] returnValue = new long[ extentX * extentY * extentZ ];
        int startX = neighborhoodStart(x, extentX);
        int startY = neighborhoodStart(y, extentY);
        int startZ = neighborhoodStart(z, extentZ);
        for ( int zNbh = startZ; zNbh < startZ + extentZ && zNbh < sz; zNbh ++ ) {
            if (zNbh < 0) {// Edge case: at beginning->partial neighborhood.
                continue;
            }
            long nbhZOffset = (sy * sx * zNbh) * fparam.getChannelCount();

            for ( int yNbh = startY; yNbh < startY + extentY && yNbh < sy; yNbh ++ ) {
                if (yNbh < 0) {// Edge case: at beginning->partial neighborhood.
                    continue;
                }
                long nbhYOutOffs = nbhZOffset + (sx * yNbh * fparam.getChannelCount());

                for ( int xNbh = startX; xNbh < startX + extentX && xNbh < sx; xNbh++ ) {
                    if (xNbh < 0) {// Edge case: at beginning->partial neighborhood.
                        continue;
                    }
                    // No need of the full stride, which includes the voxel bytes, against the input long array.
                    int arrayCopyLoc = (int)( nbhYOutOffs + ( xNbh * fparam.getChannelCount() ) + channel );
                    if (arrayCopyLoc >= inputVol.length) {
                        logger.error("Out of bounds.");
                    }

                    long equivalentValue = inputVol[ arrayCopyLoc ];
                    
                    final int outputOffset = (zNbh-startZ)*(extentY*extentX) + (yNbh-startY) * extentX + (xNbh-startX);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Output location for " + xNbh + "," + yNbh + "," + zNbh + " is " + outputOffset);
                    }
                    if (outputOffset >= returnValue.length) {
                        logger.error("Out of bounds.");
                    }
                    returnValue[ outputOffset ] = equivalentValue;
                }
            }
        }       
        return returnValue;
    }
    
    /** 
     * Start of neighborhood for a given coordinate should be minus half
     * its extent. 
     */
    private int neighborhoodStart( int coord, int extent ) {
        return coord - (extent/2);
    }
    
    /**
     * Convert the voxel value into an integer.  Assumes 4 or fewer bytes per voxel.
     * Returning the "unsigned integer" equivalent of the array of bytes.
     * Must use long to avoid use of sign bits.
     *
     * @param voxelValue input bytes
     * @return integer conversion, based on LSB
     */
    private long getIntEquiv( byte[] voxelValue ) {
        long rtnVal = 0;
        int arrLen = voxelValue.length;
        for ( int i = 0; i < arrLen; i++ ) {
            int inVal = voxelValue[ i ];
            if ( inVal < 0 ) {
                inVal += 256;
            }
            rtnVal += (inVal << shiftDistance[ i ]);
        }
        return rtnVal;
    }
    
    private byte[] getArrayEquiv( long voxelValue, int arrLen ) {
        byte[] rtnVal = new byte[ arrLen ];
        for (int i = 0; i < arrLen; i++) {
            rtnVal[ i ] = 
            (byte)(
                    (voxelValue >> shiftDistance[ i ])
                    & 0xFF
            );
        }
        return rtnVal;
    }
    
    private boolean isZero( byte[] bytes ) {
        for (byte aByte : bytes) {
            if (aByte != (byte) 0) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Bean of information used to drive the filtering.
     */
    public static class FilteringParameter {
        // Size of the entire volume, in all three dimensions.
        private int sx;
        private int sy;
        private int sz;
        
        // Size of a neighborhood, in all three dimensions.
        private int extentX;
        private int extentY;
        private int extentZ;
        
        // How many bytes make one voxel?
        private int voxelBytes;
        
        // How many channels;
        private int channelCount;
        
        private byte[] volumeData;

        /**
         * @return the sx
         */
        public int getSx() {
            return sx;
        }

        /**
         * @param sx the sx to set
         */
        public void setSx(int sx) {
            this.sx = sx;
        }

        /**
         * @return the sy
         */
        public int getSy() {
            return sy;
        }

        /**
         * @param sy the sy to set
         */
        public void setSy(int sy) {
            this.sy = sy;
        }

        /**
         * @return the sz
         */
        public int getSz() {
            return sz;
        }

        /**
         * @param sz the sz to set
         */
        public void setSz(int sz) {
            this.sz = sz;
        }
        
        public byte[] getVolume() {
            return volumeData;
        }
        
        public void setVolumeData( byte[] volumeData ) {
            this.volumeData = volumeData;
        }

        /**
         * @return the extentX
         */
        public int getExtentX() {
            return extentX;
        }

        /**
         * @param extentX the extentX to set
         */
        public void setExtentX(int extentX) {
            this.extentX = extentX;
        }

        /**
         * @return the extentY
         */
        public int getExtentY() {
            return extentY;
        }

        /**
         * @param extentY the extentY to set
         */
        public void setExtentY(int extentY) {
            this.extentY = extentY;
        }

        /**
         * @return the extentZ
         */
        public int getExtentZ() {
            return extentZ;
        }

        /**
         * @param extentZ the extentZ to set
         */
        public void setExtentZ(int extentZ) {
            this.extentZ = extentZ;
        }

        /**
         * @return the voxelBytes
         */
        public int getVoxelBytes() {
            return voxelBytes;
        }
        
        /**
         * Logical locations of subsequent/progressive cells, may be separated
         * by number of channels in the volume, times the width of each channel.
         * @return distance between subsequent cells.
         */
        public int getStride() {
            return voxelBytes * channelCount;
        }

        /**
         * @param voxelBytes the voxelBytes to set
         */
        public void setVoxelBytes(int voxelBytes) {
            this.voxelBytes = voxelBytes;
        }

        /**
         * @return the channelCount
         */
        public int getChannelCount() {
            return channelCount;
        }

        /**
         * @param channelCount the channelCount to set
         */
        public void setChannelCount(int channelCount) {
            this.channelCount = channelCount;
        }
    }

}
