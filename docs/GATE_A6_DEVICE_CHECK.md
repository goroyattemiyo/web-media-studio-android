# Gate A6 — Production UX and appearance device checkpoint

Date: 2026-09-13 JST

Status: **LOCAL PASS**

Branch: `feat/gate-a6-production-ux`

Verified implementation checkpoint: `901aca8e3fec0fc37639819ad1f4ce6373a98b99`

APK SHA-256: `9CD0E8BD47C6AE7E87A1316AD8BC4AA5BDF7080E6D1E33A76A825BCEF7A8DD52`

Target device: Redmi 12 5G (`23076RA4BR`), Android 15 / API 35.

## Local verification

- [x] Gradle Wrapper 8.13 with JDK 17
- [x] `testDebugUnitTest`
- [x] `lintDebug` (0 errors)
- [x] `assembleDebug`
- [x] versionCode 10 / `0.6.0-a6-production-ux` installed over retained Gate A5 data

## Target-device verification

- [x] Search Home displays the native WMS header, one accurate YouTube endpoint label, recent Library media, and compact Mini Player
- [x] Import displays Audio/Video choices, defaults to MP3 192 kbps, and keeps M4A in expandable controlled options
- [x] Appearance lists all 14 canonical skin IDs and canonical visualizer choices
- [x] `studio-light`, `rainbow-ring`, and reduced-motion selections were confirmed in Preferences DataStore after Activity restart
- [x] existing long-form audio played through the PCM-enabled audio sink without playback or AudioSink errors
- [x] authorized public media acquired successfully as M4A into the managed media directory
- [x] the M4A Library item played through the WMS MediaSession with `USAGE_MEDIA`
- [x] authorized public media acquired successfully as a non-empty MP4 (15,109,339 bytes)
- [x] the MP4 registered as `video`, opened native Now Playing, and played through a live `SurfaceView`
- [x] Android's MPEG4 extractor opened the exact managed MP4 and no playback/video-renderer fatal error was reported

Gate A6 is LOCAL PASS. GitHub Actions and merge to `main` were not run.
