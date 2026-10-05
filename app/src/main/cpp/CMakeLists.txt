cmake_minimum_required(VERSION 3.22.1)
project(droidlottery_native)

add_library(droidlottery_native SHARED native_miner.cpp)
find_library(log-lib log)
target_link_libraries(droidlottery_native ${log-lib})
