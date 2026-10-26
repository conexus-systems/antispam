cmake_minimum_required(VERSION 3.10.2)

project("antispam_rust" LANGUAGES)

# Android NDK toolchain
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(RUST_TARGET "aarch64-linux-android")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(RUST_TARGET "armv7-linux-androideabi")
elseif(ANDROID_ABI STREQUAL "x86")
    set(RUST_TARGET "i686-linux-android")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(RUST_TARGET "x86_64-linux-android")
else()
    message(FATAL_ERROR "Unsupported ANDROID_ABI: ${ANDROID_ABI}")
endif()

# Import the pre-built library
add_library(antispam_android SHARED IMPORTED)
set_target_properties(antispam_android PROPERTIES
    IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libantispam_android.so"
)