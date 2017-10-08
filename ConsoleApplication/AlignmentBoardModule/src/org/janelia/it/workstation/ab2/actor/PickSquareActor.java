package org.janelia.it.workstation.ab2.actor;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.media.opengl.GL4;

import org.janelia.geometry3d.Vector2;
import org.janelia.geometry3d.Vector4;
import org.janelia.it.workstation.ab2.controller.AB2Controller;
import org.janelia.it.workstation.ab2.event.AB2PickSquareColorChangeEvent;
import org.janelia.it.workstation.ab2.gl.GLAbstractActor;
import org.janelia.it.workstation.ab2.gl.GLActorUpdateCallback;
import org.janelia.it.workstation.ab2.gl.GLShaderProgram;
import org.janelia.it.workstation.ab2.renderer.AB23DRenderer;
import org.janelia.it.workstation.ab2.shader.AB2ActorPickShader;
import org.janelia.it.workstation.ab2.shader.AB2ActorShader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PickSquareActor extends GLAbstractActor {

    private final Logger logger = LoggerFactory.getLogger(PickSquareActor.class);

    Vector2 v0;
    Vector2 v1;
    Vector4 color0;
    Vector4 color1;

    IntBuffer vertexArrayId=IntBuffer.allocate(1);
    IntBuffer vertexBufferId=IntBuffer.allocate(1);

    FloatBuffer vertexFb;

    public PickSquareActor(AB23DRenderer renderer, int actorId, Vector2 v0, Vector2 v1, Vector4 color0, Vector4 color1) {
        super(renderer);
        this.actorId=actorId;
        this.v0=v0;
        this.v1=v1;
        this.color0=color0;
        this.color1=color1;
    }

    @Override
    public boolean isTwoDimensional() { return true; }

    @Override
    public void init(GL4 gl, GLShaderProgram shader) {
        if (shader instanceof AB2ActorShader) {

            float[] vertexData = {

                    v0.get(0), v0.get(1), 0f,
                    v1.get(0), v0.get(1), 0f,
                    v0.get(0), v1.get(1), 0f,

                    v1.get(0), v0.get(1), 0f,
                    v1.get(0), v1.get(1), 0f,
                    v0.get(0), v1.get(1), 0f
            };

            vertexFb=createGLFloatBuffer(vertexData);

            gl.glGenVertexArrays(1, vertexArrayId);
            checkGlError(gl, "i1 glGenVertexArrays error");

            gl.glBindVertexArray(vertexArrayId.get(0));
            checkGlError(gl, "i2 glBindVertexArray error");

            gl.glGenBuffers(1, vertexBufferId);
            checkGlError(gl, "i3 glGenBuffers() error");

            gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, vertexBufferId.get(0));
            checkGlError(gl, "i4 glBindBuffer error");

            gl.glBufferData(GL4.GL_ARRAY_BUFFER, vertexFb.capacity() * 4, vertexFb, GL4.GL_STATIC_DRAW);
            checkGlError(gl, "i5 glBufferData error");

            gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0);

        } else if (shader instanceof AB2ActorPickShader) {
            if (pickIndex<0) {
                pickIndex = AB2Controller.getController().getNextPickIndex();
                AB2Controller.getController().setPickEvent(pickIndex, new AB2PickSquareColorChangeEvent(this));
                logger.info("Setting pickIndex="+pickIndex);
            }
        }

    }

    @Override
    public void display(GL4 gl, GLShaderProgram shader) {

        if (shader instanceof AB2ActorShader) {
            AB2ActorShader actorShader=(AB2ActorShader)shader;
            actorShader.setMVP2d(gl, renderer.getVp2d());
            actorShader.setMVP3d(gl, renderer.getVp3d());
            actorShader.setTwoDimensional(gl, true);
            actorShader.setTextureType(gl, AB2ActorShader.TEXTURE_TYPE_NONE);

            Vector4 actorColor=renderer.getColorIdMap().get(actorId);
            if (actorColor!=null) {
                actorShader.setColor0(gl, actorColor);
            }

        } else if (shader instanceof AB2ActorPickShader) {
            AB2ActorPickShader pickShader=(AB2ActorPickShader)shader;
            pickShader.setMVP3d(gl, renderer.getVp3d());
            pickShader.setMVP2d(gl, renderer.getVp3d());
            pickShader.setTwoDimensional(gl, true);
            pickShader.setPickId(gl, getPickIndex());
        }

        gl.glBindVertexArray(vertexArrayId.get(0));
        checkGlError(gl, "d1 glBindVertexArray() error");

        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, vertexBufferId.get(0));
        checkGlError(gl, "d2 glBindBuffer error");

        gl.glVertexAttribPointer(0, 3, GL4.GL_FLOAT, false, 0, 0);
        checkGlError(gl, "d3 glVertexAttribPointer 0 () error");

        gl.glEnableVertexAttribArray(0);
        checkGlError(gl, "d4 glEnableVertexAttribArray 0 () error");

        gl.glDrawArrays(GL4.GL_TRIANGLES, 0, vertexFb.capacity()/2);
        checkGlError(gl, "d7 glDrawArrays() error");

        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0);

    }

    @Override
    public void dispose(GL4 gl, GLShaderProgram shader) {
        if (shader instanceof AB2ActorShader) {
            gl.glDeleteVertexArrays(1, vertexArrayId);
            gl.glDeleteBuffers(1, vertexBufferId);
        }
    }

    public Vector4 getColor0() { return color0; }

    public Vector4 getColor1() { return color1; }

}
