cmake_minimum_required(VERSION 3.4.1)
add_subdirectory(src/main/cpp/librtmp)
add_library(
             nativepush
             SHARED
             src/main/cpp/native-lib.c
             )

find_library(   log-lib
                 log )


target_link_libraries( nativepush
                       rtmp
                       ${log-lib} )