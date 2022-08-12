package com.myvideoyun.livestream.tool.gpu;

public interface MVYGPUImageInput {
    void setInputSize(int width, int height);
    void setInputFramebuffer(MVYGPUImageFramebuffer newInputFramebuffer);
    void newFrameReady();
}
