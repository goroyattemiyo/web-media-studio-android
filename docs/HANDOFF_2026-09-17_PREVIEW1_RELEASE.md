# WMS Android Handoff — Preview 1 Release

Date: 2026-09-17
Status: Development paused after Developer Preview 1 release

## Resume point

Repository: `goroyattemiyo/web-media-studio-android`

Active development branch:
`plan/product-ui-redesign`

Checkpoint commit before this handoff doc:
`de53418eb7abacde670f2e111c2601303f1aa1cc`

Tag:
`android-v0.9.0-preview.1`

Release:
https://github.com/goroyattemiyo/web-media-studio-android/releases/tag/android-v0.9.0-preview.1

Release status:
- Public repository
- Published
- Pre-release: ON
- Developer Preview
- Debug-signed APK
- Google Play distribution: not used

## Released build

Version: `0.9.0-preview.1`

Version code: `13`

APK:
`wms-android-0.9.0-preview.1-v13-de53418eb7ab-debug.apk`

SHA-256:
`8F2861C2BAF913B411987A8E008902BE667C337AE9C1A7FA37311C0C2B8627D5`

Release tag points to:
`de53418eb7abacde670f2e111c2601303f1aa1cc`

## Final local verification

Executed with:

```powershell
.\scripts\local-verify.ps1 -Install
```

Result:
- BUILD SUCCESSFUL
- Local verification PASS
- unit tests PASS
- lint PASS
- debug APK build PASS
- APK generated
- install/update verification used during development

GitHub Actions were intentionally not used for this release because of the Actions-saving policy.

## Product state at pause

Primary navigation:
- Home
- Library
- Player

Playback:
- Media3 / ExoPlayer / MediaSession based
- Library continuous queue
- Previous / Next
- Playlist playback
- Mini Player / Full Player
- background playback
- screen-off playback
- playback speed
- Shuffle / Repeat
- A-B Loop

Library / media:
- device audio/video import
- acquired media in local library
- share
- delete
- `元動画を開く` for stored HTTP/HTTPS original source URLs

Player synchronization fixes completed:
- Now Playing title follows the actual Media3 current item
- MP3 -> MP3 selection updates displayed title correctly
- MP3 -> video switches Player from visualizer to video surface
- video -> MP3 switches back to visualizer
- Previous / Next keep media title and media mode synchronized

Appearance:
- skins
- backgrounds
- custom background image
- background image removal
- visualizer selection
- reduced motion

Startup experience:
- startup sound was removed / disabled after device testing
- `NOW LOADING` text was removed
- ring / particle effects were removed
- OS launch background is plain dark, to avoid showing the logo twice
- Compose startup experience shows the WMS logo once with a short fade/scale entrance
- user accepted the current fade as good enough because startup itself is fast

Visualizer:
- FFT-based audio-reactive visualizers are implemented
- Hyper Tunnel and other modes remain available
- current product direction favors functionality over further startup animation polishing

## Provider / acquisition status

Verified on device:
- YouTube public
- public / no-login Instagram Reel

Direct public media URL path exists.

TikTok remains unverified and must not be advertised as supported.

Out of scope:
- DRM bypass
- private/login-required access bypass
- cookie theft / credential automation
- access-control circumvention

## Release notes / distribution notes

Repository is currently public, so users do not need repository access approval to view the Release page.

APK installation is direct from GitHub Release. Android may ask for permission to install unknown apps from the browser / GitHub app.

The current release is a Developer Preview and should not be described as a stable production release.

## Important repository caution

Do not assume `main` contains all current work.

The released product state is represented by:
- branch `plan/product-ui-redesign`
- tag `android-v0.9.0-preview.1`
- commit `de53418eb7abacde670f2e111c2601303f1aa1cc`

Before resuming development, inspect branch/tag state before merging anything to `main`.

## Development policy to preserve

GitHub Actions saving policy remains in force:
- Draft PR during active development
- avoid heavy CI on every commit
- run full CI only when a gate / feature is ready for final review
- avoid duplicate full CI after merge
- use `workflow_dispatch` only when needed
- do static review before CI
- do not use CI as the first debugging tool

When working interactively with ChatGPT:
- keep changes small
- one coherent edit at a time
- do not chain long polling / CI / log investigation in one turn
- return quickly so the chat stays responsive

## Recommended next version

Resume future work as:
`0.9.0-preview.2`

Do not modify Preview 1 assets/tag unless fixing a release-critical mistake.

Likely future areas:
- broader public provider coverage under the safe provider-neutral acquisition design
- further product UI simplification
- polish around visualizers and library usability
- formal release signing when approaching stable distribution
- decide whether/when to merge the released branch into `main`

## New chat restart instruction

In a new ChatGPT chat, use:

> `goroyattemiyo/web-media-studio-android の続きです。まず docs/LATEST_HANDOFF.md と docs/HANDOFF_2026-09-17_PREVIEW1_RELEASE.md を読んで、Preview 1 リリース済みの状態から再開してください。GitHub Actions節約ポリシーを維持し、mainへ勝手にmergeしないでください。`

Current intention: development is paused here. No further implementation should be started until the user explicitly resumes it.
