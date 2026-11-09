# DVINE's on-device language model: llama.cpp (MIT, vendored under llama.cpp/) on the CPU, plus the Java bridge.
#
cmake_minimum_required(VERSION 3.22.1)
project(dvine_llm C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp reads these at its own project() (this file's project() is too late for them), so they have to be
# cache variables set before add_subdirectory(llama.cpp). Only the library: no tools, tests, server, downloads or
# OpenMP, so it also builds offline on the phone.
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_HTML OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SQLITE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
# Arm's KleidiAI matrix kernels (MIT/Apache-2.0, vendored under third_party/kleidiai, v1.32.0): picked at run time by
# what the phone's CPU has (dot product, int8 matrix multiply, SME), so newer phones get their faster instructions
# while this library still runs on every arm64 phone with dot product. Checked in an emulator on a Cortex-A76 and on a
# CPU with every feature: the same answers as without it.
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE) # the fast kernels pin the cores and the phone hums; CPU dot-product is enough
set(FETCHCONTENT_SOURCE_DIR_KLEIDIAI "${CMAKE_CURRENT_SOURCE_DIR}/third_party/kleidiai" CACHE PATH "" FORCE)
set(KLEIDIAI_BUILD_EXPERIMENTAL_OPS OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# every ggml backend except CPU is off for the phone: no CUDA, HIP, Metal, Vulkan, SYCL, CANN, OpenCL, OpenVINO,
# WebGPU, Vulkan compute, BLAS, RPC or dynamic backend loading — the CPU is all this app uses, and the rest of the
# tree (vendor/, cmake/, include/llama/, samples/) is not compiled at all.
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_HIP OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_SYCL OFF CACHE BOOL "" FORCE)
set(GGML_CANN OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_OPENVINO OFF CACHE BOOL "" FORCE)
set(GGML_WEBGPU OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    # dot-product instructions (2018+ phones); the app checks the CPU for them before loading this library
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
    set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
endif()

# llama.cpp builds its own vendored ggml (llama.cpp/ggml) with the flags above, as it finds no ggml target.
add_subdirectory(llama.cpp)

add_library(dvine_llm SHARED dvine_llm.cpp dvine_llm_jni.cpp)
target_link_libraries(dvine_llm PRIVATE llama)
if(ANDROID)
    target_link_libraries(dvine_llm PRIVATE android log)
endif()
