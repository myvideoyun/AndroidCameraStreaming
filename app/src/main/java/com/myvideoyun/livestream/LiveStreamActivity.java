package com.myvideoyun.livestream;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

import com.myvideoyun.livestream.tool.GreenScreenRender;
import com.pedro.encoder.input.gl.render.filters.object.ImageObjectFilterRender;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.encoder.utils.gl.TranslateTo;
import com.pedro.rtmp.utils.ConnectCheckerRtmp;
import com.pedro.rtplibrary.rtmp.RtmpCamera1;
import com.pedro.rtplibrary.view.OpenGlView;

public class LiveStreamActivity extends AppCompatActivity implements ConnectCheckerRtmp, SurfaceHolder.Callback {

    private RtmpCamera1 rtmpCamera1;
    private ToggleButton toggleButton;
    private EditText etUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_livestream);

        OpenGlView openGlView = findViewById(R.id.surfaceView);

        toggleButton = findViewById(R.id.b_start_stop);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!rtmpCamera1.isStreaming()) {
                    if (rtmpCamera1.isRecording()
                            || rtmpCamera1.prepareAudio() && rtmpCamera1.prepareVideo()) {
                        toggleButton.setChecked(true);
                        rtmpCamera1.startStream(etUrl.getText().toString());
                    } else {
                        Toast.makeText(LiveStreamActivity.this, "Error preparing stream, This device cant do it",
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    toggleButton.setChecked(false);
                    rtmpCamera1.stopStream();
                }
            }
        });


        findViewById(R.id.switch_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
                    rtmpCamera1.switchCamera();
                } catch (CameraOpenException e) {
                    Toast.makeText(LiveStreamActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        etUrl = findViewById(R.id.et_rtp_url);
        etUrl.setHint(R.string.hint_rtmp);

        rtmpCamera1 = new RtmpCamera1(openGlView, this);

        openGlView.getHolder().addCallback(this);

        // 设置绿幕Filter
        GreenScreenRender greenScreenRender = new GreenScreenRender();
        rtmpCamera1.getGlInterface().addFilter(greenScreenRender);
        greenScreenRender.setImage(BitmapFactory.decodeResource(getResources(), R.mipmap.girl));

        // 设置前景
        ImageObjectFilterRender imageObjectFilterRender = new ImageObjectFilterRender();
        rtmpCamera1.getGlInterface().addFilter(imageObjectFilterRender);
        imageObjectFilterRender.setImage(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
        imageObjectFilterRender.setDefaultScale(600, 800);

        imageObjectFilterRender.setPosition(TranslateTo.TOP_RIGHT);
    }

    @Override
    public void onConnectionStartedRtmp(String rtmpUrl) {
    }

    @Override
    public void onConnectionSuccessRtmp() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LiveStreamActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onConnectionFailedRtmp(final String reason) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LiveStreamActivity.this, "Connection failed. " + reason, Toast.LENGTH_SHORT)
                        .show();
                rtmpCamera1.stopStream();
                toggleButton.setChecked(false);
            }
        });
    }

    @Override
    public void onNewBitrateRtmp(long bitrate) {

    }

    @Override
    public void onDisconnectRtmp() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LiveStreamActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onAuthErrorRtmp() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LiveStreamActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onAuthSuccessRtmp() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LiveStreamActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
            }
        });
    }


    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        rtmpCamera1.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if (rtmpCamera1.isStreaming()) {
            rtmpCamera1.stopStream();
            toggleButton.setChecked(false);
        }
        rtmpCamera1.stopPreview();
    }
}