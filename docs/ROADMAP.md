# WMS Android Roadmap

Last updated: 2026-09-12 JST

UI/UX source of truth:

`docs/UI_UX_DESIGN.md`

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

## Gate A1 — Search Home + Android intake shell

Goal: replace the Gate A0 developer-first screen with the first native WMS product shell while hardening Android share intake.

- [x] app launches on **Search Home** by default
- [x] approved WMS neon-diamond emblem is used as Android visual identity
- [x] primary field accepts both search text and pasted HTTP(S) URL
- [x] provider-neutral `SearchProvider` boundary exists; UI is not hard-coded around YouTube
- [x] keyword-search provider implementation can be added incrementally without changing screen structure
- [x] `ACTION_SEND text/plain` from browser/app
- [x] first HTTP(S) URL extraction
- [x] reject credential-bearing/malformed URLs
- [x] shared valid URL bypasses Search Home and opens Import Sheet prefilled
- [x] pasted URL opens the same Import Sheet path
- [x] Import Sheet performs automatic Probe; normal UI has no Probe button
- [x] rights/permission confirmation remains explicit before save
- [x] existing Gate A0 MP3 acquisition path remains usable through Import Sheet
- [x] Gate A0 yt-dlp controls move to `Developer Tools`
- [ ] keyword search returns working YouTube results on the target Android device
- [ ] search result -> `WMSに追加` -> Probe -> save -> Mini Player passes on the target Android device

Verified entry paths so far:

`direct/pasted URL -> automatic Probe -> rights confirmation -> MP3 save -> mini player -> Media3 playback` — PASS

`Android share sheet -> WMS -> automatic Probe -> rights confirmation -> MP3 save -> mini player -> Media3 playback` — PASS

Search implementation history:

- the first native search implementation targeted the WMS Media Worker,
- Android initially omitted the worker's required `Origin` header, causing rejection before provider search,
- after fixing `Origin`, real-device search reached the worker but returned HTTP 503,
- Cloud Run deployment logs confirmed `YOUTUBE_DATA_API_KEY` is not configured, so the worker intentionally disables YouTube search,
- Android now defaults to an on-device yt-dlp `ytsearch` provider instead of requiring a Cloud Run/API-key search dependency.

Exit: normal app launch looks like WMS Media Search, shared/pasted URL reliably reaches an automatically probed Import Sheet, and keyword Search works end-to-end on the target device.

**Gate A1: OPEN**

Follow-up quality checks that do not block Gate A1 closure:

- narrow-device / screen-rotation layout polish
- observe automatic yt-dlp stable refresh after the 24-hour check window

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
- [ ] persistent Mini Player shell above bottom navigation when media is loaded

Exit: acquired media survives app restart and remains playable.

## Gate A4 — Persistent playlists

- [ ] playlist create/rename/delete
- [ ] ordered entries
- [ ] add acquired item during import
- [ ] active queue restore
- [ ] previous/next

Exit: named playlists and order survive restart.

## Gate A5 — Native background playback + Now Playing

- [ ] `MediaLibraryService`
- [ ] `MediaSession`
- [ ] ExoPlayer service ownership
- [ ] audio focus
- [ ] notification/system controls
- [ ] screen-off playback
- [ ] screen-off Next
- [ ] queue/position restore
- [ ] full native Now Playing screen
- [ ] WMS visualizer renderer boundary
- [ ] lightweight visual modes (`emblem`, `pulse`, `orbit`, `bars`, `wave`, `minimal`)
- [ ] audio-analysis data-source boundary for audio-reactive visualizers

Exit: target Android device continues local playlist playback with screen locked and system Play/Pause/Next works, with the WMS player identity in place.

## Gate A6 — Production-quality UX + WMS appearance parity

- [ ] production Search Home polish
- [ ] real provider result cards with canonical URLs
- [ ] simple `Audio / Video` choice in Import Sheet
- [ ] MP3 192 default
- [ ] M4A where stable
- [ ] video acquisition where stable
- [ ] expandable advanced options
- [ ] clear unsupported/private/login-required states
- [ ] WMS skin system with canonical Web/PWA theme IDs
- [ ] Appearance screen with visual preview tiles
- [ ] selected skin persisted in DataStore
- [ ] selected visualizer persisted in DataStore
- [ ] audio-reactive visualizers ported where technically stable
- [ ] reduced-motion / `minimal` option

Exit: normal use looks and feels like native WMS rather than a developer diagnostic app, while preserving the Web/PWA visual identity.

## Gate A7 — Provider matrix

For each candidate provider, record search/probe/acquire/playback results on the target device where applicable.

- [x] YouTube public — Gate A0 feasibility sample PASS
- [ ] direct public media URL
- [ ] TikTok public
- [ ] Instagram public/no-login-accessible
- [ ] additional sources only after explicit testing

Search support and acquisition support are separate capabilities. Never mark a provider supported merely because yt-dlp has an extractor or a search UI entry exists.

## Gate A8 — Development distribution

- [ ] CI lint/test/build stable
- [ ] repeatable debug APK artifact
- [ ] development signing strategy via secrets if needed
- [ ] install/update test
- [ ] optional private GitHub Release APK
- [ ] documented rollback/version procedure

No Play Store work in the current plan.

## Canonical WMS appearance IDs

Android should preserve the original WMS identifiers in its architecture even if native renderers are delivered incrementally.

Skins:

`midnight-neon`, `obsidian`, `studio-light`, `analog-warm`, `cyber-blue`, `aurora-purple`, `emerald-night`, `crimson-noir`, `sunset-glow`, `sakura`, `pixel-arcade`, `led-marquee`, `retro-terminal`, `cassette-deck`

Visualizer modes:

`rainbow-ring`, `oscilloscope`, `spectrum-city`, `neon-tunnel`, `kaleido`, `particles`, `pulse`, `orbit`, `bars`, `wave`, `emblem`, `minimal`

## Current priority

**Gate A1.** Verify the new on-device yt-dlp keyword Search path end-to-end on the target Android device before moving to Gate A2/A3.
