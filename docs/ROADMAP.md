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
- [x] active yt-dlp version shown in diagnostics
- [x] stable yt-dlp update path
- [x] real-device MP3 192 acquisition
- [x] produced file plays through Media3

Exit criteria: satisfied on the target Android device.

**Gate A0: PASS**

## Gate A1 — Search Home + Android intake shell

Goal: replace the Gate A0 developer-first screen with the first native WMS product shell while hardening Android share/search intake.

- [x] app launches on **Search Home** by default
- [x] approved WMS neon-diamond emblem is used as Android visual identity
- [x] primary field accepts both search text and pasted HTTP(S) URL
- [x] provider-neutral `SearchProvider` boundary exists
- [x] Android keyword Search uses on-device yt-dlp `ytsearch`
- [x] result cards show title / author / thumbnail / duration / canonical source URL
- [x] `ACTION_SEND text/plain` from browser/app
- [x] first HTTP(S) URL extraction
- [x] reject credential-bearing/malformed URLs
- [x] shared valid URL bypasses Search Home and opens Import Sheet prefilled
- [x] pasted URL opens the same Import Sheet path
- [x] Import Sheet performs automatic Probe; normal UI has no Probe button
- [x] rights/permission confirmation remains explicit before save
- [x] existing Gate A0 MP3 acquisition path remains usable through Import Sheet
- [x] yt-dlp stable auto-check is rate-limited and best-effort
- [x] local Search is serialized behind yt-dlp update/init to avoid runtime races
- [x] Gate A0 yt-dlp controls moved to `Developer Tools`
- [x] direct/pasted URL -> Probe -> save -> Mini Player -> playback passes on the target device
- [x] Android share sheet -> WMS -> Probe -> save -> Mini Player -> playback passes on the target device
- [x] keyword Search returns working YouTube results on the target device
- [x] Search result -> `WMSに追加` -> Probe -> save -> `閉じて再生` -> Mini Player passes on the target device
- [x] long-form media is not rejected solely by a fixed duration threshold
- [x] storage exhaustion is handled as a user-facing capacity error

Search implementation history:

- the first native Search implementation targeted the WMS Media Worker,
- Android initially omitted the worker's required `Origin` header,
- after fixing `Origin`, the Worker returned HTTP 503 because `YOUTUBE_DATA_API_KEY` was not configured,
- Android therefore moved Search to an on-device yt-dlp `ytsearch` provider,
- a later real-device Python traceback exposed a race between automatic yt-dlp update and Search,
- Search/update serialization fixed the race and the final path passed on-device.

Exit: normal launch looks like WMS Media Search, shared/pasted URL reliably reaches an automatically probed Import Sheet, and keyword Search works end-to-end through local save and playback.

**Gate A1: PASS — verified on the target Android device on 2026-09-12 JST**

Post-Gate-A1 quality status:

- [x] Search Home system-bar safe area so scrolling content no longer overlaps Android status/navigation bars
- [ ] narrow-device / screen-rotation layout polish
- [ ] observe automatic yt-dlp stable refresh after the 24-hour check window
- [ ] continue adaptive icon visual parity work if needed

## Gate A2 — Managed acquisition jobs

- [x] foreground acquisition service
- [x] one active job
- [x] progress notification
- [x] cancel
- [x] temporary job directory
- [x] atomic success registration
- [x] failed/cancelled cleanup

Implementation status on Draft PR #5:

- foreground `dataSync` Service implemented
- one-active-job guard implemented
- progress / success / failure notification paths implemented
- UI and notification cancellation paths implemented
- Activity/ViewModel recreation restores managed job state
- yt-dlp Search/update/acquisition serialization implemented
- temporary per-job cache directory already cleaned in `finally`
- managed acquisition state-transition unit tests added
- real-device checklist added at `docs/GATE_A2_DEVICE_CHECK.md`
- Gradle Wrapper 8.13 and a local verification helper are included
- local `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass on Windows with JDK 17
- target-device success/playback, UI cancel, notification cancel, failure, and cleanup checks pass on Redmi 12 5G with Android 15
- saved-media playback controls include seek, elapsed/total time, 10-second skip, pause, and resume
- verified code checkpoint: `3716ab5afe6d34821dfcddcb2d3b820afe66e1e1`

**Gate A2: LOCAL PASS — verified locally and on the target Android device on 2026-09-12 JST.**

GitHub Actions and merge to `main` remain intentionally unrun.

Exit: an acquisition survives leaving the Activity and remains controllable.

## Gate A3 — Persistent Local Library

- [x] Room schema
- [x] media metadata persistence
- [x] app-specific media file lifecycle
- [x] All media list
- [x] delete from WMS
- [x] reopen app and retain library
- [x] persistent Mini Player shell above bottom navigation when media is loaded

Checkpoint on 2026-09-13 JST:

- Room 2.8.5 schema v1 and KSP-generated implementation compile successfully
- acquisition completion registers metadata before publishing success
- existing managed MP3 files are backfilled on first A3 launch
- repository registration/backfill/deletion/position/path-safety unit tests pass
- local `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass
- target device shows both existing items in Library and list playback passes
- verified implementation checkpoint: `f301580c1235d6d9ba767ada2c9d3002d168e67b`
- restart/selection/position persistence passed on device
- managed deletion and controlled missing-file recovery passed on device

**Gate A3: LOCAL PASS — verified locally and on the target Android device on 2026-09-13 JST.**

Exit: acquired media survives app restart and remains playable.

## Gate A4 — Persistent playlists

- [x] playlist create/rename/delete
- [x] ordered entries
- [x] add acquired item during import
- [x] active queue restore
- [x] previous/next

Exit: named playlists and order survive restart.

Checkpoint on 2026-09-13 JST:

- Room v2 schema and explicit v1-to-v2 migration implemented
- playlist CRUD, ordered entries, import-time destination, active-queue persistence, and Previous/Next implemented
- playlist repository ordering tests pass
- local `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass
- A4 APK installed over retained A3 app data without a migration crash
- real-device create/rename/delete, restart/order persistence, and ordered Previous/Next passed
- import destination now defaults to the active playlist, is shown explicitly before saving, and completed acquisition into that playlist passed
- final A4 APK SHA-256: `BB4ED94A75F391BC1C4A48D0A2B7F64C33938F3030B4468C31AFA36E7986F728`

**Gate A4: LOCAL PASS — verified locally and on the target Android device on 2026-09-13 JST.**

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

- [x] YouTube public — Gate A0/Gate A1 verified path
- [ ] direct public media URL provider-specific matrix entry
- [ ] TikTok public
- [ ] Instagram public/no-login-accessible
- [ ] additional sources only after explicit testing

Search support and acquisition support are separate capabilities. Never mark a provider supported merely because yt-dlp has an extractor or a Search UI entry exists.

## Gate A8 — Development distribution

- [ ] CI lint/test/build stable
- [ ] repeatable debug APK artifact
- [ ] development signing strategy via secrets if needed
- [ ] install/update test
- [ ] optional private GitHub Release APK
- [ ] documented rollback/version procedure

Actions usage policy from Gate A2 onward:

- Gate work stays in Draft PRs without automatic Android CI on every commit
- mark Ready once when a Gate is code-complete to trigger the full Android CI
- no automatic duplicate build after squash merge to `main`
- debug APK artifact retention is 1 day
- keep only the newest two WMS Android APK artifacts

No Play Store work in the current plan.

## Canonical WMS appearance IDs

Android should preserve the original WMS identifiers in its architecture even if native renderers are delivered incrementally.

Skins:

`midnight-neon`, `obsidian`, `studio-light`, `analog-warm`, `cyber-blue`, `aurora-purple`, `emerald-night`, `crimson-noir`, `sunset-glow`, `sakura`, `pixel-arcade`, `led-marquee`, `retro-terminal`, `cassette-deck`

Visualizer modes:

`rainbow-ring`, `oscilloscope`, `spectrum-city`, `neon-tunnel`, `kaleido`, `particles`, `pulse`, `orbit`, `bars`, `wave`, `emblem`, `minimal`

## Current priority

**Gate A5 — Native background playback + Now Playing.** Continue locally from the verified Gate A4 checkpoint. Do not run GitHub Actions or merge to `main`.
