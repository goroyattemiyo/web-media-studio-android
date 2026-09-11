# Current Implementation

Last updated: 2026-09-11 JST

## Repository state

This repository is the new Android-native WMS track.

Current active branch during bootstrap:

`feat/gate-a0-bootstrap`

Current target:

**Gate A0 — prove local Android acquisition before building the full native product.**

## Implemented/committed in the current bootstrap line

- repository initialized
- Android source-of-truth docs
- Gate A0-first development rule
- Kotlin/Compose application skeleton being added
- replaceable `MediaAcquisitionEngine` boundary being added
- youtubedl-android + FFmpeg candidate being added only as the first feasibility implementation
- Media3 local preview being added for produced-file validation
- GitHub Actions debug APK build target being added

## Not yet verified

None of the following may be described as supported until tested:

- youtubedl-android runtime initialization on the target device
- YouTube acquisition on Android
- TikTok acquisition on Android
- Instagram acquisition on Android
- direct media URL acquisition on Android
- MP3 extraction success
- background playback
- Android share-intent flow
- Room persistence
- persistent playlists

## Known extractor risk

The first candidate library currently advertises youtubedl-android `0.18.1`, while its public README still mentions a bundled Python 3.8 runtime. Modern yt-dlp versions require a newer Python runtime, so the application must not blindly update yt-dlp at startup.

Gate A0 intentionally answers this question with a real APK/runtime test. If the packaged runtime is not viable, the approved architecture keeps the rest of WMS unchanged and replaces only the `MediaAcquisitionEngine` implementation.

## Product/security boundaries

- permitted media only
- local device acquisition
- no account cookies in MVP
- no proxy rotation
- no DRM bypass
- no authentication/access-control bypass
- no arbitrary yt-dlp options in the user interface

## Next acceptance event

CI must first produce an installable debug APK. Then the target Android device must run the A0 sequence:

`URL -> Probe -> rights confirmation -> Save MP3 192 -> Play`

Do not mark Gate A0 PASS until that real-device sequence succeeds for at least one permitted public source.
