package com.myvideoyun.livestream.tool.camera;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_TEXTURE2;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glDisableVertexAttribArray;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glVertexAttribPointer;
import static android.opengl.GLES20.glViewport;
import static com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants.MVYGPUImageContentMode.kAYGPUImageScaleAspectFit;
import static com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants.getAspectRatioInsideSize;

import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.myvideoyun.livestream.tool.gpu.MVYGLProgram;
import com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants;
import com.myvideoyun.livestream.tool.gpu.MVYGPUImageEGLContext;
import com.myvideoyun.livestream.tool.gpu.MVYGPUImageFilter;

import java.nio.Buffer;

public class MVYPreviewView extends SurfaceView implements SurfaceHolder.Callback {

    private MVYPreviewViewListener listener;

    public MVYGPUImageEGLContext eglContext;

    private int boundingWidth;
    private int boundingHeight;

    private MVYGLProgram filterProgram;

    private int filterPositionAttribute, filterTextureCoordinateAttribute;
    private int filterInputTextureUniform;

    private Buffer textureCoordinates = MVYGPUImageConstants.floatArrayToBuffer(MVYGPUImageConstants.noRotationTextureCoordinates);

    private MVYGPUImageConstants.MVYGPUImageContentMode contentMode = kAYGPUImageScaleAspectFit;

    public MVYPreviewView(Context context) {
        super(context);
        commonInit();
    }

    public MVYPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        commonInit();
    }

    public MVYPreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        commonInit();
    }

    private void commonInit() {
        getHolder().addCallback(this);
    }

    /**
     * 设置窗口缩放方式
     */
    public void setContentMode(MVYGPUImageConstants.MVYGPUImageContentMode contentMode) {
        this.contentMode = contentMode;
    }

    /**
     * 渲染纹理图像到surface上
     */
    public void render(final int texture, final int width, final int height) {
        if (eglContext == null) {
            return;
        }

        eglContext.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {
                eglContext.makeCurrent();

                filterProgram.use();

                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glViewport(0, 0, boundingWidth, boundingHeight);

                glClearColor(0, 0, 0, 0);
                glClear(GL_COLOR_BUFFER_BIT);

                glActiveTexture(GL_TEXTURE2);
                glBindTexture(GL_TEXTURE_2D, texture);

                glUniform1i(filterInputTextureUniform, 2);

                PointF insetSize = getAspectRatioInsideSize(new PointF(width, height), new PointF(boundingWidth, boundingHeight));

                float widthScaling = 0.0f, heightScaling = 0.0f;

                switch (contentMode) {
                    case kAYGPUImageScaleToFill:
                        widthScaling = 1.0f;
                        heightScaling = 1.0f;
                        break;
                    case kAYGPUImageScaleAspectFit:
                        widthScaling = insetSize.x / boundingWidth;
                        heightScaling = insetSize.y / boundingHeight;
                        break;
                    case kAYGPUImageScaleAspectFill:
                        widthScaling = boundingHeight / insetSize.y;
                        heightScaling = boundingWidth / insetSize.x;
                        break;
                }

                float squareVertices[] = new float[8];
                squareVertices[0] = -widthScaling;
                squareVertices[1] = -heightScaling;
                squareVertices[2] = widthScaling;
                squareVertices[3] = -heightScaling;
                squareVertices[4] = -widthScaling;
                squareVertices[5] = heightScaling;
                squareVertices[6] = widthScaling;
                squareVertices[7] = heightScaling;

                glEnableVertexAttribArray(filterPositionAttribute);
                glEnableVertexAttribArray(filterTextureCoordinateAttribute);

                Buffer imageVertices = MVYGPUImageConstants.floatArrayToBuffer(squareVertices);

                glVertexAttribPointer(filterPositionAttribute, 2, GL_FLOAT, false, 0, imageVertices);
                glVertexAttribPointer(filterTextureCoordinateAttribute, 2, GL_FLOAT, false, 0, textureCoordinates);

                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

                glDisableVertexAttribArray(filterPositionAttribute);
                glDisableVertexAttribArray(filterTextureCoordinateAttribute);

                eglContext.swapBuffers();
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        createGLEnvironment(holder);
        if (listener != null) {
            listener.createGLEnvironment();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        this.boundingWidth = width;
        this.boundingHeight = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (listener != null) {
            listener.destroyGLEnvironment();
        }
        destroyGLEnvironment();
    }

    public void setListener(MVYPreviewViewListener listener) {
        this.listener = listener;
    }

    /**
     * 创建 GLES 环境
     */
    private void createGLEnvironment(Object object) {
        eglContext = new MVYGPUImageEGLContext();
        eglContext.initWithEGLWindow(object);

        eglContext.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {

                filterProgram = new MVYGLProgram(MVYGPUImageFilter.kAYGPUImageVertexShaderString, MVYGPUImageFilter.kAYGPUImagePassthroughFragmentShaderString);
                filterProgram.link();

                filterPositionAttribute = filterProgram.attributeIndex("position");
                filterTextureCoordinateAttribute = filterProgram.attributeIndex("inputTextureCoordinate");
                filterInputTextureUniform = filterProgram.uniformIndex("inputImageTexture");
                filterProgram.use();
            }
        });
    }

    /**
     * 销毁 GLES 环境
     */
    private void destroyGLEnvironment() {
        eglContext.syncRunOnRenderThread(() -> {
            eglContext.makeCurrent();
            filterProgram.destroy();

            eglContext.destroyEGLWindow();
            eglContext = null;
        });
    }
}
