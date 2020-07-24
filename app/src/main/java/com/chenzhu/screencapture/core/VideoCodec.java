package com.chenzhu.screencapture.core;

import android.annotation.TargetApi;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;



public class VideoCodec extends Thread {

    private static final String MIME_TYPE = "video/avc";

    public final static int WIDTH = 1280;
    public final static int HEIGHT = 720;
    private final static int BITRETE = 500_000;
    private final static int FRAME_RATE = 20; // fps
    private final static int IFRAME_INTERVAL = 2;//关键帧间隔2s

    private final static String TAG = "VideoCodec";
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaCodec mediaCodec;
    private boolean isRecoding;
    private MediaCodec.BufferInfo bufferInfo;
    private long startTime;
    private long timeStamp;


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public boolean prepare() {
        if (null == mediaProjection) {
            return false;
        }
        bufferInfo = new MediaCodec.BufferInfo();
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BITRETE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        Log.d(TAG, "created video format: " + format);
        try {
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface surface = mediaCodec.createInputSurface();
        Log.d(TAG, "created video input surface: " + surface);
        mediaCodec.start();
        //创建一个公共显示。surface是mediacodec的in
        virtualDisplay = mediaProjection.createVirtualDisplay("screen-codec",
                WIDTH, HEIGHT, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                surface, null, null);
        return true;

    }

    public void setMediaProjection(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void run() {
        while (isRecoding) {
            if (timeStamp != 0) {//2000毫秒后
                if (System.currentTimeMillis() - timeStamp >= 2_000) {
                    Bundle params = new Bundle();
                    //立即刷新 让下一帧是关键帧
                    params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                    mediaCodec.setParameters(params);
                    timeStamp = System.currentTimeMillis();
                }
            } else {
                timeStamp = System.currentTimeMillis();
            }
            int index = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
           //TODO 这里是解决累计延时的关键地方
            try {
                sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (index >= 0) {
                ByteBuffer buffer = mediaCodec.getOutputBuffer(index);
                byte[] outData = new byte[bufferInfo.size];
                buffer.get(outData);
                //这样也能拿到 sps pps
//                ByteBuffer sps = mediaCodec.getOutputFormat().getByteBuffer
//                        ("csd-0");
//                ByteBuffer pps = mediaCodec.getOutputFormat().getByteBuffer
//                        ("csd-1");
                if (startTime == 0) {
                    startTime = bufferInfo.presentationTimeUs / 1000;
                    Log.i(TAG, "video tms " + startTime);
                }
                IFrame iFrame = new IFrame();
                iFrame.setBuffer(outData);
                iFrame.setType(IFrame.RTMP_PACKET_TYPE_VIDEO);
                iFrame.setTms((bufferInfo.presentationTimeUs / 1000) - startTime);
                RtmpManager.getInstance().addFrame(iFrame);
                mediaCodec.releaseOutputBuffer(index, false);
            }
        }
        isRecoding = false;
        startTime = 0;
        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;
        virtualDisplay.release();
        virtualDisplay = null;
        mediaProjection.stop();
        mediaProjection = null;
        Log.i(TAG, "release video");
    }

    public void startCoding() {
        isRecoding = true;
        start();
    }

    public void stopCoding() {
        isRecoding = false;
        try {
            join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
