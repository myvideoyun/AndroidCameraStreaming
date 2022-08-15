package com.myvideoyun.livestream.tool.encode;

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
import static com.myvideoyun.livestream.tool.gpu.MVYGPUImageFilter.kAYGPUImagePassthroughFragmentShaderString;
import static com.myvideoyun.livestream.tool.gpu.MVYGPUImageFilter.kAYGPUImageVertexShaderString;

import android.graphics.PointF;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import com.myvideoyun.livestream.tool.gpu.MVYGLProgram;
import com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants;
import com.myvideoyun.livestream.tool.gpu.MVYGPUImageEGLContext;
import com.myvideoyun.livestream.tool.gpu.MVYGPUImageFramebuffer;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * MediaCodec相关代码参考了Google Codec Sample
 * https://android.googlesource.com/platform/cts/+/kitkat-release/tests/tests/media/src/android/media/cts/MediaCodecTest.java
 */
public class MVYMediaCodecEncoder {

    // ----- GLES 相关变量 -----
    private MVYGPUImageEGLContext eglContext;
    private MVYGPUImageEGLContext.Helper eglHelper;

    private int boundingWidth;
    private int boundingHeight;

    private MVYGLProgram filterProgram;
    private MVYGPUImageFramebuffer outputFramebuffer;

    private int filterPositionAttribute, filterTextureCoordinateAttribute;
    private int filterInputTextureUniform;

    private Buffer imageVertices = MVYGPUImageConstants.floatArrayToBuffer(MVYGPUImageConstants.imageVertices);
    private Buffer textureCoordinates = MVYGPUImageConstants.floatArrayToBuffer(MVYGPUImageConstants.noRotationTextureCoordinates);

    private MVYGPUImageConstants.MVYGPUImageContentMode contentMode = MVYGPUImageConstants.MVYGPUImageContentMode.kAYGPUImageScaleAspectFit;

    // ----- MediaCodec 相关变量 -----

    // 编码开始时间
    private long videoStartTime = -1;
    private long audioStartTime = -1;

    // 编码器
    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;
    private static final int TIMEOUT = 1000;

    // 音视频合成器
    private MVYMp4Muxer mp4Muxer;

    // 视频编码完成时用到的锁
    volatile private Boolean isRecordFinish = false;
    private ReadWriteLock recordFinishLock = new ReentrantReadWriteLock(true);

    private MVYMediaCodecEncoderListener mediaCodecEncoderListener;

    // 开始
    volatile private boolean isStart = false;

    private int renderCount;

    public MVYMediaCodecEncoder(String path) {
        // 创建音视频合成器
        mp4Muxer = new MVYMp4Muxer();

        try {
            mp4Muxer.setPath(path);
        } catch (IOException e) {
            Log.e(MVYGPUImageConstants.TAG, "🍇  encoder -> 视频文件保存路径无法访问");
            e.printStackTrace();
        }
    }

    /**
     * 设置窗口缩放方式
     */
    public void setContentMode(MVYGPUImageConstants.MVYGPUImageContentMode contentMode) {
        this.contentMode = contentMode;
    }

    /**
     * 配置和启用视频编码器
     *
     * @return 初始化视频编码器是否成功
     */
    public boolean configureVideoCodec(MVYGPUImageEGLContext eglContext, final int width, final int height, int bitrate, int fps, int iFrameInterval) {
        if (width % 16 != 0 && height % 16 != 0) {
            Log.w(MVYGPUImageConstants.TAG, "🍇  encoder -> width = " + width + " height = " + height + " Compatibility is not good");
        }

        // 配置视频媒体格式
        final MediaFormat format = MediaFormat.createVideoFormat(MVYMediaCodecEncoderHelper.MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);

        // 创建MediaCodec硬编码器
        boolean hadError = false;

        try {
            videoEncoder = MediaCodec.createEncoderByType(MVYMediaCodecEncoderHelper.MIME_TYPE);
            videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (Throwable e) {
            Log.w(MVYGPUImageConstants.TAG, "🍇  encoder -> video mediaCodec create error: " + e);
            hadError = true;
        } finally {
            if (videoEncoder != null && hadError) {
                videoEncoder.stop();
                videoEncoder.release();
                videoEncoder = null;
            }
        }

        if (hadError || videoEncoder == null) {
            return false;
        }

        // 创建视频编码器数据输入用到的EGL和GLES
        boundingWidth = height; // 交换一下, GL绘制比较方便
        boundingHeight = width;

        createGLEnvironment(eglContext);

        videoEncoder.start();

        Log.i(MVYGPUImageConstants.TAG, "🍇  encoder -> video mediaCodec create success");

        // 开启编码线程
        new Thread() {
            @Override
            public void run() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int trackIndex = -1;
                long presentationTimeUs = -1;
                boolean isVideoEncoderReady = false;

                for (; ; ) {
                    recordFinishLock.readLock().lock();

                    if (isRecordFinish) {
                        Log.i(MVYGPUImageConstants.TAG, "🍇  encoder -> 编码器(视频)输出完成");
                        recordFinishLock.readLock().unlock();
                        return;
                    }

                    // 初始化视频编码器成功, 等待写入数据
                    if (isVideoEncoderReady && !isStart) {
                        recordFinishLock.readLock().unlock();
                        SystemClock.sleep(1);
                        continue;
                    }

                    int index = videoEncoder.dequeueOutputBuffer(info, TIMEOUT);

                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat format = videoEncoder.getOutputFormat();
                        Log.i(MVYGPUImageConstants.TAG, "🍇  encoder -> 编码器(视频)初始化完成");

                        isVideoEncoderReady = true;

                        // 添加视频轨道信息到合成器
                        trackIndex = mp4Muxer.addTrack(format);

                        if (mediaCodecEncoderListener != null) {
                            mediaCodecEncoderListener.encoderOutputVideoFormat(format);
                        }

                    } else if (index >= 0) {
                        // 添加视频数据到合成器
                        ByteBuffer byteBuffer;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            byteBuffer = videoEncoder.getOutputBuffer(index);
                        } else {
                            byteBuffer = videoEncoder.getOutputBuffers()[index];
                        }

                        if (info.presentationTimeUs > presentationTimeUs || info.presentationTimeUs == 0) {
                            mp4Muxer.addData(trackIndex, byteBuffer, info);
                            presentationTimeUs = info.presentationTimeUs;
                        }

                        videoEncoder.releaseOutputBuffer(index, false);

                        // 最后一个输出
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.i(MVYGPUImageConstants.TAG, "🍇  encoder -> 编码器(视频)输出完成");
                            recordFinishLock.readLock().unlock();
                            return;
                        }
                    } else {
                        SystemClock.sleep(1);
                    }

                    recordFinishLock.readLock().unlock();
                }
            }
        }.start();

        return true;
    }

    /**
     * 配置和启用音频编码器
     *
     * @return 初始化音频编码器是否成功
     */
    public boolean configureAudioCodec(int bitrate, int sampleRate, int channelCount) {
        final MediaFormat format = MediaFormat.createAudioFormat(MVYMediaCodecEncoderHelper.MIME_TYPE_AUDIO, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, CodecProfileLevel.AACObjectLC); // 最广泛支持的AAC配置
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 10240);

        boolean hadError = false;
        try {
            audioEncoder = MediaCodec.createEncoderByType(MVYMediaCodecEncoderHelper.MIME_TYPE_AUDIO);
            audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (Throwable e) {
            Log.w(MVYGPUImageConstants.TAG, "🍇  encoder -> audio mediaCodec create error: " + e);
            hadError = true;
        } finally {
            if (audioEncoder != null && hadError) {
                audioEncoder.stop();
                audioEncoder.release();
                audioEncoder = null;
            }
        }

        if (hadError) {
            return false;
        }

        audioEncoder.start();

        Log.i(MVYGPUImageConstants.TAG, "🍇  encoder -> audio mediaCodec create success");

        // 开启编码线程
        new Thread() {
            @Override
            public void run() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int trackIndex = -1;
                long presentationTimeUs = -1;
                boolean isAudioEncoderReady = false;

                for (; ; ) {
                    recordFinishLock.readLock().lock();

                    if (isRecordFinish) {
                        Log.i(MVYGPUImageConstants.TAG, "🍇  encoder -> 编码器(音频)输出完成");
                        recordFinishLock.readLock().unlock();
                        return;
                    }

                    // 初始化音频编码器成功, 等待写入数据
                    if (isAudioEncoderReady && !isStart) {
                        recordFinishLock.readLock().unlock();
                        SystemClock.sleep(1);
                        continue;
                    }

                    // 从编码器中取出一个输出buffer
                    int index = audioEncoder.dequeueOutputBuffer(info, TIMEOUT);

                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat format = audioEncoder.getOutputFormat();
                        Log.i(MVYGPUImageConstants.TAG, "🍇  encoder -> 编码器(音频)初始化完成");

                        isAudioEncoderReady = true;

                        // 添加音频轨道信息到合成器, 如果已经提前添加此处返回-1
                        trackIndex = mp4Muxer.addTrack(format);

                        if (mediaCodecEncoderListener != null) {
                            mediaCodecEncoderListener.encoderOutputAudioFormat(format);
                        }
                    } else if (index >= 0) {
                        // 添加视频数据到合成器
                        ByteBuffer byteBuffer;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            byteBuffer = audioEncoder.getOutputBuffer(index);
                        } else {
                            byteBuffer = audioEncoder.getOutputBuffers()[index];
                        }

                        if (info.presentationTimeUs > presentationTimeUs /* || info.presentationTimeUs == 0*/) {
                            mp4Muxer.addData(trackIndex, byteBuffer, info);
                            presentationTimeUs = info.presentationTimeUs;
                        }

                        // 返回一个输出buffer到编码器中
                        audioEncoder.releaseOutputBuffer(index, false);

                        // 最后一个输出
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.i(MVYGPUImageConstants.TAG, "🍇  encoder -> 编码器(音频)输出完成");
                            recordFinishLock.readLock().unlock();
                            return;
                        }
                    } else {
                        SystemClock.sleep(1);
                    }

                    recordFinishLock.readLock().unlock();
                }
            }
        }.start();

        return true;
    }

    public void setMediaCodecEncoderListener(MVYMediaCodecEncoderListener mediaCodecEncoderListener) {
        this.mediaCodecEncoderListener = mediaCodecEncoderListener;
    }

    public void start() {
        mp4Muxer.start();
        isStart = true;
    }

    private void createGLEnvironment(MVYGPUImageEGLContext eglContext) {
        this.eglContext = eglContext;
        eglHelper = new MVYGPUImageEGLContext.Helper();

        eglContext.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {
                eglHelper.generateEGLWindow(videoEncoder.createInputSurface());
                eglContext.makeCurrent(eglHelper.eglDisplay, eglHelper.surface);

                filterProgram = new MVYGLProgram(kAYGPUImageVertexShaderString, kAYGPUImagePassthroughFragmentShaderString);
                filterProgram.link();

                filterPositionAttribute = filterProgram.attributeIndex("position");
                filterTextureCoordinateAttribute = filterProgram.attributeIndex("inputTextureCoordinate");
                filterInputTextureUniform = filterProgram.uniformIndex("inputImageTexture");
            }
        });
    }

    /**
     * 写入视频数据
     */
    public void writeImageTexture(final int texture, final int width, final int height, final long timestamp) {
        // 设置视频写入的时间
        if (videoStartTime == -1) {
            videoStartTime = timestamp;
        }
        final long time = timestamp - videoStartTime;

        // 此函数是在渲染线程, 不能中断
        if (!recordFinishLock.readLock().tryLock()) {
            return;
        }

        if (isRecordFinish) {
            recordFinishLock.readLock().unlock();
            return;
        }

        eglContext.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {
                eglContext.makeCurrent(eglHelper.eglDisplay, eglHelper.surface);

                eglContext.setTimestamp(eglHelper.eglDisplay, eglHelper.surface, time);

                filterProgram.use();

                if (outputFramebuffer != null) {
                    if (boundingWidth != outputFramebuffer.width || boundingHeight != outputFramebuffer.height) {
                        outputFramebuffer.destroy();
                        outputFramebuffer = null;
                    }
                }

                if (outputFramebuffer == null) {
                    outputFramebuffer = new MVYGPUImageFramebuffer(boundingWidth, boundingHeight);
                }

                outputFramebuffer.activateFramebuffer();

                glViewport(0, 0, boundingWidth, boundingHeight);

                glClearColor(0, 0, 0, 0);
                glClear(GL_COLOR_BUFFER_BIT);

                glActiveTexture(GL_TEXTURE2);
                glBindTexture(GL_TEXTURE_2D, texture);

                glUniform1i(filterInputTextureUniform, 2);

                PointF insetSize = MVYGPUImageConstants.getAspectRatioInsideSize(new PointF(width, height), new PointF(boundingWidth, boundingHeight));

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

                GLES20.glVertexAttribPointer(filterPositionAttribute, 2, GL_FLOAT, false, 0, MVYGPUImageConstants.floatArrayToBuffer(squareVertices));
                glVertexAttribPointer(filterTextureCoordinateAttribute, 2, GL_FLOAT, false, 0, textureCoordinates);

                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

                glBindFramebuffer(GL_FRAMEBUFFER, 0);

                glViewport(0, 0, boundingHeight, boundingWidth);

                glClearColor(0, 0, 0, 0);
                glClear(GL_COLOR_BUFFER_BIT);

                glActiveTexture(GL_TEXTURE2);
                glBindTexture(GL_TEXTURE_2D, outputFramebuffer.texture[0]);

                glUniform1i(filterInputTextureUniform, 2);

                Buffer textureCoordinates = MVYGPUImageConstants.floatArrayToBuffer(MVYGPUImageConstants.rotateRightTextureCoordinates);

                glVertexAttribPointer(filterPositionAttribute, 2, GL_FLOAT, false, 0, imageVertices);
                glVertexAttribPointer(filterTextureCoordinateAttribute, 2, GL_FLOAT, false, 0, textureCoordinates);

                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

                glDisableVertexAttribArray(filterPositionAttribute);
                glDisableVertexAttribArray(filterTextureCoordinateAttribute);

                eglContext.swapBuffers(eglHelper.eglDisplay, eglHelper.surface);

                renderCount++;
            }
        });

        recordFinishLock.readLock().unlock();
    }

    /**
     * 写入音频数据
     */
    public void writePCMByteBuffer(ByteBuffer source, final long timestamp) {
        // 设置音频写入的时间
        if (audioStartTime == -1) {
            audioStartTime = timestamp;
        }
        final long time = timestamp - audioStartTime;

        recordFinishLock.readLock().lock();

        if (isRecordFinish) {
            recordFinishLock.readLock().unlock();
            return;
        }

        short[] shorts = new short[source.limit() / 2];
        source.position(0);
        source.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);

        // 编码
        int inputIndex = audioEncoder.dequeueInputBuffer(TIMEOUT);
        while (inputIndex == -1) {
            inputIndex = audioEncoder.dequeueInputBuffer(TIMEOUT);
        }

        ByteBuffer inputBuffer;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            inputBuffer = audioEncoder.getInputBuffer(inputIndex);
        } else {
            inputBuffer = audioEncoder.getInputBuffers()[inputIndex];
        }

        inputBuffer.clear();
        inputBuffer.limit(source.limit());
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts);

        // The presentation timestamp in microseconds
        audioEncoder.queueInputBuffer(inputIndex, 0, inputBuffer.limit(), time / 1000, 0);

        recordFinishLock.readLock().unlock();
    }

    /**
     * 完成音视频录制
     */
    public void finish() {
        // 等待MediaCodec读锁释放
        recordFinishLock.writeLock().lock();
        isRecordFinish = true;
        recordFinishLock.writeLock().unlock();

        releaseVideoEncoder();

        releaseAudioEncoder();

        // 等待合成器结束
        if (mp4Muxer != null) {
            mp4Muxer.finish();
            mp4Muxer = null;
        }
    }

    private void releaseAudioEncoder() {
        if (audioEncoder != null) {
            audioEncoder.stop();
            audioEncoder.release();
            audioEncoder = null;
        }
    }

    private void releaseVideoEncoder() {
        // 释放MediaCodec
        if (videoEncoder != null) {
            videoEncoder.stop();
            videoEncoder.release();
            videoEncoder = null;

            Log.i(MVYGPUImageConstants.TAG, "🍇  encoder -> 释放编码器 总共编码视频帧: " + renderCount);
            renderCount = 0;
        }

        // 释放GLES
        if (eglContext != null) {
            destroyGLEnvironment();
            eglContext = null;
        }
    }

    private void destroyGLEnvironment() {
        eglContext.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {
                eglContext.makeCurrent();

                if (filterProgram != null) {
                    filterProgram.destroy();
                }

                if (outputFramebuffer != null) {
                    outputFramebuffer.destroy();
                }

                if (eglHelper != null) {
                    eglHelper.destroyEGLWindow();
                    eglHelper = null;
                }
            }
        });
    }
}
