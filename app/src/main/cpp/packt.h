
#ifndef SCREENLIVE_PACKT_H
#define SCREENLIVE_PACKT_H


#include <string.h>
#include "librtmp/rtmp.h"
#include <android/log.h>
#include <malloc.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,"RTMP",__VA_ARGS__)

typedef struct {
    int sps_len;
    int pps_len;
    char *sps;
    char *pps;
    RTMP *rtmp;
} Pusher;


RTMPPacket *packetVideoDecode(Pusher *pusher) {
    int body_size = 13 + pusher->sps_len + 3 + pusher->pps_len;
    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, body_size);
    int i = 0;
    //AVC sequence header 与IDR一样
    packet->m_body[i++] = 0x17;
    //AVC sequence header 设置为0x00
    packet->m_body[i++] = 0x00;
    //CompositionTime
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    //AVC sequence header
    packet->m_body[i++] = 0x01;   //configurationVersion 版本号 1
    packet->m_body[i++] = pusher->sps[1]; //profile 如baseline、main、 high

    packet->m_body[i++] = pusher->sps[2]; //profile_compatibility 兼容性
    packet->m_body[i++] = pusher->sps[3]; //profile level
    packet->m_body[i++] = 0xFF; // reserved（111111） + lengthSizeMinusOne（2位 nal 长度） 总是0xff
    //sps
    packet->m_body[i++] = 0xE1; //reserved（111） + lengthSizeMinusOne（5位 sps 个数） 总是0xe1
    //sps length 2字节
    packet->m_body[i++] = (pusher->sps_len >> 8) & 0xff; //第0个字节
    packet->m_body[i++] = pusher->sps_len & 0xff;        //第1个字节
    memcpy(&packet->m_body[i], pusher->sps, pusher->sps_len);
    i += pusher->sps_len;

    /*pps*/
    packet->m_body[i++] = 0x01; //pps number
    //pps length
    packet->m_body[i++] = (pusher->pps_len >> 8) & 0xff;
    packet->m_body[i++] = pusher->pps_len & 0xff;
    memcpy(&packet->m_body[i], pusher->pps, pusher->pps_len);

    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = body_size;
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = pusher->rtmp->m_stream_id;
    return packet;
}

RTMPPacket *packetVideoData(char *buf, int len, const long tms, Pusher *pusher) {
    buf += 4;
    len -= 4;
    int body_size = len + 9;
    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, len + 9);

    packet->m_body[0] = 0x27;
    if (buf[0] == 0x65) { //关键帧
        packet->m_body[0] = 0x17;
        LOGI("发送关键帧 data");
    }
    packet->m_body[1] = 0x01;
    packet->m_body[2] = 0x00;
    packet->m_body[3] = 0x00;
    packet->m_body[4] = 0x00;

    //长度
    packet->m_body[5] = (len >> 24) & 0xff;
    packet->m_body[6] = (len >> 16) & 0xff;
    packet->m_body[7] = (len >> 8) & 0xff;
    packet->m_body[8] = (len) & 0xff;

    //数据
    memcpy(&packet->m_body[9], buf, len);


    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = body_size;
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = tms;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = pusher->rtmp->m_stream_id;
    return packet;

}

RTMPPacket *packetAudioData(const char *buf, const int len, const int type, const long tms,
                            Pusher *pusher) {
    int body_size = len + 2;
    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, body_size);
    //aac的数据头
    packet->m_body[0] = 0xAF;
    packet->m_body[1] = 0x01;
    //解码信息
    if (type == 1) {
        packet->m_body[1] = 0x00;
    }
    memcpy(&packet->m_body[2], buf, len);

    packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet->m_nBodySize = body_size;
    packet->m_nChannel = 0x05;
    packet->m_nTimeStamp = tms;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = pusher->rtmp->m_stream_id;
    return packet;
}


void parseVideoConfiguration(char *data, int len, Pusher *pusher) {
    for (int i = 0; i < len; i++) {
        //0x00 0x00 0x00 0x01
        if (i + 4 < len) {
            if (data[i] == 0x00 && data[i + 1] == 0x00
                && data[i + 2] == 0x00
                && data[i + 3] == 0x01) {
                //0x00 0x00 0x00 0x01 7 sps 0x00 0x00 0x00 0x01 8 pps
                //将sps pps分开
                //找到pps
                if ((data[i + 4] & 0x1f) == 8) {
                    //去掉界定符
                    pusher->sps_len = i - 4;
                    pusher->sps = (char *) malloc(sizeof(char) * pusher->sps_len);
                    memcpy(pusher->sps, data + 4, pusher->sps_len);
                    pusher->pps_len = len - (4 + pusher->sps_len) - 4;
                    pusher->pps = (char *) malloc(sizeof(char) * pusher->pps_len);
                    memcpy(pusher->pps, data + 4 + pusher->sps_len + 4,
                           pusher->pps_len);
                    LOGI("sps:%d pps:%d", pusher->sps_len, pusher->pps_len);
                    break;
                }
            }
        }
    }
}


#endif //SCREENLIVE_PACKT_H
