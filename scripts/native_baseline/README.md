# FT8CN native behavior baseline

This directory provides a development gate for replacing the v0.93 prebuilt
`libft8cn.so`. It does not by itself prove a release is ready.

## Trust model

- `oracles/x86_64-production-v093005.json` and the PCM fixture were captured
  from the approved v0.93 production prebuilt library on a 16 KB x86_64 AVD.
- The decoder consumes the frozen lossless PCM fixture from the Android test
  APK. It never uses the candidate library's synthesizer as its only input.
- A normal comparison requires a clean candidate commit, a different native
  library SHA-256, the same application/version/build variant, the same real
  process ABI, and `PAGE_SIZE=16384`.
- Device name, SDK level, and build fingerprint are recorded under
  `non_authoritative_environment` and are not compared.
- This is currently an **x86_64 development gate only**. Arm64 behavior and
  final real-device/HIL evidence remain release blockers.

The subtraction section records `subtract_semantic_effect_observed`. When the
approved production library produces no observable before/after delta, the
oracle says `call-safety-only-production-showed-no-observable-delta`; that is
only call/lifetime coverage and must never be described as semantic coverage.

## Capture a clean candidate

Run from a clean candidate commit whose Gradle build actually packages the
rebuilt native library:

```powershell
pwsh -NoLogo -NoProfile -File scripts/native_baseline/capture_oracle.ps1 `
  -Serial emulator-5554 `
  -OutputPath scripts/native_baseline/candidates/x86_64-candidate.json
```

The script builds both APKs, installs those exact files on the selected
emulator, and cross-checks the current Git commit/dirty state, page size,
process ABI, both APK hashes, and the selected `libft8cn.so` hash. A dirty
capture is written for diagnostics but the comparator rejects it.

`-SkipBuild` is exceptional. It is rejected unless all three expected hashes
are supplied explicitly:

```powershell
-SkipBuild `
-ExpectedTargetApkSha256 <64-hex> `
-ExpectedTestApkSha256 <64-hex> `
-ExpectedNativeLibrarySha256 <64-hex>
```

## Compare

```powershell
python scripts/native_baseline/compare_oracle.py `
  scripts/native_baseline/oracles/x86_64-production-v093005.json `
  scripts/native_baseline/candidates/x86_64-candidate.json
```

`--allow-same-build` exists only for oracle self-checks. `--allow-non-16kb`
exists only for exploratory 4 KB analysis. Neither option is valid for native
16 KB candidate acceptance.

## Fixture custody

`ft8cn/app/src/androidTest/assets/nativebaseline/native-decoder-mixed-v1.pcm16le`
is immutable test input. Do not regenerate it while evaluating a candidate.
The maintainer-only instrumentation method
`captureProductionDecoderFixture` requires
`allow_production_fixture_capture=YES` and is reserved for a separately
approved production-baseline refresh. Any refresh requires a new input revision,
new SHA-256 constant, a new oracle capture, and review of the provenance.

## Local checks

```powershell
python -m unittest discover -s scripts/native_baseline -p 'test*.py' -v
cd ft8cn
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --no-daemon
```
