cmake_minimum_required(VERSION 3.22.1)
project(alvm_android LANGUAGES CXX)

add_library(alvm SHARED alvm_jni.cpp)
target_compile_features(alvm PRIVATE cxx_std_17)
target_compile_options(alvm PRIVATE -O2 -fexceptions -frtti)
