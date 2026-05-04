cmake_minimum_required(VERSION 3.22.1)
project(gemma_llama)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_CPP_DIR "" CACHE PATH "Path to a local llama.cpp checkout")

if (NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp checkout not found at '${LLAMA_CPP_DIR}'. Clone it next to this repo or pass -DLLAMA_CPP_DIR=/path/to/llama.cpp")
endif()

set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "Disable Vulkan for the clean emulator smoke-test branch" FORCE)

add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_BINARY_DIR}/llama.cpp")

add_library(gemma-llama SHARED llama_bridge.cpp)

target_include_directories(gemma-llama PRIVATE
    "${LLAMA_CPP_DIR}/include"
    "${LLAMA_CPP_DIR}/ggml/include"
)

target_link_libraries(gemma-llama
    llama
    android
    log
)
