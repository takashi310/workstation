package org.janelia.it.workstation.ab2.actor;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import javax.media.opengl.GL4;

import org.janelia.geometry3d.Vector3;
import org.janelia.geometry3d.Vector4;
import org.janelia.it.workstation.ab2.gl.GLAbstractActor;
import org.janelia.it.workstation.ab2.gl.GLShaderProgram;
import org.janelia.it.workstation.ab2.renderer.AB23DRenderer;
import org.janelia.it.workstation.ab2.shader.AB2ActorShader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LineSetActor extends GLAbstractActor {

    private final Logger logger = LoggerFactory.getLogger(LineSetActor.class);

    List<Vector3> vertices;

    IntBuffer vertexArrayId=IntBuffer.allocate(1);
    IntBuffer vertexBufferId=IntBuffer.allocate(1);

    FloatBuffer lineVertexFb;

    public LineSetActor(AB23DRenderer renderer, int actorId, List<Vector3> vertices) {
        super(renderer);
        this.renderer=renderer;
        this.actorId=actorId;
        this.vertices=vertices;
    }

    @Override
    public void init(GL4 gl, GLShaderProgram shader) {

        float[] lineData=new float[vertices.size()*3];

        for (int i=0;i<vertices.size();i++) {
            Vector3 v=vertices.get(i);
            lineData[i*3]=v.getX();
            lineData[i*3+1]=v.getY();
            lineData[i*3+2]=v.getZ();
        }

        lineVertexFb= GLAbstractActor.createGLFloatBuffer(lineData);

        gl.glGenVertexArrays(1, vertexArrayId);
        checkGlError(gl, "i1 glGenVertexArrays error");

        gl.glBindVertexArray(vertexArrayId.get(0));
        checkGlError(gl, "i2 glBindVertexArray error");

        gl.glGenBuffers(1, vertexBufferId);
        checkGlError(gl, "i3 glGenBuffers() error");

        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, vertexBufferId.get(0));
        checkGlError(gl, "i4 glBindBuffer error");

        gl.glBufferData(GL4.GL_ARRAY_BUFFER, lineVertexFb.capacity() * 4, lineVertexFb, GL4.GL_STATIC_DRAW);
        checkGlError(gl, "i5 glBufferData error");

        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0);

    }

    @Override
    public void display(GL4 gl, GLShaderProgram shader) {

        if (shader instanceof AB2ActorShader) {
            AB2ActorShader actorShader=(AB2ActorShader)shader;
            actorShader.setMVP3d(gl, renderer.getVp3d());
            actorShader.setMVP2d(gl, renderer.getVp2d());
            actorShader.setTwoDimensional(gl, false);
            actorShader.setTextureType(gl, AB2ActorShader.TEXTURE_TYPE_NONE);

            Vector4 actorColor=renderer.getColorIdMap().get(actorId);
            if (actorColor!=null) {
                actorShader.setColor0(gl, actorColor);
            }

        }

        gl.glBindVertexArray(vertexArrayId.get(0));
        checkGlError(gl, "d1 glBindVertexArray() error");

        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, vertexBufferId.get(0));
        checkGlError(gl, "d2 glBindBuffer error");

        gl.glVertexAttribPointer(0, 3, GL4.GL_FLOAT, false, 0, 0);
        checkGlError(gl, "d3 glVertexAttribPointer 0 () error");

        gl.glEnableVertexAttribArray(0);
        checkGlError(gl, "d4 glEnableVertexAttribArray 0 () error");

        gl.glDrawArrays(GL4.GL_LINES, 0, lineVertexFb.capacity()/3);
        checkGlError(gl, "d7 glDrawArrays() error");

        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0);

    }

    @Override
    public void dispose(GL4 gl, GLShaderProgram shader) {
        gl.glDeleteVertexArrays(1, vertexArrayId);
        gl.glDeleteBuffers(1, vertexBufferId);
    }

}
