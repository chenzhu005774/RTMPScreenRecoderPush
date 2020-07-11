#include <jni.h>
#include <string>
#include "x264.h"
#include "librtmp/rtmp.h"
#include <pthread.h>
#include <android/log.h>
#define LOG_TAG "Chnezhu"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)


JNIEXPORT jint JNICALL Java_com_example_administrator_mypush_RtmpClient_read
        (JNIEnv * env, jobject thiz,jlong rtmp, jbyteArray data_, jint offset, jint size) {

    char* data = static_cast<char *>(malloc(size * sizeof(char)));

    int readCount = RTMP_Read((RTMP*)rtmp, data, size);

    if (readCount > 0) {
        (env)->SetByteArrayRegion(data_, offset, readCount, reinterpret_cast<const jbyte *>(data));  // copy
    }
    free(data);

    return readCount;
}



JNIEXPORT jint JNICALL Java_com_example_administrator_mypush_RtmpClient_close (JNIEnv * env,jlong rtmp, jobject thiz) {
    RTMP_Close((RTMP*)rtmp);
    RTMP_Free((RTMP*)rtmp);
    return 0;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_administrator_mypush_RtmpClient_open(JNIEnv *env, jclass type, jstring url_,
                                                      jboolean isPublishMode) {
    const char *url = (env)->GetStringUTFChars( url_, 0);
    LOGD("RTMP_OPENING:%s",url);
    RTMP* rtmp = RTMP_Alloc();
    if (rtmp == NULL) {
        LOGD("RTMP_Alloc=NULL");
        return NULL;
    }

    RTMP_Init(rtmp);
    int ret = RTMP_SetupURL(rtmp, const_cast<char *>(url));

    if (!ret) {
        RTMP_Free(rtmp);
        rtmp=NULL;
        LOGD("RTMP_SetupURL=ret");
        return NULL;
    }
    if (isPublishMode) {
        RTMP_EnableWrite(rtmp);
    }

    ret = RTMP_Connect(rtmp, NULL);
    if (!ret) {
        RTMP_Free(rtmp);
        rtmp=NULL;
        LOGD("RTMP_Connect=ret");
        return NULL;
    }
    ret = RTMP_ConnectStream(rtmp, 0);

    if (!ret) {
        ret = RTMP_ConnectStream(rtmp, 0);
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
        rtmp=NULL;
        LOGD("RTMP_ConnectStream=ret");
        return NULL;
    }
    (env)->ReleaseStringUTFChars( url_, url);
    LOGD("RTMP_OPENED");
    return reinterpret_cast<jlong>(rtmp);
}extern "C"
JNIEXPORT jint JNICALL
Java_com_example_administrator_mypush_RtmpClient_write(JNIEnv *env, jclass type_, jlong rtmpPointer,
                                                       jbyteArray data_, jint size, jint type,
                                                       jint ts) {
    LOGD("start write");
    jbyte *buffer = (env)->GetByteArrayElements( data_, NULL);
    RTMPPacket *packet = (RTMPPacket*)malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, size);
    RTMPPacket_Reset(packet);
    if (type == RTMP_PACKET_TYPE_INFO) { // metadata
        packet->m_nChannel = 0x03;
    } else if (type == RTMP_PACKET_TYPE_VIDEO) { // video
        packet->m_nChannel = 0x04;
    } else if (type == RTMP_PACKET_TYPE_AUDIO) { //audio
        packet->m_nChannel = 0x05;
    } else {
        packet->m_nChannel = -1;
    }

    packet->m_nInfoField2  =  ((RTMP*)rtmpPointer)->m_stream_id;

    LOGD("write data type: %d, ts %d", type, ts);

    memcpy(packet->m_body,  buffer,  size);
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_hasAbsTimestamp = FALSE;
    packet->m_nTimeStamp = ts;
    packet->m_packetType = type;
    packet->m_nBodySize  = size;
    int ret = RTMP_SendPacket((RTMP*)rtmpPointer, packet, 0);
    RTMPPacket_Free(packet);
    free(packet);
    (env)->ReleaseByteArrayElements( data_, buffer, 0);
    if (!ret) {
        LOGD("end write error %d", ret);
        return ret;
    }else
    {
        LOGD("end write success");
        return 0;
    }
}

