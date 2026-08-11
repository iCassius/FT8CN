# Recovered native source and production build

This directory was recovered from Git commit `65c3857` (`Ver. 0.89`). It is
the checked-in source for the reproducible native build. The v0.93 resampler
and its vendored libsamplerate 0.2.1 implementation were reconstructed in the
native 16 KB work, and the resulting library is now built by Gradle/CMake.

`app/build.gradle` always points `externalNativeBuild.cmake.path` here and
does not package `app/libs`. Do not restore the old prebuilt libraries or add a
second JNI source directory: an APK must contain exactly one `libft8cn.so`
per ABI, produced by this CMake target.

Native contract and validation:

- The reconstructed source exports the complete 31-declaration JNI contract,
  including the six `FT8Resample` methods:
  `get8Resample32`, `get8Resample16`, `get32Resample32`, `get16Resample32`,
  `get32Resample16`, and `get16Resample16`.
- `scripts/native_baseline` compares the frozen production v2 oracle with the
  same x86_64 process ABI on a 16 KB AVD. The comparator requires clean,
  distinct builds, exact JNI contract coverage, and strict behavior equality;
  subtraction remains call-safety-only when no semantic delta is observed.
- Arm64, armeabi-v7a, and x86 are build/ELF-gate targets here; behavior
  equivalence beyond the x86_64 oracle and real-device/HIL evidence remain
  separate release evidence.

Build with NDK `28.2.13676358` and CMake `3.22.1` through the Gradle wrapper:

```text
cd ft8cn
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest `
  -Pft8cn.nativeCandidate=true
```

The `nativeCandidate` property is an oracle provenance marker; it does not
select a second library. The CMake target forces 16 KiB ELF load-segment
alignment for all four ABIs. A successful compile alone is not a release or
HIL authorization.
