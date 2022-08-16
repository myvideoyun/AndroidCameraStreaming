package com.myvideoyun.livestream.tool;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.myvideoyun.livestream.R;
import com.pedro.encoder.input.gl.Sprite;
import com.pedro.encoder.input.gl.TextureLoader;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.StreamObjectBase;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GreenScreenRender extends BaseFilterRender {

    //rotation matrix
    private final float[] squareVertexDataFilter = {
            // X, Y, Z, U, V
            -1f, -1f, 0f, 0f, 0f, //bottom left
            1f, -1f, 0f, 1f, 0f, //bottom right
            -1f, 1f, 0f, 0f, 1f, //top left
            1f, 1f, 0f, 1f, 1f, //top right
    };

    private int program = -1;
    private int aPositionHandle = -1;
    private int aTextureHandle = -1;
    private int aTextureObjectHandle = -1;
    private int uMVPMatrixHandle = -1;
    private int uObjectMatrixHandle = -1;
    private int uSamplerHandle = -1;
    private int uObjectHandle = -1;

    private FloatBuffer squareVertexObject;

    protected int[] streamObjectTextureId = new int[]{-1};
    protected TextureLoader textureLoader = new TextureLoader();
    protected StreamObjectBase streamObject;
    protected boolean shouldLoad = false;

    public GreenScreenRender() {
        squareVertex = ByteBuffer.allocateDirect(squareVertexDataFilter.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        squareVertex.put(squareVertexDataFilter).position(0);

        float[] vertices = new Sprite().getTransformedVertices();
        squareVertexObject = ByteBuffer.allocateDirect(vertices.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        squareVertexObject.put(vertices).position(0);

        Matrix.setIdentityM(MVPMatrix, 0);
        Matrix.setIdentityM(STMatrix, 0);

        streamObject = new ImageStreamObject();
    }

    @Override
    protected void initGlFilter(Context context) {
        String vertexShader = GlUtil.getStringFromRaw(context, R.raw.green_screen_vertex);
        String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.green_screen_fragment);

        program = GlUtil.createProgram(vertexShader, fragmentShader);
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
        aTextureObjectHandle = GLES20.glGetAttribLocation(program, "aTextureObjectCoord");
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        uObjectMatrixHandle = GLES20.glGetUniformLocation(program, "uObjectMatrix");
        uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler");
        uObjectHandle = GLES20.glGetUniformLocation(program, "uObject");
    }

    @Override
    protected void drawFilter() {
        if (shouldLoad) {
            releaseTexture();
            streamObjectTextureId = textureLoader.load(streamObject.getBitmaps());
            shouldLoad = false;
        }

        GLES20.glUseProgram(program);

        squareVertex.position(SQUARE_VERTEX_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
                SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
        GLES20.glEnableVertexAttribArray(aPositionHandle);

        squareVertex.position(SQUARE_VERTEX_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false,
                SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
        GLES20.glEnableVertexAttribArray(aTextureHandle);

        squareVertexObject.position(SQUARE_VERTEX_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(aTextureObjectHandle, 2, GLES20.GL_FLOAT, false,
                2 * FLOAT_SIZE_BYTES, squareVertexObject);
        GLES20.glEnableVertexAttribArray(aTextureObjectHandle);

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0);
        GLES20.glUniformMatrix4fv(uObjectMatrixHandle, 1, false, STMatrix, 0);

        //Sampler
        GLES20.glUniform1i(uSamplerHandle, 4);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId);

        //Object
        GLES20.glUniform1i(uObjectHandle, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, streamObjectTextureId[0]);
    }

    @Override
    public void release() {
        GLES20.glDeleteProgram(program);
        releaseTexture();
    }

    private void releaseTexture() {
        GLES20.glDeleteTextures(streamObjectTextureId.length, streamObjectTextureId, 0);
        streamObjectTextureId = new int[]{-1};
    }

    public void setImage(Bitmap bitmap) {
        ((ImageStreamObject) streamObject).load(bitmap);
        shouldLoad = true;
    }
}
