package com.myvideoyun.livestream.tool.encode;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface MVYMp4MuxerInterface {
    /**
     * 设置路径
     */
    void setPath(String path) throws IOException;

    /**
     * 设置音视频轨道，返回 -1 表示失败
     */
    int addTrack(MediaFormat mediaFormat);

    /**
     * 开始
     */
    void start();

    /**
     * 写入数据
     */
    void addData(int trackIndex, ByteBuffer buffer, MediaCodec.BufferInfo info);

    /**
     * 结束
     */
    void finish();
}
