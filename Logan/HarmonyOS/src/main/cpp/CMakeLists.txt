cmake_minimum_required(VERSION 3.5.0)
project(logan_harmonyos)

set(CLOGAN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../Clogan)
set(MBEDTLS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../mbedtls)

set(CLOGAN_SOURCES
    ${CLOGAN_DIR}/clogan_core.c
    ${CLOGAN_DIR}/mmap_util.c
    ${CLOGAN_DIR}/cJSON.c
    ${CLOGAN_DIR}/construct_data.c
    ${CLOGAN_DIR}/zlib_util.c
    ${CLOGAN_DIR}/json_util.c
    ${CLOGAN_DIR}/aes_util.c
    ${CLOGAN_DIR}/directory_util.c
    ${CLOGAN_DIR}/base_util.c
    ${CLOGAN_DIR}/console_util.c
    ${MBEDTLS_DIR}/library/aes.c
    ${MBEDTLS_DIR}/library/aesni.c
    ${MBEDTLS_DIR}/library/padlock.c)

add_library(clogan_harmony STATIC ${CLOGAN_SOURCES})
target_include_directories(clogan_harmony PUBLIC ${CLOGAN_DIR} ${MBEDTLS_DIR}/include)
target_compile_options(clogan_harmony PRIVATE -std=c11 -fPIC)
target_link_libraries(clogan_harmony PUBLIC libz.so)

add_library(logan SHARED napi_init.cpp)
target_include_directories(logan PRIVATE ${CLOGAN_DIR})
target_compile_options(logan PRIVATE -std=c++17)
target_link_libraries(logan PUBLIC clogan_harmony libace_napi.z.so libz.so)
