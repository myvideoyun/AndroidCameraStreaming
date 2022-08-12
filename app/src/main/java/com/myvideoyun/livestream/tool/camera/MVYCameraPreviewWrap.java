package com.myvideoyun.livestream.tool.camera;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_TEXTURE2;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glDisableVertexAttribArray;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glFinish;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glTexParameterf;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glVertexAttribPointer;
import static com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants.MVYGPUImageRotationMode.kAYGPUImageNoRotation;
import static com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants.needExchangeWidthAndHeightWithRotation;
import static com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants.textureCoordinatesForRotation;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;

import com.myvideoyun.livestream.tool.gpu.MVYGLProgram;
import com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants;
import com.myvideoyun.livestream.tool.gpu.MVYGPUImageEGLContext;
import com.myvideoyun.livestream.tool.gpu.MVYGPUImageFilter;
import com.myvideoyun.livestream.tool.gpu.MVYGPUImageFramebuffer;

import java.io.IOException;
import java.nio.Buffer;

public class MVYCameraPreviewWrap implements SurfaceTexture.OnFrameAvailableListener {

    private static final String kAYOESTextureFragmentShader = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "\n" +
            "uniform samplerExternalOES inputImageTexture;\n" +
            "\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "}";

    private Camera mCamera;

    private MVYGPUImageEGLContext eglContext;

    private SurfaceTexture surfaceTexture;

    private int oesTexture;

    private MVYCameraPreviewListener previewListener;

    private MVYGPUImageFramebuffer outputFramebuffer;

    private MVYGPUImageConstants.MVYGPUImageRotationMode rotateMode = kAYGPUImageNoRotation;

    private int inputWidth;
    private int inputHeight;

    private MVYGLProgram filterProgram;

    private int filterPositionAttribute, filterTextureCoordinateAttribute;
    private int filterInputTextureUniform;

    private Buffer imageVertices = MVYGPUImageConstants.floatArrayToBuffer(MVYGPUImageConstants.imageVertices);

    public MVYCameraPreviewWrap(Camera camera) {
        mCamera = camera;
    }

    public void startPreview(MVYGPUImageEGLContext eglContext) {
        this.eglContext = eglContext;
        createGLEnvironment();

        try {
            mCamera.setPreviewTexture(surfaceTexture);
        } catch (IOException ignored) {
        }

        Camera.Size s = mCamera.getParameters().getPreviewSize();
        inputWidth = s.width;
        inputHeight = s.height;

        setRotateMode(rotateMode);

        mCamera.startPreview();
   }

   public void stopPreview() {
       destroyGLContext();
       mCamera.stopPreview();
   }

    public void setPreviewListener(MVYCameraPreviewListener previewListener) {
        this.previewListener = previewListener;
    }

    public void setRotateMode(MVYGPUImageConstants.MVYGPUImageRotationMode rotateMode) {
        this.rotateMode = rotateMode;

        if (needExchangeWidthAndHeightWithRotation(rotateMode)) {
            int temp = inputWidth;
            inputWidth = inputHeight;
            inputHeight = temp;
        }
    }

    private void createGLEnvironment() {
        eglContext.syncRunOnRenderThread(() -> {
            eglContext.makeCurrent();

            oesTexture = createOESTextureID();
            surfaceTexture = new SurfaceTexture(oesTexture);
            surfaceTexture.setOnFrameAvailableListener(MVYCameraPreviewWrap.this);

            filterProgram = new MVYGLProgram(MVYGPUImageFilter.kAYGPUImageVertexShaderString, kAYOESTextureFragmentShader);
            filterProgram.link();

            filterPositionAttribute = filterProgram.attributeIndex("position");
            filterTextureCoordinateAttribute = filterProgram.attributeIndex("inputTextureCoordinate");
            filterInputTextureUniform = filterProgram.uniformIndex("inputImageTexture");
        });
    }

    @Override
    public void onFrameAvailable(final SurfaceTexture surfaceTexture) {
        eglContext.syncRunOnRenderThread(() -> {
            eglContext.makeCurrent();

            if (EGL14.eglGetCurrentDisplay() != EGL14.EGL_NO_DISPLAY) {
                surfaceTexture.updateTexImage();

                glFinish();

                // 因为在shader中处理oes纹理需要使用到扩展类型, 必须要先转换为普通纹理再传给下一级
                renderToFramebuffer(oesTexture);

                if (previewListener != null) {
                    previewListener.cameraVideoOutput(outputFramebuffer.texture[0], inputWidth, inputHeight, surfaceTexture.getTimestamp());
                }
            }
        });
    }

    private void renderToFramebuffer(int oesTexture) {

        filterProgram.use();

        if (outputFramebuffer != null) {
            if (inputWidth != outputFramebuffer.width || inputHeight != outputFramebuffer.height) {
                outputFramebuffer.destroy();
                outputFramebuffer = null;
            }
        }

        if (outputFramebuffer == null) {
            outputFramebuffer = new MVYGPUImageFramebuffer(inputWidth, inputHeight);
        }

        outputFramebuffer.activateFramebuffer();

        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, oesTexture);

        glUniform1i(filterInputTextureUniform, 2);

        glEnableVertexAttribArray(filterPositionAttribute);
        glEnableVertexAttribArray(filterTextureCoordinateAttribute);

        glVertexAttribPointer(filterPositionAttribute, 2, GL_FLOAT, false, 0, imageVertices);
        glVertexAttribPointer(filterTextureCoordinateAttribute, 2, GL_FLOAT, false, 0, MVYGPUImageConstants.floatArrayToBuffer(textureCoordinatesForRotation(rotateMode)));

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

        glDisableVertexAttribArray(filterPositionAttribute);
        glDisableVertexAttribArray(filterTextureCoordinateAttribute);
    }

    private int createOESTextureID() {
        int[] texture = new int[1];
        glGenTextures(1, texture, 0);

        glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture[0]);
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        return texture[0];
    }

    private void destroyGLContext() {
        eglContext.syncRunOnRenderThread(() -> {

            filterProgram.destroy();

            if (outputFramebuffer != null) {
                outputFramebuffer.destroy();
            }
        });
    }
}

