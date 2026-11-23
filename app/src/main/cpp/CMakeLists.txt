cmake_minimum_required(VERSION 3.22.1)
project(nice_tagfix C)

add_library(tagfix SHARED tagfix.c)
target_compile_options(tagfix PRIVATE -Wall -Wextra -Werror)
target_link_options(tagfix PRIVATE -Wl,--no-undefined)
