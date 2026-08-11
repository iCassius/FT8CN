# Recovered native source: reference only

This directory was recovered from Git commit `65c3857` (`Ver. 0.89`). It is
kept as an auditable starting point for restoring a reproducible native build;
it is **not** the source of the current v0.93 `libft8cn.so` files.

Current production APKs continue to package the four prebuilt libraries from
`app/libs`. `app/build.gradle` deliberately does not point
`externalNativeBuild.cmake.path` at this directory. Do not enable that path or
copy an output from this directory over `app/libs` until behavior-equivalence
validation has passed.

Known contract gap:

- The recovered source exports 27 JNI functions.
- The current v0.93 libraries export 33 JNI functions.
- The six missing functions are all methods of `FT8Resample`:
  `get8Resample32`, `get8Resample16`, `get32Resample32`, `get16Resample32`,
  `get32Resample16`, and `get16Resample16`.
- The v0.93 binary contains `resample_lib.cpp` plus a statically linked
  libsamplerate source tree that is absent from Git history. Debug line
  information also proves that some existing decoder/listener sources moved
  between v0.89 and v0.93, so matching only the JNI symbol names is not an
  adequate replacement gate.

For isolated compile validation, configure CMake with NDK r28 or newer and
explicitly acknowledge the incomplete reference:

```text
-DFT8CN_BUILD_INCOMPLETE_REFERENCE=ON
-DANDROID_PLATFORM=android-23
```

The CMake target forces 16 KiB ELF load-segment alignment for all four legacy
ABIs. A successful compile only demonstrates source/build viability; it does
not demonstrate FT8 encode/decode, FFT, subtraction, hashing, or resampling
equivalence with the shipped v0.93 library.
