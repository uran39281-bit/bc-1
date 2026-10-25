# Speech-to-text for auto captions: whisper.cpp (MIT, vendored v1.9.5 source in BC-1/third_party) on the CPU,
# behind a small JNI bridge (bc1_whisper.cpp). Always built optimised: an unoptimised build is ~10x slower.
cmake_minimum_required(VERSION 3.22)
project(bc1whisper C CXX)

set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)  # static whisper/ggml linked into the JNI shared library
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)      # cross-compiling: pick the target ISA explicitly below
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)      # ggml's own thread pool; no libomp to ship
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
if (ANDROID_ABI STREQUAL "arm64-v8a")
    # Baseline ARM64 works on older supported phones; do not require optional dot-product instructions.
    set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)
endif()

add_subdirectory(${WHISPER_DIR} whisper EXCLUDE_FROM_ALL)

add_library(bc1whisper SHARED bc1_whisper.cpp)
target_link_libraries(bc1whisper PRIVATE whisper log)
target_compile_options(bc1whisper PRIVATE -O3 -fvisibility=hidden)
target_link_options(bc1whisper PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
