/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.janelia.it.workstation.gui.viewer3d.picking;

//import javax.media.opengl.GL2GL3;
import java.nio.ByteBuffer;
import javax.media.opengl.GL3;
import javax.media.opengl.GLAutoDrawable;

import java.nio.IntBuffer;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import static org.janelia.it.workstation.gui.viewer3d.OpenGLUtils.reportError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Setup a buffer for IDs-as-renders, object selection.  Support
 * carrying out the prep, teardown, and actual selection.  Meant
 * to be called from a GLActor.
 * 
 * @see http://www.opengl-tutorial.org/intermediate-tutorials/tutorial-14-render-to-texture/
 *
 * @author fosterl
 */
public class RenderedIdPicker {
    private int frameBufId;
    private int colorTextureId_0;
    private int colorTextureId_1;
    private int depthBufferId;
    private int viewportWidth;
    private int viewportHeight;
	
	private Logger logger = LoggerFactory.getLogger(RenderedIdPicker.class);

    public RenderedIdPicker() {
    }
    
    /**
     * Call this for the one-time-only steps.
	 * @see https://www.opengl.org/wiki/Framebuffer_Object_Examples
     * @param glDrawable 
     */
    public void init(GLAutoDrawable glDrawable, int width, int height) {        
		this.viewportWidth = width;
		this.viewportHeight = height;				

// Temporary Cruft-Hut.		
//        prepareTexture(gl, colorTextureId_0);
//		prepareTexture(gl, colorTextureId_1);

        // Set the list of draw buffers.
//        int drawBuffers[] = new int[] {
//			GL3.GL_COLOR_ATTACHMENT0,
//			GL3.GL_DEPTH_ATTACHMENT,
//			GL3.GL_COLOR_ATTACHMENT1
//	    };
//		gl.glDrawBuffer(3);
//		gl.glBindTexture(GL3.GL_TEXTURE_2D, colorTextureId_0);
//		gl.glFramebufferTexture2D(GL3.GL_FRAMEBUFFER, GL3.GL_COLOR_ATTACHMENT0, GL3.GL_TEXTURE_2D, colorTextureId_0, 0);   
//		gl.glBindTexture(GL3.GL_TEXTURE_2D, colorTextureId_1);
//		gl.glFramebufferTexture2D(GL3.GL_FRAMEBUFFER, GL3.GL_COLOR_ATTACHMENT1, GL3.GL_TEXTURE_2D, colorTextureId_1, 0);   
//		IntBuffer drawBuffersBuffer = IntBuffer.allocate(3);
//		drawBuffersBuffer.put(drawBuffers);
//        gl.glDrawBuffers(drawBuffers.length, drawBuffersBuffer);
		
		GL3 gl = glDrawable.getGL().getGL2().getGL3();
        
		// Test the version.
		logger.info("OpenGL version {}.", gl.glGetString(GL3.GL_VERSION));

		/*
		  The steps:
		  - get an FBO
		  - bind to FBO
		  - set its viewport
		  - get attachments: 2 for color, 1 for depth.
		  - attach all said attachments to the framebuffer.
		  - check status of whole FBO: is it complete?
		*/
        // The framebuffer, which regroups 0, 1, or more textures, and 0 or 1 depth buffer.
        IntBuffer exchange = IntBuffer.allocate(3);
        exchange.rewind();
        gl.glGenFramebuffers(1, exchange);
        exchange.rewind();
        frameBufId = exchange.get();
        		
        gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, frameBufId);
		reportError(gl, "Binding Framebuffer");
		
		gl.glViewport(0, 0, viewportWidth, viewportHeight);
		reportError(gl, "Viewport for Framebuffer");

		// Attach the renderbuffers.
		exchange.rewind();
		gl.glGenRenderbuffers(3, exchange);
		reportError(gl, "Gen render texture Buffer");
		colorTextureId_0 = exchange.get();
		depthBufferId = exchange.get();
		colorTextureId_1 = exchange.get();
		
		gl.glBindRenderbuffer(GL3.GL_RENDERBUFFER, colorTextureId_0);
		reportError(gl, "Bind Color-0 Render Buffer");
        gl.glRenderbufferStorage(GL3.GL_RENDERBUFFER, GL3.GL_RGB, viewportWidth, viewportHeight);
		reportError(gl, "Render-Buffer Color-0 Attachment");		
		gl.glFramebufferRenderbuffer(GL3.GL_FRAMEBUFFER, GL3.GL_COLOR_ATTACHMENT0, GL3.GL_RENDERBUFFER, colorTextureId_0);
		reportError(gl, "Render Buffer Color Storage-0");
		
		// Establish depth render buffer.
		gl.glBindRenderbuffer(GL3.GL_RENDERBUFFER, depthBufferId);
		reportError(gl, "Bind Depth Render Buffer");
        gl.glRenderbufferStorage(GL3.GL_RENDERBUFFER, GL3.GL_DEPTH_COMPONENT24, viewportWidth, viewportHeight);
		reportError(gl, "Render-Buffer Depth Attachment");		
		gl.glFramebufferRenderbuffer(GL3.GL_FRAMEBUFFER, GL3.GL_DEPTH_ATTACHMENT, GL3.GL_RENDERBUFFER, depthBufferId);
		reportError(gl, "Render Buffer Storage-0");
		
		gl.glBindRenderbuffer(GL3.GL_RENDERBUFFER, colorTextureId_1);
		reportError(gl, "Bind Color-0 Render Buffer");
        gl.glRenderbufferStorage(GL3.GL_RENDERBUFFER, GL3.GL_RGB, viewportWidth, viewportHeight);
		reportError(gl, "Render-Buffer Color-0 Attachment");		
		gl.glFramebufferRenderbuffer(GL3.GL_FRAMEBUFFER, GL3.GL_COLOR_ATTACHMENT1, GL3.GL_RENDERBUFFER, colorTextureId_1);
		reportError(gl, "Render Buffer Color Storage-0");
		
		int status = gl.glCheckFramebufferStatus(GL3.GL_FRAMEBUFFER);
		if (status != GL3.GL_FRAMEBUFFER_COMPLETE) {
			logger.error("Failed to establish framebuffer: {}", decodeFramebufferStatus(status));
		}
		else {
			logger.info("Framebuffer complete.");
		}
		reportError(gl, "Frame Render Buffer");
		
		gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, 0);
        
    }

	public void prePick(GLAutoDrawable glDrawable) {
		
		/*
		   Bind the FBO, so that all drawing is done to IT, not usual
		   default framebuffer.
		*/
		GL3 gl = glDrawable.getGL().getGL2().getGL3();
        gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, frameBufId);
		reportError(gl, "Binding Frame Buffer");
	}
	
    public void postPick(GLAutoDrawable glDrawable) {
		GL3 gl = glDrawable.getGL().getGL2().getGL3();
		gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, frameBufId);
        //gl.glActiveTexture(textureSymbolicId);
//        gl.glBindTexture(GL3.GL_TEXTURE_2D, colorTextureId_0);
//        reportError("testTextureContents glBindTexture", gl2, colorTextureId_0);
		pixelReadTest(gl, GL3.GL_COLOR_ATTACHMENT0);
		pixelReadTest(gl, GL3.GL_COLOR_ATTACHMENT1);
		pixelReadTest(gl, GL3.GL_DEPTH_ATTACHMENT);
        gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, 0);
		reportError(gl, "Unbind Frame Buffer");
    }

	public void dispose(GLAutoDrawable glDrawable) {
		GL3 gl = glDrawable.getGL().getGL2().getGL3();
		IntBuffer exchange = IntBuffer.allocate(2);

		exchange.rewind();
		exchange.put(colorTextureId_0);
		exchange.put(colorTextureId_1);
		exchange.rewind();
		gl.glDeleteTextures(2, exchange);
		reportError(gl, "Delete Textures");

		exchange.rewind();
		exchange.put(depthBufferId);
		exchange.rewind();
		gl.glDeleteRenderbuffers(1, exchange);
		reportError(gl, "Delete Render Buffers");

	    exchange.rewind();
		exchange.put(frameBufId);
		exchange.rewind();
		gl.glDeleteFramebuffers(1, exchange);
		reportError(gl, "Delete Frame Buffers");
	}
	
	private void prepareTexture(GL3 gl, int texId) {
		// "Bind" the newly created texture : all future texture functions will modify this texture
		gl.glBindTexture(GL3.GL_TEXTURE_2D, texId);
		reportError("Binding Texture ", gl.getGL2(), texId);
		gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_WRAP_S, GL3.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_WRAP_T, GL3.GL_CLAMP_TO_BORDER);
		gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_NEAREST);
		gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_NEAREST);
		reportError(gl, "Texture Characteristics");
		// Give an empty image to OpenGL ( the last "0" )
		gl.glTexImage2D(GL3.GL_TEXTURE_2D, 0, GL3.GL_RGBA, viewportWidth, viewportHeight, 0, GL3.GL_RGBA, GL3.GL_UNSIGNED_BYTE, null);
		reportError("Frame Buffer Texture", gl.getGL2(), texId);
	}

	private void pixelReadTest(GL3 gl, int attachment) {
		int pixelSize = 3 * (Float.SIZE / Byte.SIZE);
		int bufferSize = viewportWidth * viewportHeight * pixelSize;
		byte[] rawBuffer = new byte[bufferSize];
		ByteBuffer buffer = ByteBuffer.wrap(rawBuffer);
		gl.glReadBuffer(attachment);
		gl.glReadPixels(0, 0, viewportWidth, viewportHeight, GL3.GL_RGB, GL3.GL_UNSIGNED_BYTE, buffer);
//		gl.glGetTexImage(GL3.GL_TEXTURE_2D, colorTextureId_0, GL3.GL_RGB, GL3.GL_UNSIGNED_BYTE, buffer);
		byte[] pixel = new byte[3];
		int[] freq = new int[256];
		for (int i = 0; i < rawBuffer.length; i++) {
			int b = rawBuffer[i];
			if (b < 0) {
				b += 256;
			}
			freq[b]++;
		}
		for (int i = 0; i < freq.length; i++) {
			if (freq[i] > 0) {
				System.out.println("Frequency of character " + i + "=" + freq[i]);
			}
		}
		reportError(gl, "Pixel Read Test.");
	}
	
	private String decodeFramebufferStatus( int status ) {
		String rtnVal = null;
		switch (status) {			
			case GL3.GL_FRAMEBUFFER_UNDEFINED:
				rtnVal = "GL_FRAMEBUFFER_UNDEFINED means target is the default framebuffer, but the default framebuffer does not exist.";
				break;
			case GL3.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT :
				rtnVal = "GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT is returned if any of the framebuffer attachment points are framebuffer incomplete.";
				break;
			case GL3.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT:
				rtnVal = "GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT is returned if the framebuffer does not have at least one image attached to it.";
				break;
			case GL3.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER:
				rtnVal = "GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER is returned if the value of GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE\n" +
"		 is GL_NONE for any color attachment point(s) named by GL_DRAW_BUFFERi.";
				break;
			case GL3.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER:
				rtnVal = "GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER is returned if GL_READ_BUFFER is not GL_NONE\n" +
"		 and the value of GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE is GL_NONE for the color attachment point named\n" +
"		 by GL_READ_BUFFER.";
				break;
			case GL3.GL_FRAMEBUFFER_UNSUPPORTED:
				rtnVal = "GL_FRAMEBUFFER_UNSUPPORTED is returned if the combination of internal formats of the attached images violates\n" +
"		 an implementation-dependent set of restrictions.";
				break;
			case GL3.GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE:
				rtnVal = "GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE is returned if the value of GL_RENDERBUFFER_SAMPLES is not the same\n" +
"		 for all attached renderbuffers; if the value of GL_TEXTURE_SAMPLES is the not same for all attached textures; or, if the attached\n" +
"		 images are a mix of renderbuffers and textures, the value of GL_RENDERBUFFER_SAMPLES does not match the value of\n" +
"		 GL_TEXTURE_SAMPLES.  GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE  also returned if the value of GL_TEXTURE_FIXED_SAMPLE_LOCATIONS is\n" +
"		 not the same for all attached textures; or, if the attached images are a mix of renderbuffers and textures, the value of GL_TEXTURE_FIXED_SAMPLE_LOCATIONS\n" +
"		 is not GL_TRUE for all attached textures.";
				break;
			case GL3.GL_FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS:
				rtnVal = " is returned if any framebuffer attachment is layered, and any populated attachment is not layered,\n" +
"		 or if all populated color attachments are not from textures of the same target.";
				break;
			default:
				rtnVal = "--Message not decoded: " + status;
		}
		return rtnVal;
	}
}

/*

*/