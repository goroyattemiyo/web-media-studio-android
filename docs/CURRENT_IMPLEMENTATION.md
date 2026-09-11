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
- explicit `yt-dlp Diagnostics` action using `--verbose --simulate --no-playlist`
- diagnostic summary extracts only relevant runtime/provider lines such as `JS runtimes`, `PO Token Providers`, `JS Challenge Providers`, player-client hints and HTTP 403 lines
- diagnostic output is sanitized before display; URLs and app/storage paths are not exposed verbatim

The app does **not** blindly update yt-dlp during startup. Runtime update is an explicit Gate A0 diagnostic action.

## Verified on real Android device

- APK installs and launches
- Acquisition Engine initializes as `READY`
- public YouTube metadata Probe succeeds and returns title/provider

## Current real-device failures / open questions

### YouTube acquisition

The tested public YouTube URL reaches Probe PASS but MP3 acquisition still returns:

`SOURCE_FORBIDDEN`

The failure persisted after moving to the current stable-update path, so stale yt-dlp alone is not considered sufficient explanation.

Current next diagnostic target is to determine from yt-dlp verbose output whether the Android runtime has:

- a usable JS runtime / QuickJS,
- a PO Token provider,
- a usable JS Challenge provider,
- player-client-related warnings associated with HTTP 403.

Do not add a new QuickJS integration until diagnostics show the currently packaged QuickJS path is unavailable or unusable.

### TikTok Probe

The earlier public TikTok URL returned `PROBE_FAILED` and exposed a stale yt-dlp warning (`2025.11.12`). The same URL should be retested after the explicit stable update.

## Not yet verified

None of the following may be described as supported until tested successfully on the target device:

- stable yt-dlp runtime update on Android
- packaged QuickJS availability/use by current yt-dlp
- PO Token provider availability
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

Install the CI-built APK and run the same YouTube URL in this order:

1. confirm displayed active yt-dlp version,
2. tap `Update yt-dlp stable`,
3. tap `yt-dlp Diagnostics`,
4. capture the displayed lines for `JS runtimes`, `PO Token Providers`, `JS Challenge Providers` and any player-client / HTTP 403 warning,
5. Probe,
6. rights confirmation,
7. Save MP3 192,
8. if saved, Media3 Play.

Then repeat diagnostics/Probe/acquisition with the same TikTok URL.

Only after the runtime/provider diagnostics are known should Gate A0 choose between:

- fixing packaged QuickJS/runtime wiring,
- adding a PO Token provider path,
- adjusting a provider/player-client integration,
- or replacing the acquisition engine implementation.

Do not mark Gate A0 PASS until at least one authorized/public source completes:

`Probe -> Save MP3 -> non-empty local file -> Media3 Play`
