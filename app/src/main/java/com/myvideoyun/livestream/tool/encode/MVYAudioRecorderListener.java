package com.myvideoyun.livestream.tool.encode;

import java.nio.ByteBuffer;

public interface MVYAudioRecorderListener {
    void audioRecorderOutput(ByteBuffer byteBuffer, long timestamp);
}
