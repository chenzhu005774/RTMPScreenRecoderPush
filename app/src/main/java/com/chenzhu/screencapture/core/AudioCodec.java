package com.chenzhu.screencapture.core;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;



public class AudioCodec extends Thread {

    private final static String MIME_TYPE = "audio/mp4a-latm";
    private final static int SAMPLE_RATE = 44100; //采样
    private final static int CHANNEL_COUNT = 1; //单声道
    private final static int BITRETE = 64_000;


    private MediaCodec.BufferInfo bufferInfo;
    private final static String TAG = "AudioCodec";
    private MediaCodec mediaCodec;
    private byte[] audioDecoderSpecificInfo;
    private AudioRecord audioRecord;
    private boolean isRecoding;

    private long startTime;


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public boolean prepare() {
        bufferInfo = new MediaCodec.BufferInfo();
        MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel
                .AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BITRETE);
        Log.d(TAG, "created audio format: " + format);
        try {
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
        //从faac库将代码用java实现了.
        audioDecoderSpecificInfo = AudioSpecificConfig.getAudioDecoderSpecificInfo
                (MediaCodecInfo.CodecProfileLevel
                        .AACObjectLC, SAMPLE_RATE, CHANNEL_COUNT);
        // 这样也能拿到
        // ByteBuffer csd0 = mediaCodec.getOutputFormat().getByteBuffer("csd-0");
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
        return true;
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void run() {
        IFrame iFrame = new IFrame();
        iFrame.setBuffer(audioDecoderSpecificInfo);
        iFrame.setType(IFrame.RTMP_PACKET_TYPE_AUDIO_HEAD);
        RtmpManager.getInstance().addFrame(iFrame);
        audioRecord.startRecording();
        byte[] buffer = new byte[2048];
        while (isRecoding) {
            int len = audioRecord.read(buffer, 0, buffer.length);
            if (len <= 0) {
                continue;
            }
            //立即得到有效输入缓冲区
            int index = mediaCodec.dequeueInputBuffer(0);
            if (index >= 0) {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(index);
                inputBuffer.clear();
                inputBuffer.put(buffer, 0, len);
                //填充数据后再加入队列
                mediaCodec.queueInputBuffer(index, 0, len,
                        System.currentTimeMillis(), 0);
            }
            index = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            while (index >= 0 && isRecoding) {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(index);
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                if (startTime == 0) {
                    startTime = bufferInfo.presentationTimeUs;
                    Log.i(TAG, "audio tms " + startTime);
                }
                iFrame = new IFrame();
                iFrame.setBuffer(outData);
                iFrame.setType(IFrame.RTMP_PACKET_TYPE_AUDIO_DATA);
                iFrame.setTms(bufferInfo.presentationTimeUs - startTime);
                RtmpManager.getInstance().addFrame(iFrame);
                mediaCodec.releaseOutputBuffer(index, false);
                index = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        }
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;

        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;
        startTime = 0;
        isRecoding = false;
        Log.i(TAG, "release audio");
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
