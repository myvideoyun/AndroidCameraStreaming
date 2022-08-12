package com.myvideoyun.livestream.tool.camera;

public interface MVYCameraPreviewListener {
    void cameraVideoOutput(int texture, int width, int height, long timestamp);
}
