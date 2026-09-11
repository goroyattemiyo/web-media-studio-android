# WMS Android Roadmap

Last updated: 2026-09-11 JST

## Gate A0 — Local acquisition feasibility

Goal: prove the riskiest assumption before building the full app.

- [x] dedicated Android repository
- [x] approved architecture and gate plan
- [x] minimal Kotlin/Compose project bootstrap
- [x] `MediaAcquisitionEngine` abstraction
- [x] youtubedl-android + FFmpeg candidate integration
- [x] CI debug APK artifact
- [x] real-device runtime initialization
- [x] real-device Probe for one permitted public YouTube source
- [x] active yt-dlp version shown in Gate A0 diagnostics
- [x] explicit stable yt-dlp update action using `updateYoutubeDL(..., STABLE)`
- [x] real-device stable yt-dlp update verification
- [x] real-device MP3 192 acquisition for one permitted public source
- [x] produced file plays through Media3

Exit criteria: satisfied on the target Android device with a permitted/public YouTube sample.

Observed passing path:

`Update yt-dlp stable -> Probe -> rights confirmation -> Save MP3 192 -> non-empty local file -> Media3 Play`

Active yt-dlp during the successful run: `2026.08.19`.

**Gate A0: PASS**

Provider test order after basic runtime proof:

1. permitted public YouTube sample — feasibility PASS
2. direct public media URL
3. public TikTok sample
4. public Instagram Reel/post accessible without login

One provider success was enough to exit A0; broad provider support remains later work.

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

- [x] YouTube public — Gate A0 feasibility sample PASS
- [ ] TikTok public
- [ ] Instagram public/no-login-accessible
- [ ] direct public media URL
- [ ] additional sources only after explicit testing

Never mark a provider broadly supported based only on one sample or extractor presence.

## Gate A8 — Development distribution

- [ ] CI lint/test/build stable
- [ ] repeatable debug APK artifact
- [ ] development signing strategy via secrets if needed
- [ ] install/update test
- [ ] optional private GitHub Release APK
- [ ] documented rollback/version procedure

No Play Store work in the current plan.

## Current priority

Gate A0 feasibility is proven. After PR #1 is merged, proceed to productizing the local acquisition path, starting with share intake hardening and managed acquisition jobs before persistent Library/Playlist/background playback.
