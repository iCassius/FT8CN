# Releases

## v0.93.004 - Cloudlog/Wavelog and stability update

This release packages the latest runtime hardening and logbook sync improvements for FT8CN.

### Highlights

- Added combined Cloudlog/Wavelog upload support with automatic endpoint selection.
- Updated the Cloudlog test flow to validate logbook connectivity without writing a dummy QSO.
- Hardened broadcast receiver registration for Android 13+ compatibility.
- Preserved the existing exit flow while silencing the lint false positive in `onBackPressed`.
- Switched GitHub release workflows to build and publish release APKs instead of debug APKs.

### Notes

- Known issues that are intentionally deferred are tracked in `KNOWN_ISSUES.md`.
- Release APKs are now generated from the `release` build type and tagged GitHub release workflow.

## v0.93.001 draft - Android 14 compatibility and performance modernization

This release is a major maintenance update for FT8CN. It focuses on runtime stability, lower power consumption, cleaner Android lifecycle handling, and improved radio compatibility while preserving the existing FT8 workflow.

### Highlights

- Improved Android 14 compatibility and project build configuration.
- Added centralized background execution through `AppExecutors` to avoid uncontrolled thread growth.
- Reworked `MainViewModel` lifecycle cleanup so timers, recording, FT8 listening, and the embedded HTTP server are stopped when the ViewModel is cleared.
- Removed forced `System.exit(0)` shutdown and switched to a cleaner app exit path.
- Reduced CPU and battery pressure by replacing high-frequency timer polling with scheduled FT8 cycle timing.
- Optimized waterfall rendering with double buffering and redraw throttling around 5-10 FPS.
- Reduced audio recording allocation pressure by reusing float buffers.
- Migrated database and callsign operations away from deprecated `AsyncTask` patterns.
- Improved Android data structures in hot paths with `SparseIntArray`.
- Fixed XML string formatting issues by using explicit positional placeholders where needed.
- Added connection, transmission, and QSO success feedback bubbles.
- Added or restored radio model support for YAESU FT-710 and YAESU FTX-1.

### Build and Compatibility

- Android Gradle Plugin configuration was updated for modern build behavior.
- `BuildConfig` generation is explicitly enabled because the project uses `buildConfigField`.
- Gradle JVM settings were tuned for better build throughput.

### Versioning

- Version numbering starts at `0.93.001`.
- The format is `major.minor.build`.
- For each new release on the same base version, increment the three-digit build number by 1: `0.93.002`, `0.93.003`, etc.
- `versionCode` follows the same sequence numerically; `0.93.001` is represented as `93001`.

### Notes

- Release tags should match the Android `versionName`, for example `v0.93.001`.
- See `ft8cn/OPTIMIZATION_GUIDE.md` for the detailed technical change list.
- See `ft8cn/AGP_UPGRADE_FIX.md` for the Android Gradle Plugin upgrade note.
