#include <jni.h>
#include "packt.h"


Pusher *pusher = 0;

JNIEXPORT jboolean JNICALL
Java_com_chenzhu_screencapture_core_RtmpManager_connect(JNIEnv *env, jobject instance, jstring url_) {
    const char *url = (*env)->GetStringUTFChars(env, url_, 0);
    int ret;
    do {
        pusher = malloc(sizeof(Pusher));
        memset(pusher, 0, sizeof(Pusher));
        pusher->rtmp = RTMP_Alloc();
        RTMP_Init(pusher->rtmp);
        pusher->rtmp->Link.timeout = 10;
        LOGI("connect %s", url);
        if (!(ret = RTMP_SetupURL(pusher->rtmp, url))) break;
        RTMP_EnableWrite(pusher->rtmp);
        LOGI("RTMP_Connect");
        if (!(ret = RTMP_Connect(pusher->rtmp, 0))) break;
        LOGI("RTMP_ConnectStream ");
        if (!(ret = RTMP_ConnectStream(pusher->rtmp, 0))) break;
        LOGI("connect success");
    } while (0);
    (*env)->ReleaseStringUTFChars(env, url_, url);
    return ret;
}

JNIEXPORT jboolean JNICALL
Java_com_chenzhu_screencapture_core_RtmpManager_isConnect(JNIEnv *env, jobject instance) {
    return pusher && pusher->rtmp && RTMP_IsConnected(pusher->rtmp);
}

JNIEXPORT void JNICALL
Java_com_chenzhu_screencapture_core_RtmpManager_disConnect(JNIEnv *env, jobject instance) {
    if (pusher) {
        if (pusher->sps) {
            free(pusher->sps);
        }
        if (pusher->pps) {
            free(pusher->pps);
        }
        if (pusher->rtmp) {
            RTMP_Close(pusher->rtmp);
            RTMP_Free(pusher->rtmp);
        }
        free(pusher);
        pusher = 0;
    }
}

int sendPacket(RTMPPacket *packet) {
    int r = RTMP_SendPacket(pusher->rtmp, packet, 1);
    RTMPPacket_Free(packet);
    free(packet);
    return r;
}

int sendVideo(char *buf, int len, long tms) {
    int ret;
    do {
        if (buf[4] == 0x67) {//sps pps
            if (pusher && (!pusher->pps || !pusher->sps)) {
                parseVideoConfiguration(buf, len, pusher);
            }
        } else {
            if (buf[4] == 0x65) {//关键帧
                RTMPPacket *packet = packetVideoDecode(pusher);
                if (!(ret = sendPacket(packet))) {
                    break;
                }
            }
            RTMPPacket *packet = packetVideoData(buf, len, tms, pusher);
            ret = sendPacket(packet);
        }
    } while (0);
    return ret;
}

int sendAudio(char *buf, int len, int type, int tms) {
    int ret;
    do {
        RTMPPacket *packet = packetAudioData(buf, len, type, tms, pusher);
        ret = sendPacket(packet);
    } while (0);
    return ret;
}

JNIEXPORT  jboolean JNICALL
Java_com_chenzhu_screencapture_core_RtmpManager_sendData(JNIEnv *env, jobject instance, jbyteArray data_,
                                                    jint len, jint type, jlong tms) {
    jbyte *data = (*env)->GetByteArrayElements(env, data_, NULL);
    int ret;
    switch (type) {
        case 0: //video
            ret = sendVideo(data,len, tms);
            break;
        default: //audio
            ret = sendAudio(data, len, type, tms);
            break;
    }
    (*env)->ReleaseByteArrayElements(env, data_, data, 0);
    return ret;
}