cmake_minimum_required(VERSION 3.4.1)
add_subdirectory(${CMAKE_SOURCE_DIR}/src/main/cpp/librtmp)



include_directories (${CMAKE_SOURCE_DIR}/src/main/cpp/include)

#CMAKE_CXX_FLAGS会被添加到build.ninja的FLAGS中，ANDROID_ABI是内置变量，不要直接写死armeabi-v7a
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -L${CMAKE_SOURCE_DIR}/src/main/cpp/libs/${ANDROID_ABI}")

add_library(
             native-lib

             SHARED

             src/main/cpp/native-lib.cpp
             )

find_library(   log-lib

                 log )


target_link_libraries( native-lib
                       rtmp
                       #x264
                       #faac
                       ${log-lib} )