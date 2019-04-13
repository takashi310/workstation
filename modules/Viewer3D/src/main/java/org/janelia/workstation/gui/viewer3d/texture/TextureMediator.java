package org.janelia.workstation.gui.viewer3d.texture;

import org.janelia.workstation.gui.viewer3d.VolumeDataAcceptor;
import org.janelia.workstation.gui.viewer3d.volume_builder.VolumeDataChunk;

import org.janelia.workstation.gui.viewer3d.OpenGLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.media.opengl.GL2;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: fosterl
 * Date: 1/17/13
 * Time: 2:48 PM
 *
 * This handles interfacing with OpenGL / JOGL for matters regarding textures.  One such mediator represents information
 * regarding a single texture.
 */
public class TextureMediator {
    public static int SIGNAL_TEXTURE_OFFSET = 0;
    public static int MASK_TEXTURE_OFFSET = 1;
    public static int COLOR_MAP_TEXTURE_OFFSET = 2;

    private int textureName;
    private int textureSymbolicId; // This is an ID like GL.GL_TEXTURE0.
    private int textureOffset; // This will be 0, 1, ...

    private boolean isInitialized = false;
    private boolean hasBeenUploaded = false;

    private TextureDataI textureData;
    private final Logger logger = LoggerFactory.getLogger( TextureMediator.class );

    private static Map<Integer,String> glConstantToName;

    public static int[] genTextureIds( GL2 gl, int count ) {
        int[] rtnVal = new int[ count ];
        gl.glGenTextures( count, rtnVal, 0 );
        return rtnVal;
    }

    public TextureMediator() {
        // No initialization.
    }

    /**
     * Initialize a mediator.  Assumptions that can be made about various identifiers will be made here.
     *
     * @param textureId as generated by @See #genTextureIds
     * @param offset 0, 1, ...
     */
    public void init( int textureId, int offset ) {
        if ( ! isInitialized ) {
            this.textureName = textureId;
            this.textureOffset = offset;
            textureSymbolicId = GL2.GL_TEXTURE0 + offset;
        }
        isInitialized = true;
    }

    public boolean uploadTexture( GL2 gl ) {
        boolean rtnVal = true;
        if (OpenGLUtils.reportError( "upon entry to uploadTexture", gl, textureName )) {
            return false;
        }
        if ( ! isInitialized ) {
            logger.error("Attempted to upload texture before mediator was initialized.");
            throw new RuntimeException("Failed to upload texture");
        }


        if ( textureData.getTextureData().getVolumeChunks() != null ) {

            logger.debug(
                    "[" +
                            textureData.getFilename() +
                            "]: Coords are " + textureData.getSx() + " * " + textureData.getSy() + " * " + textureData.getSz()
            );
            int maxCoord = getMaxTexCoord(gl);
            if ( textureData.getSx() > maxCoord  || textureData.getSy() > maxCoord || textureData.getSz() > maxCoord ) {
                logger.warn(
                        "Exceeding max coord in one or more size of texture data {}.  Results unpredictable.",
                        textureData.getFilename()
                );
            }

            gl.glActiveTexture( textureSymbolicId );
            if (OpenGLUtils.reportError( "glActiveTexture", gl, textureName )) {
                return false;
            }

            gl.glEnable( GL2.GL_TEXTURE_3D );
            if (OpenGLUtils.reportError( "glEnable", gl, textureName )) {
                return false;
            }

            gl.glBindTexture( GL2.GL_TEXTURE_3D, textureName );
            if (OpenGLUtils.reportError( "glBindTexture", gl, textureName )) {
                return false;
            }

            gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE);
            if (OpenGLUtils.reportError( "glTexEnv MODE-REPLACE", gl, textureName )) {
                return false;
            }

            try {
                gl.glTexImage3D(
                        GL2.GL_TEXTURE_3D,
                        0, // mipmap level
                        getInternalFormat(), // as stored INTO graphics hardware, w/ srgb info (GLint internal format)
                        textureData.getSx(), // width
                        textureData.getSy(), // height
                        textureData.getSz(), // depth
                        0, // border
                        getVoxelComponentOrder(), // voxel component order (GLenum format)
                        getVoxelComponentType(), // voxel component type=packed RGBA values(GLenum type)
                        null
                );
                if (OpenGLUtils.reportError("Tex-image-allocate", gl, textureName)) {
                    return false;
                }
                
                if ( 1==1 || logger.isDebugEnabled() ) {
                    dumpGlTexImageCall(
                        GL2.GL_TEXTURE_3D,
                        0, // mipmap level
                        getInternalFormat(), // as stored INTO graphics hardware, w/ srgb info (GLint internal format)
                        textureData.getSx(), // width
                        textureData.getSy(), // height
                        textureData.getSz(), // depth
                        0, // border
                        getVoxelComponentOrder(), // voxel component order (GLenum format)
                        getVoxelComponentType() // voxel component type=packed RGBA values(GLenum type)
                    );
                }

                long expectedRemaining = (long)textureData.getSx() * (long)textureData.getSy() * (long)textureData.getSz()
                        * (long)textureData.getPixelByteCount() * (long)textureData.getChannelCount();
                if ( expectedRemaining != textureData.getTextureData().length() ) {
                    logger.warn( "Invalid remainder vs texture data dimensions.  Sx=" + textureData.getSx() +
                            " Sy=" + textureData.getSy() + " Sz=" + textureData.getSz() +
                            " storageFmtReq=" + getStorageFormatMultiplier() +
                            " pixelByteCount=" + textureData.getPixelByteCount() +
                            ";  total remaining is " +
                            textureData.getTextureData().length() + " " + textureData.getFilename() +
                            ";  expected remaining is " + expectedRemaining
                    );
                }

                for ( VolumeDataChunk volumeDataChunk: textureData.getTextureData().getVolumeChunks() ) {
                    ByteBuffer data = ByteBuffer.wrap( volumeDataChunk.getData() );
                    data.rewind();

                    logger.debug("Sub-image: {}, {}, " + volumeDataChunk.getStartZ(), volumeDataChunk.getStartX(), volumeDataChunk.getStartY() );
                    gl.glTexSubImage3D(
                            GL2.GL_TEXTURE_3D,
                            0, // mipmap level
                            volumeDataChunk.getStartX(),
                            volumeDataChunk.getStartY(),
                            volumeDataChunk.getStartZ(),
                            volumeDataChunk.getWidth(), // width
                            volumeDataChunk.getHeight(), // height
                            volumeDataChunk.getDepth(), // depth
                            getVoxelComponentOrder(), // voxel component order (GLenum format)
                            getVoxelComponentType(), // voxel component type=packed RGBA values(GLenum type)
                            data
                    );
                    if (OpenGLUtils.reportError("Tex-sub-image", gl, textureName)) {
                        return false;
                    }

                }

            } catch ( Exception exGlTexImage ) {
                logger.error(
                        "Exception reported during texture upload of NAME:OFFS={}, FORMAT:COMP-ORDER:MULTIPLIER:VC-TYPE={}",
                        this.textureName + ":" + this.getTextureOffset(),
                        getConstantName(this.getInternalFormat()) + ":" +
                        getConstantName(this.getVoxelComponentOrder()) + ":" +
                        this.getStorageFormatMultiplier() + ":" + 
                        getConstantName(this.getVoxelComponentType()),
                        exGlTexImage
                );
            }
            if (OpenGLUtils.reportError( "glTexImage", gl, textureName )) {
                return false;
            }
            gl.glBindTexture( GL2.GL_TEXTURE_3D, 0 );
            gl.glDisable( GL2.GL_TEXTURE_3D );
            if (OpenGLUtils.reportError( "disable-tex", gl, textureName )) {
                return false;
            }

            hasBeenUploaded = true;

            // DEBUG
            //if ( expectedRemaining < 1000000 )
            //    testTextureContents(gl); // NOT for very large files!
        }
        return rtnVal;
    }

    @Deprecated
    @SuppressWarnings("unused")
    /** This uploads all textures as one contiguous piece, by concatenating all chunks. */
    public void contigUploadTexture( GL2 gl ) {
        if ( ! isInitialized ) {
            logger.error("Attempted to upload texture before mediator was initialized.");
            throw new RuntimeException("Failed to upload texture");
        }

        // Just accumulate all the volume chunks into one big array, and send that over in one burst.
        if ( textureData.getTextureData().getVolumeChunks() != null ) {

            logger.debug(
                    "[" +
                            textureData.getFilename() +
                            "]: Coords are " + textureData.getSx() + " * " + textureData.getSy() + " * " + textureData.getSz()
            );
            int maxCoord = getMaxTexCoord(gl);
            if ( textureData.getSx() > maxCoord  || textureData.getSy() > maxCoord || textureData.getSz() > maxCoord ) {
                logger.warn(
                        "Exceeding max coord in one or more size of texture data {}.  Results unpredictable.",
                        textureData.getFilename()
                );
            }

            gl.glActiveTexture( textureSymbolicId );
            OpenGLUtils.reportError( "glActiveTexture", gl, textureName );

            gl.glEnable( GL2.GL_TEXTURE_3D );
            OpenGLUtils.reportError( "glEnable", gl, textureName );

            gl.glBindTexture( GL2.GL_TEXTURE_3D, textureName );
            OpenGLUtils.reportError( "glBindTexture", gl, textureName );

            gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE);
            OpenGLUtils.reportError( "glTexEnv MODE-REPLACE", gl, textureName );

            try {
                byte[] rawBytes = new byte[ (int)textureData.getTextureData().length() ];
                int nextInx = 0;
                for ( VolumeDataChunk volumeDataChunk: textureData.getTextureData().getVolumeChunks() ) {
                    System.arraycopy( volumeDataChunk.getData(), 0, rawBytes, nextInx, volumeDataChunk.getData().length );
                    nextInx += volumeDataChunk.getData().length;
                }

                ByteBuffer data = ByteBuffer.wrap( rawBytes );
                data.rewind();

                gl.glTexImage3D(
                        GL2.GL_TEXTURE_3D,
                        0, // mipmap level
                        getInternalFormat(), // as stored INTO graphics hardware, w/ srgb info (GLint internal format)
                        textureData.getSx(), // width
                        textureData.getSy(), // height
                        textureData.getSz(), // depth
                        0, // border
                        getVoxelComponentOrder(), // voxel component order (GLenum format)
                        getVoxelComponentType(), // voxel component type=packed RGBA values(GLenum type)
                        data
                );

            } catch ( Exception exGlTexImage ) {
                logger.error(
                        "Exception reported during texture upload of NAME:OFFS={}, FORMAT:COMP-ORDER:MULTIPLIER={}",
                        this.textureName + ":" + this.getTextureOffset(),
                        getConstantName(this.getInternalFormat()) + ":" +
                        getConstantName(this.getVoxelComponentOrder()) + ":" +
                        getConstantName(this.getStorageFormatMultiplier()),
                        exGlTexImage
                );
            }
            OpenGLUtils.reportError( "glTexImage", gl, textureName );

            gl.glBindTexture( GL2.GL_TEXTURE_3D, 0 );
            gl.glDisable( GL2.GL_TEXTURE_3D );
            OpenGLUtils.reportError( "disable-tex", gl, textureName );

            hasBeenUploaded = true;

        }

    }

    /** Release the texture data memory from the GPU. */
    public void deleteTexture( GL2 gl ) {
        if ( hasBeenUploaded ) {
            OpenGLUtils.reportError( "tex-mediator: upon entry to delete tex", gl, textureName );
            IntBuffer textureNameBuffer = IntBuffer.allocate( 1 );
            textureNameBuffer.put( textureName );
            textureNameBuffer.rewind();
            textureNameBuffer.rewind();
            gl.glDeleteTextures( 1, textureNameBuffer );
            OpenGLUtils.reportError( "tex-mediator: delete texture", gl, textureName );
            hasBeenUploaded = false;
        }
    }

    public int getTextureOffset() {
        return textureOffset;
    }

    /**
     * NOTE: Forcing coord back to 1, if greater, or back to 0, if lower, does not change appearance noticeably,
     * nor alleviate the end-on-X rendering problem.
     *
     * @param voxelCoord a voxel coordinate set for geometry.
     * @return texture coordinate set that corresponds, in range 0..1
     */
    public float[] textureCoordFromVoxelCoord(float[] voxelCoord) {
        float[] tc = {voxelCoord[0], voxelCoord[1], voxelCoord[2]}; // micrometers, origin at center
        int[] voxels = { textureData.getSx(), textureData.getSy(), textureData.getSz() };
        Double[] volumeMicrometers = textureData.getVolumeMicrometers();
        Double[] voxelMicrometers = textureData.getVoxelMicrometers();
        for (int i =0; i < 3; ++i) {
            // Move origin to upper left corner
            tc[i] += volumeMicrometers[i] / 2.0; // micrometers, origin at corner
            // Rescale from micrometers to voxels
            tc[i] /= voxelMicrometers[i]; // voxels, origin at corner
            // Rescale from voxels to texture units (range 0-1)
            tc[i] /= voxels[i]; // texture units
        }

        return tc;
    }

    public Double[] getVolumeMicrometers() {
        return textureData.getVolumeMicrometers();
    }

    public Double[] getVoxelMicrometers() {
        return textureData.getVoxelMicrometers();
    }

    /** Carry out all ops to ensure textures ready.  Return False if failed. */
    public boolean setupTexture( GL2 gl ) {
        boolean rtnVal = true;
        OpenGLUtils.reportError("Entering setupTexture", gl, textureName);
        logger.debug( "Texture Data for {} has interp of {}.", textureData.getFilename(),
                getConstantName( textureData.getInterpolationMethod() ) );

        if ( ! isInitialized ) {
            logger.error("Attempting to setup texture before mediator has been initialized.");
            throw new RuntimeException( "Texture setup failed." );
        }
        gl.glActiveTexture( textureSymbolicId );
        if (OpenGLUtils.reportError( "setupTexture glActiveTexture", gl, textureName )) {
            return false;
        }
        gl.glBindTexture( GL2.GL_TEXTURE_3D, textureName );
        if (OpenGLUtils.reportError( "setupTexture glBindTexture", gl, textureName )) {
            return false;
        }
        gl.glTexParameteri(GL2.GL_TEXTURE_3D, GL2.GL_TEXTURE_MIN_FILTER, textureData.getInterpolationMethod() );
        if (OpenGLUtils.reportError( "setupTexture glTexParam MIN FILTER", gl, textureName )) {
            return false;
        }
        gl.glTexParameteri(GL2.GL_TEXTURE_3D, GL2.GL_TEXTURE_MAG_FILTER, textureData.getInterpolationMethod() );
        if (OpenGLUtils.reportError( "setupTexture glTexParam MAG_FILTER", gl, textureName )) {
            return false;
        }
        gl.glTexParameteri(GL2.GL_TEXTURE_3D, GL2.GL_TEXTURE_WRAP_R, GL2.GL_CLAMP_TO_BORDER);
        if (OpenGLUtils.reportError( "setupTexture glTexParam TEX-WRAP-R", gl, textureName )) {
            return false;
        }
        gl.glTexParameteri(GL2.GL_TEXTURE_3D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_BORDER);
        if (OpenGLUtils.reportError( "setupTexture glTexParam TEX-WRAP-S", gl, textureName )) {
            return false;
        }
        gl.glTexParameteri(GL2.GL_TEXTURE_3D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP_TO_BORDER);
        if (OpenGLUtils.reportError( "setupTexture glTexParam TEX-WRAP-T", gl, textureName )) {
            return false;
        }
        return rtnVal;
    }

    /**
     * This debugging method will take the assumptions inherent in this mediator, and use them to
     * grab the stuff in the graphics memory.  If it has been loaded, and contains non-zero data
     * (any), this method will tell that, and any other kinds of checks that may be coded below.
     *
     * Please do not remove this "apparently dead" code, as it may be called to debug difficult-to
     * -track problems with texture loading.
     *
     * @param gl for invoking the required OpenGL method.
     */
    @SuppressWarnings("unused")
    public void testTextureContents( GL2 gl ) {
        gl.glActiveTexture( textureSymbolicId );
        OpenGLUtils.reportError( "testTextureContents glActiveTexture", gl, textureName );
        gl.glBindTexture( GL2.GL_TEXTURE_3D, textureName );
        OpenGLUtils.reportError( "testTextureContents glBindTexture", gl, textureName );

        int pixelByteCount = textureData.getPixelByteCount();
        int bufferSize = textureData.getSx() * textureData.getSy() * textureData.getSz() *
                pixelByteCount * textureData.getChannelCount();

        byte[] rawBuffer = new byte[ bufferSize ];
        ByteBuffer buffer = ByteBuffer.wrap(rawBuffer);
        gl.glGetTexImage( GL2.GL_TEXTURE_3D, 0, getVoxelComponentOrder(), getVoxelComponentType(), buffer );
        OpenGLUtils.reportError( "TEST: Getting texture for testing", gl, textureName );

        buffer.rewind();

        testRawBufferContents(pixelByteCount, rawBuffer);

    }

    /** This should be called immediately after some openGL call, to check error status. */
    public void setTextureData( TextureDataI textureData ) {
        this.textureData = textureData;
    }

    /**
     * If this has been set, it will be used to transform every point in the coordinate set.
     * 
     * @param transformMatrix applied to all points.
     */
    public void setTransformMatrix(float[] transformMatrix) {        
        textureData.setTransformMatrix(transformMatrix);
    }
    
    /**
     * Convenience pass-through method to get coord transform matrix.
     * @return 
     */
    public float[] getTransformMatrix() {
        return textureData.getTransformMatrix();
    }

    private int getStorageFormatMultiplier() {
        int orderId =  getVoxelComponentOrder();
        if ( orderId == GL2.GL_BGRA ) {
            return 4;
        }
        else if ( orderId == GL2.GL_LUMINANCE16_ALPHA16 ) {
            return 4;
        }
        else {
            return 1;
        }
    }

    //--------------------------- Helpers for glTexImage3D
    // NOTES on these helpers:
    //  -  It is often the case that "format" and "internal format" can and should be the same value.
    //  -  "voxel component type" is aka "type" in the OpenGL documentation.
    //  -  "voxel component order" is aka "format" in the OpenGL docs, but can confuse with internal format.

    private int getVoxelComponentType() {
        int rtnVal = GL2.GL_UNSIGNED_INT_8_8_8_8_REV;
        if ( textureData.getExplicitVoxelComponentType() != TextureDataI.UNSET_VALUE ) {
            rtnVal = textureData.getExplicitVoxelComponentType();
        }
        else {
            // This: tested vs 1-byte mask.
            if ( textureData.getPixelByteCount()  == 1 ) {
                rtnVal = GL2.GL_UNSIGNED_BYTE;
            }

            // This throws excepx for current read method.
            if ( textureData.getPixelByteCount() == 2 ) {
                rtnVal = GL2.GL_UNSIGNED_SHORT;
            }
        }

        logger.debug( "Got voxel component type of {} for {}.", getConstantName( rtnVal ), textureData.getFilename() );

        return rtnVal;
        // BLACK SCREEN. GL2.GL_UNSIGNED_BYTE_3_3_2,  // BLACK SCREEN for 143/266
        // GL2.GL_UNSIGNED_SHORT_4_4_4_4_REV, // TWO-COLOR SCREEN for 143/266
        // GL2.GL_UNSIGNED_SHORT_5_5_5_1, // 3-Color Screen for 143/266
        // GL2.GL_UNSIGNED_SHORT_1_5_5_5_REV, // Different 3-Color Screen for 143/266
        // GL2.GL_UNSIGNED_SHORT_5_6_5, // BLACK SCREEN for 143/266
        // GL2.GL_UNSIGNED_SHORT_5_6_5_REV, // BLACK SCREEN for 143/266
        // GL2.GL_BYTE, // YBD for 143/266
        // GL2.GL_BYTE, // YBD for 143/266
        // GL2.GL_UNSIGNED_BYTE, // Grey Neurons for 143/266
        // GL2.GL_UNSIGNED_SHORT, // Stack Trace for 143/266
    }

    private int getMaxTexCoord(GL2 gl) {
        IntBuffer rtnBuf = IntBuffer.allocate( 1 );
        rtnBuf.rewind();
        gl.glGetIntegerv(GL2.GL_MAX_TEXTURE_SIZE, rtnBuf);
        int[] rtnVals = rtnBuf.array();
        return rtnVals[0];
    }

    private int getInternalFormat() {
        int internalFormat = GL2.GL_RGBA;
        if ( textureData.getExplicitInternalFormat() != TextureDataI.UNSET_VALUE ) {
            internalFormat = textureData.getExplicitInternalFormat();
        }
        else {
            if (textureData.getColorSpace() == VolumeDataAcceptor.TextureColorSpace.COLOR_SPACE_SRGB)
                internalFormat = GL2.GL_SRGB8_ALPHA8;

            // This: tested against a mask file.
            if (textureData.getChannelCount() == 1) {
                internalFormat = GL2.GL_LUMINANCE;

                if (textureData.getPixelByteCount() == 2) {
                    internalFormat = GL2.GL_LUMINANCE16;
                }
            }

            if (textureData.getColorSpace() == VolumeDataAcceptor.TextureColorSpace.COLOR_SPACE_RGB) {
                internalFormat = GL2.GL_RGB;
            }

        }
        logger.debug( "internalFormat = {} for {}", getConstantName( internalFormat ), textureData.getFilename() );
        return internalFormat;
    }

    private int getVoxelComponentOrder() {
        int rtnVal = GL2.GL_BGRA;
        if ( textureData.getExplicitVoxelComponentOrder() != TextureDataI.UNSET_VALUE ) {
            rtnVal = textureData.getExplicitVoxelComponentOrder();
        }
        else {
            if ( textureData.getChannelCount() == 1 ) {
                rtnVal = GL2.GL_LUMINANCE;
            }
        }

        logger.debug( "Voxel Component order/glTexImage3D 'format' {} for {}.", getConstantName( rtnVal ), textureData.getFilename() );
        return rtnVal;
    }

    //--------------------------- End: Helpers for glTexImage3D

    private void testRawBufferContents(int pixelByteCount, byte[] rawBuffer) {
        Map<Integer,Integer> allFoundFrequencies = new HashMap<>();

        int nonZeroCount = 0;
        for (byte aRawBuffer : rawBuffer) {
            if (aRawBuffer != 0) {
                nonZeroCount++;
            }
        }
        if ( nonZeroCount == 0 ) {
            logger.warn( "TEST: All-zero texture loaded for {} by name.", textureName );
        }
        else {
            logger.info( "TEST: Found {} non-zero bytes in texture {} by name.", nonZeroCount, textureName );

            byte[] voxel = new byte[pixelByteCount];
            int leftByteNonZeroCount = 0;
            int rightByteNonZeroCount = 0;
            for ( int i = 0; i < rawBuffer.length; i += pixelByteCount ) {
                boolean voxelNonZero = false;
                for ( int voxOffs = 0; voxOffs < pixelByteCount; voxOffs ++ ) {
                    voxel[ voxOffs ] = rawBuffer[ i+voxOffs ];
                    if ( voxel[ voxOffs ] > 0 ) {
                        voxelNonZero = true;
                    }

                }
                if ( voxelNonZero ) {
                    if ( voxel[ 0 ] != 0 ) {
                        leftByteNonZeroCount ++;
                    }
                    else if ( voxel[ pixelByteCount - 1 ] > 0 ) {
                        rightByteNonZeroCount ++;
                    }
                }

                for ( int j = 0; j < pixelByteCount; j++ ) {
                    Integer count = allFoundFrequencies.get( (int)voxel[ j ] );
                    if ( count == null ) {
                        count = 0;
                    }
                    allFoundFrequencies.put( (int)voxel[ j ], ++count );
                }
            }

            logger.info( "TEST: There are {} nonzero left-most bytes.", leftByteNonZeroCount );
            logger.info( "TEST: There are {} nonzero right-most bytes.", rightByteNonZeroCount );

        }

        logger.info("Texture Values Dump---------------------");
        for ( Integer key: allFoundFrequencies.keySet() ) {
            int foundValue = key;
            if ( foundValue < 0 ) {
                foundValue = 256 + key;
            }
            logger.info("Found {}  occurrences of {}.", allFoundFrequencies.get( key ), foundValue );
        }
        logger.info("End: Texture Values Dump---------------------");
    }
    
    public static void dumpGlTexImageCall(
            int texImgType,
            int mipMapLevel,
            int internalFormat,
            int sx,
            int sy,
            int sz,
            int border,
            int voxCompOrder,
            int voxCompType
    ) {
        System.out.println(
                "glTexImage params:  texImgType=" + getConstantName( texImgType )+
                ", mipMapLevel=" + mipMapLevel +
                ", internalFormat=" + getConstantName( internalFormat ) +
                ", border=" + border +
                ", sx= " + sx + ", sy=" + sy + ", sz=" + sz +
                ", componentOrder=" + getConstantName( voxCompOrder ) +
                ". componentType=" + getConstantName( voxCompType )
        );
    }

    /** Gets a string name of an OpenGL constant used in this class.  For debugging purposes. */
    public static String getConstantName( Integer openGlEnumConstant ) {
        String rtnVal;
        if ( glConstantToName == null ) {
            glConstantToName = new HashMap<>();
            glConstantToName.put( GL2.GL_TEXTURE_3D, "GL2.GL_TEXTURE_3D" );
            glConstantToName.put( GL2.GL_TEXTURE_2D, "GL2.GL_TEXTURE_2D" );
            glConstantToName.put( GL2.GL_TEXTURE_1D, "GL2.GL_TEXTURE_1D" );
            glConstantToName.put( GL2.GL_UNSIGNED_INT_8_8_8_8_REV, "GL2.GL_UNSIGNED_INT_8_8_8_8_REV" );
            glConstantToName.put( GL2.GL_UNSIGNED_INT_8_8_8_8, "GL2.GL_UNSIGNED_INT_8_8_8_8" );
            glConstantToName.put( GL2.GL_UNSIGNED_BYTE, "GL2.GL_UNSIGNED_BYTE" );
            glConstantToName.put( GL2.GL_UNSIGNED_SHORT, "GL2.GL_UNSIGNED_SHORT" );

            glConstantToName.put( GL2.GL_LUMINANCE, "GL2.GL_LUMINANCE" );
            glConstantToName.put( GL2.GL_LUMINANCE_ALPHA, "GL2.GL_LUMINANCE_ALPHA" );
            glConstantToName.put( GL2.GL_SRGB8_ALPHA8, "GL2.GL_SRGB8_ALPHA8" );
            glConstantToName.put( GL2.GL_LUMINANCE16, "GL2.GL_LUMINANCE16" );
            glConstantToName.put( GL2.GL_RGBA, "GL2.GL_RGBA" );
            glConstantToName.put( GL2.GL_RGB, "GL2.GL_RGB" );
            glConstantToName.put( GL2.GL_RG, "GL2.GL_RG" );
            glConstantToName.put( GL2.GL_RED, "GL2.GL_RED" );
            glConstantToName.put( GL2.GL_BGRA, "GL2.GL_BGRA" );
            glConstantToName.put( GL2.GL_RGBA16, "GL2.GL_RGBA16");

            glConstantToName.put( GL2.GL_LINEAR, "GL2.GL_LINEAR" );
            glConstantToName.put( GL2.GL_NEAREST, "GL2.GL_NEAREST" );

            glConstantToName.put( GL2.GL_UNSIGNED_BYTE_3_3_2, "GL2.GL_UNSIGNED_BYTE_3_3_2" );
            glConstantToName.put( GL2.GL_UNSIGNED_SHORT_4_4_4_4_REV, "GL2.GL_UNSIGNED_SHORT_4_4_4_4_REV" );
            glConstantToName.put( GL2.GL_UNSIGNED_SHORT_5_5_5_1, "GL2.GL_UNSIGNED_SHORT_5_5_5_1" );
            glConstantToName.put( GL2.GL_UNSIGNED_SHORT_5_6_5, "GL2.GL_UNSIGNED_SHORT_5_6_5" );
            glConstantToName.put( GL2.GL_UNSIGNED_SHORT_1_5_5_5_REV, "GL2.GL_UNSIGNED_SHORT_1_5_5_5_REV" );
            glConstantToName.put( GL2.GL_BYTE, "GL2.GL_BYTE" );
            glConstantToName.put( GL2.GL_SHORT, "GL2.GL_SHORT" );
            glConstantToName.put( GL2.GL_UNSIGNED_BYTE, "GL2.GL_UNSIGNED_BYTE" );
            glConstantToName.put( GL2.GL_UNSIGNED_SHORT, "GL2.GL_UNSIGNED_SHORT" );
            glConstantToName.put( GL2.GL_LUMINANCE16_ALPHA16, "GL2.GL_LUMINANCE16_ALPHA16");

            glConstantToName.put( javax.media.opengl.GL2GL3.GL_BGRA, "GL2GL3.GL_BGRA" );
            glConstantToName.put( javax.media.opengl.GL2GL3.GL_RGB, "GL2GL3.GL_RGB" );
            glConstantToName.put( javax.media.opengl.GL2GL3.GL_UNSIGNED_SHORT, "GL2GL3.GL_UNSIGNED_SHORT" );

        }
        rtnVal = glConstantToName.get( openGlEnumConstant );
        if ( rtnVal == null ) {
            rtnVal = "::Unknown " + openGlEnumConstant + "/"+ Integer.toHexString( openGlEnumConstant );
        }

        return rtnVal;
    }

}