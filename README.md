# WMS Android

Native Android track for Web Media Studio.

Primary product loop:

`Share URL -> local acquisition -> Local Library -> playlist -> screen-off playback`

The Web/PWA repository remains the browser implementation and cross-platform reference:

- https://github.com/goroyattemiyo/web-media-studio

## Current phase

**Gate A0 — prove local acquisition on a real Android device.**

Before building the full player/library UI, this repository must prove that an Android device can:

1. accept or paste a permitted public media URL,
2. probe it locally,
3. save an audio file locally without Colab or Cloud Run extraction,
4. play the produced local file through AndroidX Media3.

Gate A0 deliberately has a small diagnostic UI. Full playlists, Room persistence, `MediaLibraryService`, background playback and polished WMS UI follow only after this gate passes.

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
