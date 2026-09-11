# Current Implementation

Last updated: 2026-09-11 JST

## Repository state

This repository is the Android-native WMS track.

Current active branch:

`feat/gate-a0-bootstrap`

Draft PR:

`#1 feat: bootstrap Android Gate A0 local acquisition spike`

Gate A0 has now passed on a real Android device for a permitted/public YouTube sample. PR #1 may proceed toward merge after the latest CI for the branch is green.

Current target:

**Gate A0 — PASS. Prepare transition to Gate A1+ product work after merge.**

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
- active yt-dlp version display
- explicit `Update yt-dlp stable` action using `updateYoutubeDL(..., UpdateChannel.STABLE)`
- update failure fallback to existing bundled/installed yt-dlp
- Probe/acquisition controls disabled while yt-dlp is being updated
- explicit `yt-dlp Diagnostics` action using `--verbose --simulate --no-playlist`
- sanitized extraction of relevant runtime/provider lines

The app does **not** blindly update yt-dlp during startup. Runtime update remains an explicit diagnostic/user action.

## Verified on real Android device

The following path has passed end-to-end on the target device:

1. APK installs and launches.
2. Acquisition Engine initializes as `READY`.
3. `Update yt-dlp stable` succeeds.
4. Active yt-dlp version displays as `2026.08.19`.
5. Public YouTube metadata Probe succeeds and returns title/provider.
6. Rights/permission confirmation can be enabled.
7. `Save audio / MP3 192 kbps` completes successfully.
8. A non-empty MP3 file is written under app-specific Android storage.
9. Media3 local preview opens the produced file.
10. Playback succeeds; the UI shows `Pause` while audio is playing.

Observed successful sample title:

`Michael Jackson - Beat It (Official 4K Video)`

This establishes the Gate A0 acceptance path:

`Probe -> Save MP3 -> non-empty local file -> Media3 Play`

## Gate A0 status

**PASS**

The previous `SOURCE_FORBIDDEN` failure did not persist after updating the active yt-dlp version to the current stable path. Therefore stale yt-dlp was materially involved in the earlier acquisition failure for the tested sample.

This result does not prove universal provider support. YouTube/TikTok/Instagram/direct-media support must still be recorded provider-by-provider later.

## Still not verified

Do not describe these as supported yet:

- TikTok acquisition
- Instagram acquisition
- direct media URL acquisition
- packaged QuickJS behavior across all providers
- PO Token provider availability/use
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

## Next step

After latest branch CI passes, merge PR #1 with squash and move to the next Android product gates:

- Gate A1: share intake hardening
- Gate A2: managed acquisition job/service
- Gate A3: persistent Local Library
- Gate A4: persistent playlists
- Gate A5: MediaLibraryService/background/screen-off playback

Provider matrix expansion remains separate from the fact that Gate A0 feasibility is now proven.
