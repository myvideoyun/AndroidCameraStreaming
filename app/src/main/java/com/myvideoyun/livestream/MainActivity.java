package com.myvideoyun.livestream;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        findViewById(R.id.main_livestream).setOnClickListener(this);
        findViewById(R.id.main_camera).setOnClickListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1001) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }

            enterLiveStreamPage();
        } else if (requestCode == 1002) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }

            enterCameraPage();
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.main_livestream) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 1001);
                } else {
                    enterLiveStreamPage();
                }
            } else {
                enterLiveStreamPage();
            }

        } else if (view.getId() == R.id.main_camera) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 1002);
                } else {
                    enterCameraPage();
                }
            } else {
                enterCameraPage();
            }

        }

    }

    private void enterLiveStreamPage() {
        startActivity(new Intent(this, LiveStreamActivity.class));
    }

    private void enterCameraPage() {
        startActivity(new Intent(this, CameraActivity.class));
    }
}
