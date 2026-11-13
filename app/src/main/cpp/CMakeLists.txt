cmake_minimum_required(VERSION 3.22.1)
project(alvm_android LANGUAGES CXX)

set(ALVM_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../..")
set(CI_RUNTIME_DIR "${ALVM_ROOT}/runtime/ci-runtime")
set(CI_RUNTIME_TARGET "")
set(CI_RUNTIME_LINKER "")

if(NOT ANDROID)
    message(FATAL_ERROR "The ALVM Android JNI target must be configured with the Android NDK")
endif()

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CI_RUNTIME_TARGET "aarch64-linux-android")
    set(CI_RUNTIME_LINKER_ENV "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER")
    set(CI_RUNTIME_LINKER_NAME "aarch64-linux-android24-clang")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CI_RUNTIME_TARGET "armv7-linux-androideabi")
    set(CI_RUNTIME_LINKER_ENV "CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER")
    set(CI_RUNTIME_LINKER_NAME "armv7a-linux-androideabi24-clang")
else()
    message(FATAL_ERROR "Unsupported ALVM Android ABI: ${ANDROID_ABI}")
endif()

if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
    if(CMAKE_HOST_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
        set(NDK_HOST_TAG "darwin-arm64")
    else()
        set(NDK_HOST_TAG "darwin-x86_64")
    endif()
elseif(CMAKE_HOST_SYSTEM_NAME STREQUAL "Windows")
    set(NDK_HOST_TAG "windows-x86_64")
else()
    set(NDK_HOST_TAG "linux-x86_64")
endif()

if(DEFINED CMAKE_ANDROID_NDK AND NOT CMAKE_ANDROID_NDK STREQUAL "")
    set(ANDROID_NDK_ROOT "${CMAKE_ANDROID_NDK}")
elseif(DEFINED ENV{ANDROID_NDK_HOME})
    set(ANDROID_NDK_ROOT "$ENV{ANDROID_NDK_HOME}")
elseif(DEFINED ENV{ANDROID_NDK_ROOT})
    set(ANDROID_NDK_ROOT "$ENV{ANDROID_NDK_ROOT}")
else()
    message(FATAL_ERROR "Android NDK path is missing")
endif()

set(CI_RUNTIME_LINKER "${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/${NDK_HOST_TAG}/bin/${CI_RUNTIME_LINKER_NAME}")
if(NOT EXISTS "${CI_RUNTIME_LINKER}")
    message(FATAL_ERROR "Rust Android linker not found: ${CI_RUNTIME_LINKER}")
endif()

find_program(CARGO_EXECUTABLE cargo)
if(NOT CARGO_EXECUTABLE)
    message(FATAL_ERROR "Rust Cargo is required to build ALVM CI Runtime")
endif()

set(CI_RUNTIME_STATICLIB "${CI_RUNTIME_DIR}/target/${CI_RUNTIME_TARGET}/release/libci_runtime.a")
add_custom_command(
    OUTPUT "${CI_RUNTIME_STATICLIB}"
    COMMAND ${CMAKE_COMMAND} -E env
        "${CI_RUNTIME_LINKER_ENV}=${CI_RUNTIME_LINKER}"
        "CI_RUNTIME_VERSION=2.0.0"
        ${CARGO_EXECUTABLE} build
        --manifest-path "${CI_RUNTIME_DIR}/Cargo.toml"
        --lib --release --target "${CI_RUNTIME_TARGET}"
    WORKING_DIRECTORY "${CI_RUNTIME_DIR}"
    DEPENDS
        "${CI_RUNTIME_DIR}/Cargo.toml"
        "${CI_RUNTIME_DIR}/src/lib.rs"
    COMMENT "Building verified ALRM bytecode runtime in Rust for ${ANDROID_ABI}"
    VERBATIM
)

add_custom_target(ci_runtime_build DEPENDS "${CI_RUNTIME_STATICLIB}")
add_library(ci_runtime STATIC IMPORTED GLOBAL)
set_target_properties(ci_runtime PROPERTIES IMPORTED_LOCATION "${CI_RUNTIME_STATICLIB}")

add_library(alvm SHARED alvm_jni.cpp)
target_compile_features(alvm PRIVATE cxx_std_17)
target_compile_options(alvm PRIVATE -O3 -DNDEBUG -fexceptions -frtti)
add_dependencies(alvm ci_runtime_build)
target_link_libraries(alvm PRIVATE ci_runtime log android dl m atomic)
