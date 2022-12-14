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

    // ??????
    Camera camera;
    MVYCameraPreviewWrap cameraPreviewWrap;
    public static final int FRONT_CAMERA_ID = 1;
    public static final int BACK_CAMERA_ID = 0;
    int mCurrentCameraID = FRONT_CAMERA_ID;

    // ?????????
    AudioRecord audioRecord;
    MVYAudioRecorderWrap audioRecordWrap;

    // ?????????surface
    MVYPreviewView surfaceView;

    // ??????????????????
    volatile MVYMediaCodecEncoder encoder;
    volatile boolean videoCodecConfigResult = false;
    volatile boolean audioCodecConfigResult = false;
    volatile boolean isCodecInit = false;

    // ??????????????????
    String videoPath;

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
    }

    @Override
    public void destroyGLEnvironment() {
        closeHardware();
    }

    /**
     * ??????????????????
     */
    private void openHardware() {
        // ??????????????????
        openFrontCamera();

        // ??????????????????
//        openBackCamera();

        // ???????????????
        openAudioRecorder();
    }

    /**
     * ??????????????????
     */
    private void openFrontCamera() {
        if (cameraPreviewWrap != null) {
            cameraPreviewWrap.stopPreview();
        }
        if (camera != null) {
            camera.release();
        }

        Log.d(TAG, "??????????????????");
        mCurrentCameraID = FRONT_CAMERA_ID;
        camera = Camera.open(mCurrentCameraID); // TODO ?????????????????????????????????
        setCameraDisplayOrientation(this, camera);

        cameraPreviewWrap = new MVYCameraPreviewWrap(camera);
        cameraPreviewWrap.setPreviewListener(this);

        cameraPreviewWrap.setRotateMode(kAYGPUImageRotateRight); // TODO ????????????????????????, ????????????
        cameraPreviewWrap.startPreview(surfaceView.eglContext);
    }

    /**
     * ??????????????????
     */
    private void openBackCamera() {
        if (cameraPreviewWrap != null) {
            cameraPreviewWrap.stopPreview();
        }
        if (camera != null) {
            camera.release();
        }

        Log.d(TAG, "??????????????????");
        mCurrentCameraID = BACK_CAMERA_ID;
        camera = Camera.open(mCurrentCameraID);
        setCameraDisplayOrientation(this, camera);

        cameraPreviewWrap = new MVYCameraPreviewWrap(camera);
        cameraPreviewWrap.setPreviewListener(this);

        cameraPreviewWrap.setRotateMode(kAYGPUImageRotateRightFlipHorizontal); // TODO ????????????????????????, ????????????
        cameraPreviewWrap.startPreview(surfaceView.eglContext);
    }

    /**
     * ???????????????
     */
    private void openAudioRecorder() {
        Log.d(TAG, "???????????????");

        // ????????????????????????
        final int audioSampleRate = 16000;   //???????????????
        final int audioChannel = AudioFormat.CHANNEL_IN_MONO;   //?????????
        final int audioFormat = AudioFormat.ENCODING_PCM_16BIT; //??????????????????

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
     * ??????????????????
     */
    private void closeHardware() {
        // ????????????
        if (camera != null) {
            Log.d(TAG, "????????????");
            cameraPreviewWrap.stopPreview();
            cameraPreviewWrap = null;
            camera.release();
            camera = null;
        }

        // ???????????????
        if (audioRecord != null) {
            Log.d(TAG, "???????????????");
            audioRecordWrap.stop();
            audioRecordWrap = null;
            audioRecord.release();
            audioRecord = null;
        }

        // ??????????????????, ????????????
        closeMediaCodec();
    }

    /**
     * ??????????????????
     * ?????? ??????: ?????????????????????????????????
     * This timestamp is in nanoseconds
     */
    @Override
    public void cameraVideoOutput(int texture, int width, int height, long timestamp) {
        // ?????????surfaceView
        surfaceView.render(texture, width, height);

        // ??????????????????
        if (encoder != null && isCodecInit) {
            encoder.writeImageTexture(texture, width, height, timestamp);
        }
    }

    /**
     * ?????????????????????
     * ?????? ??????: ?????????????????????????????????
     * This timestamp is in nanoseconds
     */
    @Override
    public void audioRecorderOutput(ByteBuffer byteBuffer, long timestamp) {

        // ??????????????????
        if (encoder != null && isCodecInit) {
            encoder.writePCMByteBuffer(byteBuffer, timestamp);
        }
    }

    public void startRecord() {
        // ??????????????????
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
     * ???????????????
     */
    private boolean startMediaCodec() {

        // ??????????????????
        int width = 1280; // ??????????????????????????????90???
        int height = 720;
        int bitRate = 1000000; // ??????: 1Mbps
        int fps = 30; // ??????: 30
        int iFrameInterval = 1; // GOP: 30

        // ??????????????????
        int audioBitRate = 128000; // ??????: 128kbps
        int sampleRate = 16000; // ?????????

        // ???????????????
        CodecInfo codecInfo = getAvcSupportedFormatInfo();
        if (codecInfo == null) {
            Log.d(TAG, "??????????????????");
            return false;
        }

        // ???????????????????????????????????????????????????
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

        Log.d(TAG, "?????????????????????????????????" + "width = " + width + "height = " + height + "bitRate = " + bitRate
                + "fps = " + fps + "IFrameInterval = " + iFrameInterval);

        // ????????????
        encoder = new MVYMediaCodecEncoder(videoPath);
        encoder.setContentMode(kAYGPUImageScaleAspectFill);
        encoder.setMediaCodecEncoderListener(this);
        boolean videoCodecInitResult = encoder.configureVideoCodec(surfaceView.eglContext, width, height, bitRate, fps, iFrameInterval);
        boolean audioCodecInitResult = encoder.configureAudioCodec(audioBitRate, sampleRate, 1);

        isCodecInit = videoCodecInitResult && audioCodecInitResult;
        return isCodecInit;
    }

    /**
     * ???????????????
     */
    private boolean closeMediaCodec() {
        // ????????????
        if (encoder != null) {
            Log.d(TAG, "???????????????");
            encoder.finish();
            encoder = null;
        }

        boolean recordSuccess = videoCodecConfigResult && audioCodecConfigResult;

        // ?????????????????????
        videoCodecConfigResult = false;
        audioCodecConfigResult = false;
        isCodecInit = false;

        return recordSuccess;
    }

    /**
     * ??????????????????????????? ??????
     * ?????? ??????: ?????????????????????????????????
     */
    @Override
    public void encoderOutputVideoFormat(MediaFormat format) {
        videoCodecConfigResult = true;
        if (videoCodecConfigResult && audioCodecConfigResult) {
            encoder.start();
        }
    }

    /**
     * ??????????????????????????? ??????
     * ?????? ??????: ?????????????????????????????????
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
