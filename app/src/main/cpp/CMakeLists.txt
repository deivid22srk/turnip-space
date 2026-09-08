cmake_minimum_required(VERSION 3.22.1)
project("turnipspace" LANGUAGES C CXX)

add_library(turniploader SHARED
    turnip_loader.cpp
    gpu_probe.cpp)

target_link_libraries(turniploader
    EGL
    GLESv2
    android
    log)

target_compile_options(turniploader PRIVATE -Wall -Wextra)
