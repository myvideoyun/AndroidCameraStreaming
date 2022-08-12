package com.myvideoyun.livestream.plug;

import android.media.MediaCodec;
import android.media.MediaFormat;

import com.myvideoyun.livestream.tool.encode.MVYMp4MuxerInterface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class RtmpPush implements MVYMp4MuxerInterface  {

    /**
     * 设置路径
     */
    public void setPath(String path) throws IOException {

    }

    /**
     * 设置音视频轨道，返回轨道id
     */
    public int addTrack(MediaFormat mediaFormat) {
        return 1;
    }

    /**
     * 开始
     */
    public void start() {

    }

    /**
     * 写入数据
     */
    public void addData(int trackIndex, ByteBuffer buffer, MediaCodec.BufferInfo info) {

    }

    /**
     * 结束
     */
    public void finish() {

    }

}
