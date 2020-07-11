package com.example.administrator.rtmp;

/**
 */
public class RESFlvData {

//    提高码率BPS
//    降低帧率
//    调小关键帧间隔（MediaFormat.KEY_I_FRAME_INTERVAL）
//    提高AVCProfile（Android默认使用的是BaseLine模式）

    // video size
    public static final int VIDEO_WIDTH = 1280;
//    public static final int VIDEO_WIDTH = 960;
    public static final int VIDEO_HEIGHT = 720;
//    public static final int VIDEO_HEIGHT = 540;
//    public static final int VIDEO_BITRATE = 500000; // 500Kbps
    public static final int VIDEO_BITRATE =  2048*1000; // 1M


//    public static final int FPS = 20;
    public static final int FPS = 25;
    public static final int AAC_SAMPLE_RATE = 44100;
    public static final int AAC_BITRATE = 32 * 1024;

    public static final int FLV_RTMP_PACKET_TYPE_VIDEO = 9;
    public static final int FLV_RTMP_PACKET_TYPE_AUDIO = 8;
    public static final int FLV_RTMP_PACKET_TYPE_INFO = 18;
    public static final int NALU_TYPE_IDR = 5;

    public boolean droppable;

    public int dts;//解码时间戳

    public byte[] byteBuffer; //数据

    public int size; //字节长度

    public int flvTagType; //视频和音频的分类

    public int videoFrameType;

    public boolean isKeyframe() {
        return videoFrameType == NALU_TYPE_IDR;
    }

}
