package com.myvideoyun.livestream.tool.encode;

import android.media.MediaFormat;

public interface MVYMediaCodecEncoderListener {

    void encoderOutputVideoFormat(MediaFormat format);

    void encoderOutputAudioFormat(MediaFormat format);
}
