# WMS Android Roadmap

Last updated: 2026-09-11 JST

## Gate A0 — Local acquisition feasibility

Goal: prove the riskiest assumption before building the full app.

- [x] dedicated Android repository
- [x] approved architecture and gate plan
- [~] minimal Kotlin/Compose project bootstrap
- [~] `MediaAcquisitionEngine` abstraction
- [~] youtubedl-android + FFmpeg candidate integration
- [ ] CI debug APK artifact
- [ ] real-device runtime initialization
- [ ] real-device Probe for one permitted public source
- [ ] real-device MP3 192 acquisition for one permitted public source
- [ ] produced file plays through Media3

Exit criteria: all final four real-device items PASS.

Provider test order after basic runtime proof:

1. permitted public YouTube sample
2. direct public media URL
3. public TikTok sample
4. public Instagram Reel/post accessible without login

One provider success is enough to exit A0; broad provider support remains later work.

## Gate A1 — Android share intake

- [ ] `ACTION_SEND text/plain` from browser/app
- [ ] first HTTP(S) URL extraction
- [ ] reject credential-bearing/malformed URLs
- [ ] Import sheet opens with source prefilled
- [ ] manual paste remains available

Exit: share from another Android app reaches WMS reliably.

## Gate A2 — Managed acquisition jobs

- [ ] foreground acquisition service
- [ ] one active job
- [ ] progress notification
- [ ] cancel
- [ ] temporary job directory
- [ ] atomic success registration
- [ ] failed/cancelled cleanup

Exit: an acquisition survives leaving the Activity and remains controllable.

## Gate A3 — Persistent Local Library

- [ ] Room schema
- [ ] media metadata persistence
- [ ] app-specific media file lifecycle
- [ ] All media list
- [ ] delete from WMS
- [ ] reopen app and retain library

Exit: acquired media survives app restart and remains playable.

## Gate A4 — Persistent playlists

- [ ] playlist create/rename/delete
- [ ] ordered entries
- [ ] add acquired item during import
- [ ] active queue restore
- [ ] previous/next

Exit: named playlists and order survive restart.

## Gate A5 — Native background playback

- [ ] `MediaLibraryService`
- [ ] `MediaSession`
- [ ] ExoPlayer service ownership
- [ ] audio focus
- [ ] notification/system controls
- [ ] screen-off playback
- [ ] screen-off Next
- [ ] queue/position restore

Exit: target Android device continues local playlist playback with screen locked and system Play/Pause/Next works.

## Gate A6 — Production-quality import UX

- [ ] Share Import sheet
- [ ] simple `Audio / Video` choice
- [ ] MP3 192 default
- [ ] M4A where stable
- [ ] video acquisition where stable
- [ ] expandable advanced options
- [ ] clear unsupported/private/login-required states

Exit: normal use does not look like a developer diagnostic screen.

## Gate A7 — Provider matrix

For each candidate provider, record probe/acquire/playback results on the target device.

- [ ] YouTube public
- [ ] TikTok public
- [ ] Instagram public/no-login-accessible
- [ ] direct public media URL
- [ ] additional sources only after explicit testing

Never mark a provider supported based only on yt-dlp extractor presence.

## Gate A8 — Development distribution

- [ ] CI lint/test/build stable
- [ ] repeatable debug APK artifact
- [ ] development signing strategy via secrets if needed
- [ ] install/update test
- [ ] optional private GitHub Release APK
- [ ] documented rollback/version procedure

No Play Store work in the current plan.

## Deferred until core loop is proven

- Chrome extension for desktop handoff
- Windows native WMS
- recorder parity with Web/PWA
- EQ/visualizers
- rich metadata/artwork editing
- simultaneous/queued acquisition jobs
- provider-specific fallback chains
- cloud extraction as normal Android path

## Current priority

**Gate A0 only.** Build the smallest APK that can prove local source acquisition and Media3 playback on a real Android device.
