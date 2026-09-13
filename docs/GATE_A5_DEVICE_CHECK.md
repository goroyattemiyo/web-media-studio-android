# Gate A5 — Background playback device checkpoint

Date: 2026-09-13 JST

Status: **LOCAL PASS**

Branch: `feat/gate-a5-background-playback`

Verified implementation checkpoint: `1cc703d09eae5dd89bf78e5ee8c1246889e3ab0c`

APK SHA-256: `80ACD8D9AF0AEE8AE2D9F2FD21B0EA88679E38F12566604597AEBE73491FE6D0`

Target device: Redmi 12 5G (`23076RA4BR`), Android 15 / API 35.

## Local verification

- [x] Gradle Wrapper 8.13 with JDK 17
- [x] `testDebugUnitTest`
- [x] `lintDebug` (0 errors)
- [x] `assembleDebug`
- [x] versionCode 9 / `0.5.0-a5-background-playback` installed over retained Gate A4 data

## Target-device verification

- [x] `MediaLibraryService` exposed an active MediaSession with a three-item playlist queue
- [x] playback continued after pressing Home
- [x] playback continued for at least ten seconds with the screen off
- [x] foreground media notification used the transport category and exposed three actions
- [x] system Pause changed the session to paused and system Play resumed it
- [x] system Next advanced queue index 0 to 1 while WMS was in the background
- [x] system Next advanced queue index 1 to 2 while the screen was off
- [x] media session reported `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC`
- [x] force-stop and Activity restart restored queue size 3, active index 2, and the saved position near 29 seconds in a paused state
- [x] the final APK reconnected its MediaController to the restored session
- [x] native Now Playing displayed WMS artwork, title/provider, and the visualizer selector
- [x] visualizer selection changed from `emblem` to `pulse` on device
- [x] the restored item resumed playback from the native controls

Gate A5 is LOCAL PASS. GitHub Actions and merge to `main` were not run.
