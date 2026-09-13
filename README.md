# WMS Android

Native Android track for Web Media Studio.

Primary product loop:

`Share URL -> local acquisition -> Local Library -> playlist -> screen-off playback`

The Web/PWA repository remains the browser implementation and cross-platform reference:

- https://github.com/goroyattemiyo/web-media-studio

## Current phase

Gates A0–A7 are LOCAL PASS. The current branch is completing Gate A8 development
distribution: repeatable local verification, versioned private debug APKs, update
installation checks, and documented signing/rollback boundaries.

Run the complete local verification and create a checksum-paired APK in ignored `dist/`:

```powershell
.\scripts\local-verify.ps1
```

See `docs/CURRENT_IMPLEMENTATION.md` and `docs/DEVELOPMENT_DISTRIBUTION.md` for the
verified feature set and distribution procedure.

## Stack target

- Kotlin
- Jetpack Compose
- AndroidX Media3 / ExoPlayer
- Room in the library phase
- replaceable `MediaAcquisitionEngine`
- first A0 extractor spike: `youtubedl-android` + its FFmpeg module

## Extractor compatibility note

The current `youtubedl-android` README advertises `0.18.1`, but also still documents a bundled Python 3.8 runtime. Modern yt-dlp releases require a newer Python runtime. Therefore Gate A0 does **not** blindly update yt-dlp on startup. We first test the packaged runtime as-is. If the runtime/extractor boundary is not viable on the target device, the app keeps the same `MediaAcquisitionEngine` interface and swaps the implementation rather than adding provider-specific bypasses.

## Distribution scope

Development/private APK distribution only. No Play Store release is planned in the current phase.

## Safety/product boundary

- only media the user owns or is otherwise permitted to save
- no account-cookie import in the MVP
- no proxy rotation
- no DRM bypass
- no authentication/access-control bypass
- no arbitrary yt-dlp flags exposed in the UI
- external URLs and metadata are untrusted input

See `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, and `docs/CURRENT_IMPLEMENTATION.md`.
