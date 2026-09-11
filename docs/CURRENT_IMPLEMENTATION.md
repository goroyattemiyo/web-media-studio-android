# Current Implementation

Last updated: 2026-09-11 JST

## Repository state

This repository is the Android-native WMS track.

Current active branch:

`feat/gate-a0-bootstrap`

Draft PR:

`#1 feat: bootstrap Android Gate A0 local acquisition spike`

PR #1 must remain Draft and must not be merged until Gate A0 succeeds on a real device.

Current target:

**Gate A0 — prove local Android acquisition before building the full native product.**

## Implemented in the current Gate A0 line

- Kotlin + Jetpack Compose application skeleton
- replaceable `MediaAcquisitionEngine` boundary
- `youtubedl-android` + FFmpeg feasibility engine
- Media3 local preview for produced-file validation
- Android `ACTION_SEND text/plain` URL intake plus manual URL input
- URL validation and credential-bearing URL rejection
- explicit rights/permission confirmation before acquisition
- controlled MP3 192 kbps acquisition
- 30-minute Gate A0 duration guard
- one acquisition process at a time plus cancel
- sanitized/classified extractor diagnostics
- GitHub Actions build / unit test / lint / debug APK artifact pipeline
- Gate A0 diagnostic display of the active yt-dlp version
- explicit `Update yt-dlp stable` action using `updateYoutubeDL(..., UpdateChannel.STABLE)`
- update failure keeps the existing installed/bundled yt-dlp version and reports `YTDLP_UPDATE_FAILED`
- Probe/acquisition controls are disabled while yt-dlp is being updated

The app does **not** blindly update yt-dlp during startup. Runtime update is an explicit Gate A0 diagnostic action.

## Verified on real Android device

- APK installs and launches
- Acquisition Engine initializes as `READY`
- public YouTube metadata Probe succeeds and returns title/provider

## Current real-device failures / open questions

### YouTube acquisition

The previously tested public YouTube URL reached Probe PASS, but MP3 acquisition returned:

`SOURCE_FORBIDDEN`

This does not yet prove a JavaScript runtime problem. The same URL must be retested after explicit stable yt-dlp update.

### TikTok Probe

The previously tested public TikTok URL returned `PROBE_FAILED` and exposed:

`WARNING: Your yt-dlp version (2025.11.12) is older than 90 days!`

This stale active yt-dlp version is the immediate hypothesis being tested by the new stable-update action.

## Not yet verified

None of the following may be described as supported until tested successfully on the target device:

- stable yt-dlp runtime update on Android
- YouTube MP3 acquisition
- TikTok acquisition
- Instagram acquisition
- direct media URL acquisition
- produced MP3 playback through Media3 after a successful acquisition
- background playback
- persistent Room library
- persistent playlists

## Product/security boundaries

- permitted/public or otherwise authorized media only
- local device acquisition
- no account cookies in MVP
- no proxy rotation
- no DRM bypass
- no authentication/access-control bypass
- no arbitrary yt-dlp options in the user interface

## Next acceptance event

Install the CI-built APK and run the same test URLs in this order:

1. confirm displayed active yt-dlp version,
2. tap `Update yt-dlp stable`,
3. confirm the displayed version/update result,
4. YouTube: Probe -> rights confirmation -> Save MP3 192 -> Media3 Play,
5. TikTok: Probe -> rights confirmation -> Save MP3 192 -> Media3 Play where Probe succeeds.

Only if current stable yt-dlp still returns an actual JS/EJS-specific diagnostic should Gate A0 move to JavaScript-runtime work.

Do not mark Gate A0 PASS until at least one authorized/public source completes:

`Probe -> Save MP3 -> non-empty local file -> Media3 Play`
