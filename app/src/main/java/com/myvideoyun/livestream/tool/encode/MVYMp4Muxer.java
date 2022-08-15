package com.myvideoyun.livestream.tool.encode;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MVYMp4Muxer {

    private MediaMuxer muxer;
    private ReadWriteLock lock = new ReentrantReadWriteLock(false);
    private boolean isStart = false;
    private Map<String, Integer> indexInfo = new HashMap<>();

    /**
     * 设置路径
     */
    public void setPath(String path) throws IOException {
        muxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        muxer.setOrientationHint(90);
    }

    /**
     * 设置音视频轨道, 如果已经设置返回已经设置的值
     */
    public int addTrack(MediaFormat mediaFormat) {
        lock.writeLock().lock();

        if (muxer == null) {
            lock.writeLock().unlock();
            return -1;
        }

        // 防止重复添加轨道
        Integer index = indexInfo.get(mediaFormat.getString(MediaFormat.KEY_MIME));

        if (index != null) {
            Log.w(MVYGPUImageConstants.TAG, "🍇  encoder -> 请勿重复添加 " + mediaFormat.getString(MediaFormat.KEY_MIME) + " 轨道");
            lock.writeLock().unlock();
            return index;

        } else if (!isStart) {
            Log.w(MVYGPUImageConstants.TAG, "🍇  encoder -> 添加轨道 " + mediaFormat);
            int trackIndex = muxer.addTrack(mediaFormat);

            indexInfo.put(mediaFormat.getString(MediaFormat.KEY_MIME), trackIndex);

            lock.writeLock().unlock();
            return trackIndex;

        } else {
            Log.w(MVYGPUImageConstants.TAG, "🍇  encoder -> 编码器已经启动, 无法再添加轨道");
            lock.writeLock().unlock();
            return -1;
        }


    }

    public void start() {
        lock.writeLock().lock();

        if (muxer == null) {
            lock.writeLock().unlock();
            return;
        }

        if (isStart) {
            Log.w(MVYGPUImageConstants.TAG, "🍇  encoder -> 请勿重复 start muxer");
            lock.writeLock().unlock();
            return;
        }

        muxer.start();
        isStart = true;

        lock.writeLock().unlock();
    }

    /**
     * 写入数据
     */
    public void addData(int trackIndex, ByteBuffer buffer, MediaCodec.BufferInfo info) {
        lock.readLock().lock();

        if (muxer == null) {
            lock.readLock().unlock();
            return;
        }

        if (!isStart) {
            Log.w(MVYGPUImageConstants.TAG, "🍇  encoder -> muxer 还未启动");
            lock.writeLock().unlock();
            return;
        }

        if (trackIndex == -1) {
            Log.w(MVYGPUImageConstants.TAG, "🍇  encoder -> muxer 写入数据失败, track 不能为 -1");
            lock.readLock().unlock();
            return;
        }

        if (info.size == 0) {
            // 结束标识不需要写入
            lock.readLock().unlock();
            return;
        }

        buffer.position(info.offset);
        buffer.limit(info.offset + info.size);

        muxer.writeSampleData(trackIndex, buffer, info);

        lock.readLock().unlock();
    }

    /**
     * 写入完成
     */
    public void finish() {
        lock.writeLock().lock();

        if (muxer == null) {
            lock.writeLock().unlock();
            return;
        }

        try {
            if (isStart) {
                isStart = false;
                muxer.stop();
            }
            muxer.release();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } finally {
            muxer = null;
            indexInfo.clear();
            lock.writeLock().unlock();
        }
    }
}