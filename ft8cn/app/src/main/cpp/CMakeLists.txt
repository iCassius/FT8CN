
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.18.1)

# Declares and names the project.

project("ft8cn")

# This tree was recovered from the v0.89 history and does not contain the six
# FT8Resample JNI entry points shipped by the current v0.93 binary. Keep it
# opt-in until the native behavior-equivalence gate is complete.
option(FT8CN_BUILD_INCOMPLETE_REFERENCE
       "Build the recovered, behavior-unverified v0.89 native reference"
       OFF)
if(NOT FT8CN_BUILD_INCOMPLETE_REFERENCE)
    message(FATAL_ERROR
            "Recovered native source is an incomplete reference. "
            "Pass -DFT8CN_BUILD_INCOMPLETE_REFERENCE=ON only for isolated validation.")
endif()

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             ft8cn

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).

        generate_ft8.cpp
        ft8Listener.cpp
        ft8Spectrum.cpp
        fft/kiss_fftr.c
        fft/kiss_fft.c

        ft8/decode.c
        ft8/crc.c
        ft8/constants.c
        ft8/ldpc.c
        ft8/unpack.c
        ft8/text.c
        ft8/hash22.c
        ft8/encode.c
        ft8/pack.c
        monitor_opr.c
        ft8Decoder.c
        ft8Encoder.c
        spectrum_data.c
              )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       ft8cn

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )

# NDK r28 defaults to flexible page sizes for 64-bit targets, but its 32-bit
# targets still link with 4 KiB PT_LOAD alignment. Keep every reference ABI at
# the same 16 KiB release gate so future packaging cannot silently regress.
target_link_options(ft8cn PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384")

# The Android toolchain injects debug information even for Release. Removing it
# here keeps isolated reference outputs independent of the temporary build path;
# APK packaging can still retain symbols separately once this source is trusted.
target_compile_options(ft8cn PRIVATE "$<$<CONFIG:Release>:-g0>")
