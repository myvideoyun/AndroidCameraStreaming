package com.myvideoyun.livestream;

import static com.myvideoyun.livestream.tool.encode.MVYMediaCodecEncoderHelper.getAvcSupportedFormatInfo;
import static com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants.MVYGPUImageContentMode.kAYGPUImageScaleAspectFill;
import static com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants.MVYGPUImageContentMode.kAYGPUImageScaleAspectFit;
import static com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants.MVYGPUImageRotationMode.kAYGPUImageRotateRight;
import static com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants.MVYGPUImageRotationMode.kAYGPUImageRotateRightFlipHorizontal;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.myvideoyun.livestream.plug.EffectHandler;
import com.myvideoyun.livestream.tool.camera.MVYCameraPreviewListener;
import com.myvideoyun.livestream.tool.camera.MVYCameraPreviewWrap;
import com.myvideoyun.livestream.tool.camera.MVYPreviewView;
import com.myvideoyun.livestream.tool.camera.MVYPreviewViewListener;
import com.myvideoyun.livestream.tool.encode.MVYAudioRecorderListener;
import com.myvideoyun.livestream.tool.encode.MVYAudioRecorderWrap;
import com.myvideoyun.livestream.tool.encode.MVYMediaCodecEncoder;
import com.myvideoyun.livestream.tool.encode.MVYMediaCodecEncoderHelper.CodecInfo;
import com.myvideoyun.livestream.tool.encode.MVYMediaCodecEncoderListener;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.UUID;

public class CameraActivity extends AppCompatActivity implements MVYCameraPreviewListener, MVYAudioRecorderListener, MVYPreviewViewListener, MVYMediaCodecEncoderListener {

    private static final String TAG = "CameraActivity";

    // 相机
    Camera camera;
    MVYCameraPreviewWrap cameraPreviewWrap;
    public static final int FRONT_CAMERA_ID = 1;
    public static final int BACK_CAMERA_ID = 0;
    int mCurrentCameraID = FRONT_CAMERA_ID;

    // 麦克风
    AudioRecord audioRecord;
    MVYAudioRecorderWrap audioRecordWrap;

    // 预览的surface
    MVYPreviewView surfaceView;

    // 音视频硬编码
    volatile MVYMediaCodecEncoder encoder;
    volatile boolean videoCodecConfigResult = false;
    volatile boolean audioCodecConfigResult = false;
    volatile boolean isCodecInit = false;

    // 视频保存路径
    String videoPath;

    // 特效
    EffectHandler effectHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);

        surfaceView = findViewById(R.id.recorder_preview);
        surfaceView.setContentMode(kAYGPUImageScaleAspectFit);
        surfaceView.setListener(this);

        ToggleButton recorderToggle = findViewById(R.id.recorder_toggle);
        recorderToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startRecord();
            } else {
                stopRecord();
            }
        });

        findViewById(R.id.switch_camera).setOnClickListener(v -> {
            if (encoder == null) {
                switchCamera();
            }
        });
    }

    @Override
    public void createGLEnvironment() {
        openHardware();

        surfaceView.eglContext.syncRunOnRenderThread(this::createEffectHandler);
    }

    @Override
    public void destroyGLEnvironment() {
        closeHardware();

        surfaceView.eglContext.syncRunOnRenderThread(this::destroyEffectHandler);
    }

    /**
     * 打开硬件设备
     */
    private void openHardware() {
        // 打开前置相机
        openFrontCamera();

        // 打开后置相机
//        openBackCamera();

        // 打开麦克风
        openAudioRecorder();
    }

    /**
     * 打开前置相机
     */
    private void openFrontCamera() {
        if (cameraPreviewWrap != null) {
            cameraPreviewWrap.stopPreview();
        }
        if (camera != null) {
            camera.release();
        }

        Log.d(TAG, "打开前置相机");
        mCurrentCameraID = FRONT_CAMERA_ID;
        camera = Camera.open(mCurrentCameraID); // TODO 省略判断是否有前置相机
        setCameraDisplayOrientation(this, camera);

        cameraPreviewWrap = new MVYCameraPreviewWrap(camera);
        cameraPreviewWrap.setPreviewListener(this);

        cameraPreviewWrap.setRotateMode(kAYGPUImageRotateRight); // TODO 如果画面方向不对, 修改此值
        cameraPreviewWrap.startPreview(surfaceView.eglContext);
    }

    /**
     * 打开后置相机
     */
    private void openBackCamera() {
        if (cameraPreviewWrap != null) {
            cameraPreviewWrap.stopPreview();
        }
        if (camera != null) {
            camera.release();
        }

        Log.d(TAG, "打开后置相机");
        mCurrentCameraID = BACK_CAMERA_ID;
        camera = Camera.open(mCurrentCameraID);
        setCameraDisplayOrientation(this, camera);

        cameraPreviewWrap = new MVYCameraPreviewWrap(camera);
        cameraPreviewWrap.setPreviewListener(this);

        cameraPreviewWrap.setRotateMode(kAYGPUImageRotateRightFlipHorizontal); // TODO 如果画面方向不对, 修改此值
        cameraPreviewWrap.startPreview(surfaceView.eglContext);
    }

    /**
     * 打开麦克风
     */
    private void openAudioRecorder() {
        Log.d(TAG, "打开麦克风");

        // 音频编码固定参数
        final int audioSampleRate = 16000;   //音频采样率
        final int audioChannel = AudioFormat.CHANNEL_IN_MONO;   //单声道
        final int audioFormat = AudioFormat.ENCODING_PCM_16BIT; //音频录制格式

        int bufferSize = AudioRecord.getMinBufferSize(audioSampleRate, audioChannel, audioFormat);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, audioSampleRate, audioChannel,
                audioFormat, bufferSize);
        if (audioRecordWrap == null) {
            audioRecordWrap = new MVYAudioRecorderWrap(audioRecord, bufferSize);
            audioRecordWrap.setAudioRecorderListener(this);
        }
        audioRecordWrap.startRecording();
    }

    /**
     * 关闭硬件设备
     */
    private void closeHardware() {
        // 关闭相机
        if (camera != null) {
            Log.d(TAG, "关闭相机");
            cameraPreviewWrap.stopPreview();
            cameraPreviewWrap = null;
            camera.release();
            camera = null;
        }

        // 关闭麦克风
        if (audioRecord != null) {
            Log.d(TAG, "关闭麦克风");
            audioRecordWrap.stop();
            audioRecordWrap = null;
            audioRecord.release();
            audioRecord = null;
        }

        // 如果正在录像, 停止录像
        closeMediaCodec();
    }

    private void createEffectHandler() {
        effectHandler = new EffectHandler(this);
    }

    private void processTexture(int texture, int width, int height) {
        effectHandler.processTexture(texture, width, height);
    }

    private void destroyEffectHandler() {
        if (effectHandler != null) {
            effectHandler.destroy();
            effectHandler = null;
        }
    }

    /**
     * 相机数据回调
     * ⚠️ 注意: 函数运行在视频处理线程
     * This timestamp is in nanoseconds
     */
    @Override
    public void cameraVideoOutput(int texture, int width, int height, long timestamp) {
        processTexture(texture, width, height);

        // 渲染到surfaceView
        surfaceView.render(texture, width, height);

        // 进行视频编码
        if (encoder != null && isCodecInit) {
            encoder.writeImageTexture(texture, width, height, timestamp);
        }
    }

    /**
     * 麦克风数据回调
     * ⚠️ 注意: 函数运行在音频处理线程
     * This timestamp is in nanoseconds
     */
    @Override
    public void audioRecorderOutput(ByteBuffer byteBuffer, long timestamp) {

        // 进行音频编码
        if (encoder != null && isCodecInit) {
            encoder.writePCMByteBuffer(byteBuffer, timestamp);
        }
    }

    public void startRecord() {
        // 设置视频路径
        if (getExternalCacheDir() != null) {
            videoPath = getExternalCacheDir().getAbsolutePath();
        } else {
            videoPath = getCacheDir().getAbsolutePath();
        }
        videoPath = videoPath + File.separator + UUID.randomUUID().toString().replace("-", "") + ".mp4";

        if (!startMediaCodec()) {
            stopRecord();
            ToggleButton toggleButton = findViewById(R.id.recorder_toggle);
            toggleButton.setChecked(false);
        }
    }

    public void stopRecord() {
        if (closeMediaCodec() && new File(videoPath).exists()) {
            showVideo();
        }
    }

    public void showVideo() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri contentUri = FileProvider.getUriForFile(getBaseContext(), "com.myvideoyun.livestream.fileprovider", new File(videoPath));
            intent.setDataAndType(contentUri, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void switchCamera() {
        if (mCurrentCameraID == FRONT_CAMERA_ID) {
            openBackCamera();
        } else if (mCurrentCameraID == BACK_CAMERA_ID) {
            openFrontCamera();
        }
    }

    /**
     * 启动编码器
     */
    private boolean startMediaCodec() {

        // 图像编码参数
        int width = 1280; // 视频编码时图像旋转了90度
        int height = 720;
        int bitRate = 1000000; // 码率: 1Mbps
        int fps = 30; // 帧率: 30
        int iFrameInterval = 1; // GOP: 30

        // 音频编码参数
        int audioBitRate = 128000; // 码率: 128kbps
        int sampleRate = 16000; // 采样率

        // 编码器信息
        CodecInfo codecInfo = getAvcSupportedFormatInfo();
        if (codecInfo == null) {
            Log.d(TAG, "不支持硬编码");
            return false;
        }

        // 设置给编码器的参数不能超过其最大值
        if (width > codecInfo.maxWidth) {
            width = codecInfo.maxWidth;
        }
        if (height > codecInfo.maxHeight) {
            height = codecInfo.maxHeight;
        }
        if (bitRate > codecInfo.bitRate) {
            bitRate = codecInfo.bitRate;
        }
        if (fps > codecInfo.fps) {
            fps = codecInfo.fps;
        }

        Log.d(TAG, "开始编码，初始化参数；" + "width = " + width + "height = " + height + "bitRate = " + bitRate
                + "fps = " + fps + "IFrameInterval = " + iFrameInterval);

        // 启动编码
        encoder = new MVYMediaCodecEncoder(videoPath);
        encoder.setContentMode(kAYGPUImageScaleAspectFill);
        encoder.setMediaCodecEncoderListener(this);
        boolean videoCodecInitResult = encoder.configureVideoCodec(surfaceView.eglContext, width, height, bitRate, fps, iFrameInterval);
        boolean audioCodecInitResult = encoder.configureAudioCodec(audioBitRate, sampleRate, 1);

        isCodecInit = videoCodecInitResult && audioCodecInitResult;
        return isCodecInit;
    }

    /**
     * 关闭编码器
     */
    private boolean closeMediaCodec() {
        // 关闭编码
        if (encoder != null) {
            Log.d(TAG, "关闭编码器");
            encoder.finish();
            encoder = null;
        }

        boolean recordSuccess = videoCodecConfigResult && audioCodecConfigResult;

        // 关闭编码器标志
        videoCodecConfigResult = false;
        audioCodecConfigResult = false;
        isCodecInit = false;

        return recordSuccess;
    }

    /**
     * 视频编码器准备完成 回调
     * ⚠️ 注意: 函数运行在视频编码线程
     */
    @Override
    public void encoderOutputVideoFormat(MediaFormat format) {
        videoCodecConfigResult = true;
        if (videoCodecConfigResult && audioCodecConfigResult) {
            encoder.start();
        }
    }

    /**
     * 音频编码器准备完成 回调
     * ⚠️ 注意: 函数运行在音频编码线程
     */
    @Override
    public void encoderOutputAudioFormat(MediaFormat format) {
        audioCodecConfigResult = true;
        if (videoCodecConfigResult && audioCodecConfigResult) {
            encoder.start();
        }
    }

    /**
     * @see <a
     * href="http://stackoverflow.com/questions/12216148/android-screen-orientation-differs-between-devices">SO
     * post</a>
     */
    public static void setCameraDisplayOrientation(Context context, Camera mCamera) {
        final int rotationOffset;
        // Check "normal" screen orientation and adjust accordingly
        int naturalOrientation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRotation();
        if (naturalOrientation == Surface.ROTATION_0) {
            rotationOffset = 0;
        } else if (naturalOrientation == Surface.ROTATION_90) {
            rotationOffset = 90;
        } else if (naturalOrientation == Surface.ROTATION_180) {
            rotationOffset = 180;
        } else if (naturalOrientation == Surface.ROTATION_270) {
            rotationOffset = 270;
        } else {
            // just hope for the best (shouldn't happen)
            rotationOffset = 0;
        }

        int result;

        /* check API level. If upper API level 21, re-calculate orientation. */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Camera.CameraInfo info =
                    new Camera.CameraInfo();
            Camera.getCameraInfo(0, info);
            int cameraOrientation = info.orientation;
            result = (cameraOrientation - rotationOffset + 360) % 360;
        } else {
            /* if API level is lower than 21, use the default value */
            result = 90;
        }

        /*set display orientation*/
        mCamera.setDisplayOrientation(result);
    }

}
