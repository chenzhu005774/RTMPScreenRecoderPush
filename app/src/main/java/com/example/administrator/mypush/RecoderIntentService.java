package com.example.administrator.mypush;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.Toast;

import com.example.administrator.core.Packager;
import com.example.administrator.core.RESCoreParameters;
import com.example.administrator.rtmp.FLvMetaData;
import com.example.administrator.rtmp.RESFlvData;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.example.administrator.rtmp.RESFlvData.FLV_RTMP_PACKET_TYPE_VIDEO;
import static java.lang.Thread.sleep;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class RecoderIntentService extends IntentService {

    static   MediaProjection mediaProjection;
    public RecoderIntentService() {
        super("RecoderIntentService");
    }


    public  void startActionFoo(Context context, MediaProjection mediaProjection) {
        this.mediaProjection =mediaProjection;
        Intent intent = new Intent(context, RecoderIntentService.class);
        context.startService(intent);
        Log.d("chenzhu", "this.mediaProjection:"+this.mediaProjection );
    }


    long  jniRtmpPointer=0;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onHandleIntent(Intent intent) {

        try {
            sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        RESCoreParameters   coreParameters = new RESCoreParameters();
        coreParameters.mediacodecAACBitRate = RESFlvData.AAC_BITRATE;
        coreParameters.mediacodecAACSampleRate = RESFlvData.AAC_SAMPLE_RATE;
        coreParameters.mediacodecAVCFrameRate = RESFlvData.FPS;
        coreParameters.videoWidth = RESFlvData.VIDEO_WIDTH;
        coreParameters.videoHeight = RESFlvData.VIDEO_HEIGHT;
        FLvMetaData  fLvMetaData = new FLvMetaData(coreParameters);


        jniRtmpPointer = RtmpClient.open("rtmp://192.168.2.207:1935/live/myapp", true);

        if (jniRtmpPointer == 0) {
            Log.d(TAG,"rtmp init fail" );
            return;
        } else {

            Log.d(TAG,"jniRtmpPointer:"+jniRtmpPointer );
            byte[] MetaData = fLvMetaData.getMetaData();
            RtmpClient.write(jniRtmpPointer,
                    MetaData,
                    MetaData.length,
                    RESFlvData.FLV_RTMP_PACKET_TYPE_INFO, 0);
        }


        prepareEncoder();
        Log.d(TAG, "created virtual display:1111 " +this.mediaProjection);
        creatVirtualDisPlay();
        recordVirtualDisplay();
    }



     private MediaCodec mEncoder;
     private String  TAG ="chenzhu";
     Surface inputSurface;
     int width =960;
     int heigiht =540;
    @SuppressLint("NewApi")
    private void prepareEncoder()  {
        try {
            String MIME_TYPE = "video/avc";
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, heigiht);

            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 1024*1000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30); // 设置帧率
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            format.setInteger(MediaFormat.KEY_BITRATE_MODE,MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);

            Log.d("chenzhu", "created video format: " + format);
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = mEncoder.createInputSurface();
            Log.d(TAG, "created input surface: " + inputSurface);
            mEncoder.start();
        }catch (Exception e){
            Log.d("chenzhu", "created  encoder fail" );
        }

    }



    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void creatVirtualDisPlay(){

        Log.d(TAG, "created virtual display: " +this.mediaProjection);
       VirtualDisplay mVirtualDisplay = mediaProjection.createVirtualDisplay(TAG + "-display",
               width, heigiht, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                inputSurface, null, null);
        Log.d(TAG, "created virtual display: " + mVirtualDisplay);

    }


    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private static final int TIMEOUT_US = 10000;
    private long startTime = 0;
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void recordVirtualDisplay() {
        while (true) {
            int eobIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            switch (eobIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG,"VideoSenderThread,MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
//                     Log.d(TAG,"VideoSenderThread,MediaCodec.INFO_TRY_AGAIN_LATER");
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG,"VideoSenderThread,MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:" + mEncoder.getOutputFormat().toString());
                    sendAVCDecoderConfigurationRecord(0, mEncoder.getOutputFormat());
                    break;
                default:
                    Log.d(TAG,"VideoSenderThread,MediaCode,eobIndex=" + eobIndex);
                    if (startTime == 0) {
                        startTime = mBufferInfo.presentationTimeUs / 1000;
                    }
                    /**
                     * we send sps pps already in INFO_OUTPUT_FORMAT_CHANGED
                     * so we ignore MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                     */
                    if (mBufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && mBufferInfo.size != 0) {
                        ByteBuffer realData = mEncoder.getOutputBuffers()[eobIndex];
                        realData.position(mBufferInfo.offset + 4);
                        realData.limit(mBufferInfo.offset + mBufferInfo.size);
                        sendRealData((mBufferInfo.presentationTimeUs / 1000) - startTime, realData);
                    }
                    mEncoder.releaseOutputBuffer(eobIndex, false);
                    break;
            }
        }
    }





    private void sendAVCDecoderConfigurationRecord(long tms, MediaFormat format) {
        byte[] AVCDecoderConfigurationRecord = Packager.H264Packager.generateAVCDecoderConfigurationRecord(format);
        int packetLen = Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                AVCDecoderConfigurationRecord.length;
        byte[] finalBuff = new byte[packetLen];
        Packager.FLVPackager.fillFlvVideoTag(finalBuff,
                0,
                true,
                true,
                AVCDecoderConfigurationRecord.length);
        System.arraycopy(AVCDecoderConfigurationRecord, 0,
                finalBuff, Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH, AVCDecoderConfigurationRecord.length);
        RESFlvData resFlvData = new RESFlvData();
        resFlvData.droppable = false;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = FLV_RTMP_PACKET_TYPE_VIDEO;
        resFlvData.videoFrameType = RESFlvData.NALU_TYPE_IDR;
        //        TODO send
        RtmpClient.write(jniRtmpPointer, resFlvData.byteBuffer, resFlvData.byteBuffer.length, resFlvData.flvTagType, resFlvData.dts);
    }

    private void sendRealData(long tms, ByteBuffer realData) {
        int realDataLength = realData.remaining();
        int packetLen = Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                Packager.FLVPackager.NALU_HEADER_LENGTH +
                realDataLength;
        byte[] finalBuff = new byte[packetLen];
        realData.get(finalBuff, Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                        Packager.FLVPackager.NALU_HEADER_LENGTH,
                realDataLength);
        int frameType = finalBuff[Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                Packager.FLVPackager.NALU_HEADER_LENGTH] & 0x1F;
        Packager.FLVPackager.fillFlvVideoTag(finalBuff,
                0,
                false,
                frameType == 5,
                realDataLength);
        RESFlvData resFlvData = new RESFlvData();
        resFlvData.droppable = true;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = FLV_RTMP_PACKET_TYPE_VIDEO;
        resFlvData.videoFrameType = frameType;
//        TODO send
        RtmpClient.write(jniRtmpPointer, resFlvData.byteBuffer, resFlvData.byteBuffer.length, resFlvData.flvTagType, resFlvData.dts);
    }


}
