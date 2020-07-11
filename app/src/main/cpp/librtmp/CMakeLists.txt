
cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DNO_CRYPTO") #CMAKE_C_FLAGS：用C的方式去编译，  -D传入参数
add_library(
        rtmp    #生成库的名称
        STATIC  #静态库
        amf.c   #下面是需要的.c文件，可以在rtmp饿makefile中看到
        hashswf.c
        log.c
        parseurl.c
        rtmp.c
)