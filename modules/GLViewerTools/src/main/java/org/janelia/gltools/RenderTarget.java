
package org.janelia.gltools;

import org.janelia.gltools.texture.Texture2d;
import com.jogamp.common.nio.Buffers;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.media.opengl.DebugGL3;
import javax.media.opengl.GL3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLContext;

/**
 * One (of possibly many) textures used as render targets in a FrameBuffer
 * for offscreen rendering.
 * @author Christopher Bruns <brunsc at janelia.hhmi.org>
 */
public class RenderTarget extends Texture2d 
{
    private final int attachment;
    private boolean isAllocated = false;
    private ByteBuffer hostTextureBuffer; // for cacheing host-side version of texture (on demand)
    private boolean hostBufferNeedsUpdate = true; // TODO use this...
    // private int paddedWidth;
    private int widthPadInBytes = 0;
    private boolean dirty;

    public RenderTarget(
            int width, int height, 
            int internalFormat,
            int attachment) 
    {
        this.width = width;
        this.height = height;
        this.internalFormat = internalFormat;
        this.attachment = attachment;
        mipMapLevel = 0;
        magFilter = GL3.GL_NEAREST;
        minFilter = GL3.GL_NEAREST;
        updateParameters(internalFormat);
        generateMipmaps = false;
        useImmutableTexture = true;
        unpackAlignment = 4;
    }
    
    public int getAttachment() {
        return attachment;
    }
    
    /**
     * Get cached host-side image intensity value
     * @param glad
     * @param x
     * @param y
     * @param channel
     * @return -1 on failure, otherwise intensity
     */
    public synchronized double getIntensity(GLAutoDrawable glad, int x, int y, int channel) {
        // System.out.println("pick x = "+x+"; pick y = "+y+"; width = "+width+"("+paddedWidth+"); height = "+height);
        if (handle == 0) return -1; // This texture is not even initialized.
        if (hostBufferNeedsUpdate && glad == null) // stale and no source of pixels
            return -1;
        if (width*height*numberOfComponents == 0) return -1;
        if (x < 0) return -1;
        if (x >= width) return -1;
        if (y < 0) return -1;
        if (y >= height) return -1;
        if (channel >= numberOfComponents) return -1;
        if (channel < 0) return -1;
        
        double result = -1;

        int totalBytes = width*height*bytesPerIntensity*numberOfComponents + height*widthPadInBytes;
        if ( (hostTextureBuffer != null) && (hostTextureBuffer.capacity() < totalBytes) ) {
            // release old buffer
            hostTextureBuffer = null;
            hostBufferNeedsUpdate = true;
        }
        if (hostTextureBuffer == null) {
            hostTextureBuffer = Buffers.newDirectByteBuffer(totalBytes);
            hostTextureBuffer.order(ByteOrder.nativeOrder());
            hostBufferNeedsUpdate = true;
        }
        if (hostBufferNeedsUpdate) {
            hostTextureBuffer.rewind();
            GLContext context =  glad.getContext();
            if (context.makeCurrent() == GLContext.CONTEXT_CURRENT) {
                try {
                    GL3 gl = new DebugGL3(glad.getGL().getGL3());
                    // GL3 gl = context.getGL().getGL3();
                    bind(gl);
                    gl.glPixelStorei(GL3.GL_PACK_ALIGNMENT, unpackAlignment);
                    gl.glGetTexImage(textureTarget, 0, format, type, hostTextureBuffer);
                    unbind(gl);
                    hostBufferNeedsUpdate = false;
                    hostTextureBuffer.rewind();
                } finally {
                    if (context != null)
                        context.release();
                }
            }
        }
        int sc = 1;
        int sx = numberOfComponents*sc;
        int sy = width*sx + widthPadInBytes/bytesPerIntensity;
        int offset = sx*x + sy*y + sc*channel;
        if (format == GL3.GL_DEPTH_COMPONENT)
            result = hostTextureBuffer.asFloatBuffer().get(offset);
        else if (type == GL3.GL_FLOAT)
            result = hostTextureBuffer.asFloatBuffer().get(offset);
        else if (bytesPerIntensity == 4)
            result = hostTextureBuffer.asIntBuffer().get(offset);
        else if (bytesPerIntensity == 2)
            result = hostTextureBuffer.asShortBuffer().get(offset) & 0xffff;
        else
            result = hostTextureBuffer.get(offset) & 0xff;
        return result;
    }
            
    @Override
    public void init(GL3 gl) {
        if (width*height == 0)
            return;
        
        initAndLeaveBound(gl);

        setSizeAndAllocate(gl, width, height);
        
        unbind(gl);
    }
    
    public boolean reshape(GL3 gl, int w, int h) {
        if ( (width == w) && (height == h) )
            return false;
        setSizeAndAllocate(gl, w, h);
        return true;
    }

    public void setHostBufferNeedsUpdate(boolean hostBufferNeedsUpdate) {
        this.hostBufferNeedsUpdate = hostBufferNeedsUpdate;
    }

    private synchronized void setSizeAndAllocate(GL3 gl, int w, int h) {
        // glTexStorage2D cannot be resized, so use glTexImage3D
        // http://stackoverflow.com/questions/23362497/how-can-i-resize-existing-texture-attachments-at-my-framebuffer
        // gl.glTexStorage2D(

        // Setting width/height here corrects aspect ratio problem after resizing
        width = w;
        height = h;
        
        bind(gl);
        
        // Pad width to multiple of unpackAlignment
        // TODO - this only works when numberOfComponents and bytesPerIntensity are powers of 2?
        // i.e. 3 component images might need more help here...
        // int pixelAlignment = unpackAlignment;
        int unpaddedScanLineBytes = width * numberOfComponents * bytesPerIntensity;
        int pad = unpaddedScanLineBytes % unpackAlignment;
        if (pad != 0)
            pad = unpackAlignment - pad;
        widthPadInBytes = pad;

        // glTexStorage2D is immutable, so upon resizing, we need to recreate everything
        if (isAllocated) {
            unbind(gl);
            dispose(gl);
            initAndLeaveBound(gl);
        }

        isAllocated = true;
        unbind(gl);
        hostBufferNeedsUpdate = true;
        hostTextureBuffer = null;
    }
    
    /*  // clear() method causes problems on ATI
    // Presumably because render textures should not be writeable in general.
    // ESPECIALLY MSAA render textures, which we use sometimes.
    // I'm not sure why the nvidia cards did not complain, now that I think about it.
    public void clear(GL3 gl) {
        int totalBytes = width*height*bytesPerIntensity*numberOfComponents + height*widthPadInBytes;
        ByteBuffer clearBuffer = Buffers.newDirectByteBuffer(totalBytes);
        clearBuffer.order(ByteOrder.nativeOrder());
        clearBuffer.clear();
        
        gl.glTexSubImage2D(
                textureTarget, 
                0, // mipmap level
                0, 0, // xy offset
                width, height,
                format, 
                type, 
                clearBuffer);
    }
     */
    
    // RenderTargets have nothing to upload.
    @Override
    protected void uploadTexture(GL3 gl) 
    {}

    public boolean isDirty() {
        return dirty;
    }
    
    public void setDirty(boolean d) {
        dirty = d;
    }
}
