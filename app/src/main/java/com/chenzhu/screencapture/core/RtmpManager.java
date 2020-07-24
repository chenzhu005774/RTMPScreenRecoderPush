package com.chenzhu.screencapture.core;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;

import java.util.concurrent.LinkedBlockingQueue;


public class RtmpManager {


    public static final int STATUS_START = 0;
    public static final int STATUS_STOP = 1;

    public static final int STATUS_NO_PREMISSION = -1;
    public static final int STATUS_VIDEO_FAIL = -2;
    public static final int STATUS_AUDIO_FAIL = -3;
    public static final int STATUS_CONNECT_FAIL = -4;
    public static final int STATUS_SEND_DATA_FAIL = -5;


    public interface Callback {
        void onStatus(int status);
    }

    static {
        System.loadLibrary("nativepush");
    }

    private VideoCodec videoCodec;
    private AudioCodec audioCodec;
    private Callback callback;
    private String url;
    private LinkedBlockingQueue<IFrame> queue;
    private boolean isRecording;
    private MediaProjectionManager mediaProjectionManager;
    private Handler handler;
    private Thread thread;

    @SuppressLint("HandlerLeak")
    private RtmpManager() {
        queue = new LinkedBlockingQueue<>();
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == STATUS_CONNECT_FAIL || msg.what == STATUS_SEND_DATA_FAIL) {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (null != callback)
                        callback.onStatus(msg.what);
                } else if (msg.what == 100) {
                    videoCodec.startCoding();
                    audioCodec.startCoding();
                }
            }
        };
    }

    private static RtmpManager instance;

    public static RtmpManager getInstance() {
        if (null == instance)
            instance = new RtmpManager();
        return instance;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }


    protected void addFrame(IFrame frame) {
        if (!isConnect() || !isRecording) return;
        queue.add(frame);
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            MediaProjection mediaProjection = mediaProjectionManager.getMediaProjection
                    (resultCode, data);
            videoCodec = new VideoCodec();
            videoCodec.setMediaProjection(mediaProjection);
            if (!videoCodec.prepare()) {
                videoCodec.stopCoding();
                if (null != callback)
                    callback.onStatus(STATUS_VIDEO_FAIL);
                return;
            }
            audioCodec = new AudioCodec();
            if (!audioCodec.prepare()) {
                audioCodec.stopCoding();
                if (null != callback)
                    callback.onStatus(STATUS_AUDIO_FAIL);
                return;
            }
            isRecording = true;
            thread = new Thread() {
                @Override
                public void run() {
                    if (!connect(url)) {
                        handler.sendEmptyMessage(STATUS_CONNECT_FAIL);
                        return;
                    }
                    boolean isSend = true;
                    handler.sendEmptyMessage(100);
                    while (isRecording && isSend) {
                        if (queue.size() <= 0) {
                            continue;
                        }
                        IFrame iFrame = null;
                        try {
                            iFrame = queue.take();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if (null == iFrame) {
                            break;
                        }
                        if (iFrame.getBuffer() != null && iFrame.getBuffer().length != 0)
                            isSend = sendData(iFrame.getBuffer(), iFrame.getBuffer().length, iFrame
                                    .getType(), iFrame.getTms());
                    }
                    isRecording = false;
                    queue.clear();
                    disConnect();
                    if (null != audioCodec)
                        audioCodec.stopCoding();
                    if (null != videoCodec)
                        videoCodec.stopCoding();
                    if (!isSend) {
                        handler.sendEmptyMessage(STATUS_SEND_DATA_FAIL);
                    }
                }
            };
            thread.start();
            if (null != callback)
                callback.onStatus(STATUS_START);
        } else {
            if (null != callback)
                callback.onStatus(STATUS_NO_PREMISSION);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void startScreenCapture(Activity activity, String url) {
        this.url = url;
        mediaProjectionManager = (MediaProjectionManager) activity
                .getSystemService
                        (Context.MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
        activity.startActivityForResult(captureIntent, 100);
    }

    public void stopScreenCapture() {
        isRecording = false;
        if (null != thread) {
            thread.interrupt();
        }
        callback.onStatus(STATUS_STOP);
    }


    private native boolean connect(String url);

    private native boolean isConnect();

    private native void disConnect();

    public native boolean sendData(byte[] data, int len, int type, long tms);


}
