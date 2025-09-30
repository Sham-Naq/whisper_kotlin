cmake_minimum_required(VERSION 3.22)
project(whisper_android_kotlin)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Point to a vendored copy of whisper.cpp inside the app module so everything lives under app/src/main/cpp
# Expected location: app/src/main/cpp/third_party/whisper.cpp
set(WHISPER_LIB_DIR "${CMAKE_SOURCE_DIR}/third_party/whisper.cpp")

if (NOT EXISTS "${WHISPER_LIB_DIR}/src/whisper.cpp")
    message(FATAL_ERROR "whisper.cpp not found at ${WHISPER_LIB_DIR}.\n"
        "Add it via: git submodule add https://github.com/ggerganov/whisper.cpp app/src/main/cpp/third_party/whisper.cpp\n"
        "Then run: git submodule update --init --recursive")
endif()

message(STATUS "Using WHISPER_LIB_DIR=${WHISPER_LIB_DIR}")

# Read version from whisper.cpp main CMake
file(READ "${WHISPER_LIB_DIR}/CMakeLists.txt" MAIN_CMAKE_CONTENT)
string(REGEX MATCH "project\(\"whisper\\.cpp\" VERSION ([0-9]+\\.[0-9]+\\.[0-9]+)\)" VERSION_MATCH "${MAIN_CMAKE_CONTENT}")
if(CMAKE_MATCH_1)
    set(WHISPER_VERSION ${CMAKE_MATCH_1})
else()
    set(WHISPER_VERSION "unknown")
endif()
message(STATUS "Whisper version: ${WHISPER_VERSION}")

set(SOURCE_FILES
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${CMAKE_SOURCE_DIR}/jni/whisper/jni.c
)

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)

include(FetchContent)
FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

function(build_library target_name)
    add_library(${target_name} SHARED ${SOURCE_FILES})

    target_link_libraries(${target_name} PRIVATE ${LOG_LIB} ${ANDROID_LIB} ggml)
    target_compile_definitions(${target_name} PUBLIC GGML_USE_CPU)
    target_compile_definitions(${target_name} PRIVATE WHISPER_VERSION="${WHISPER_VERSION}")

    if (${target_name} STREQUAL "whisper_v8fp16_va")
        target_compile_options(${target_name} PRIVATE -march=armv8.2-a+fp16)
    elseif (${target_name} STREQUAL "whisper_vfpv4")
        target_compile_options(${target_name} PRIVATE -mfpu=neon-vfpv4)
    endif ()

    if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
        target_compile_options(${target_name} PRIVATE -O3 -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden)
        # target_link_options(${target_name} PRIVATE -Wl,--gc-sections)
    endif ()

    target_include_directories(${target_name} PRIVATE
        ${WHISPER_LIB_DIR}
        ${WHISPER_LIB_DIR}/src
        ${WHISPER_LIB_DIR}/include
        ${WHISPER_LIB_DIR}/ggml/include
        ${WHISPER_LIB_DIR}/ggml/src
        ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu)
endfunction()

# Default
build_library(whisper)

# Per-ABI optimized variants (only built when that ABI is targeted)
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    build_library(whisper_v8fp16_va)
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    build_library(whisper_vfpv4)
endif()
