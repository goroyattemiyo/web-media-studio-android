# WMS Android — Product UI redesign progress

Last updated: 2026-09-15 JST

Branch: `plan/product-ui-redesign`

Status: **IN PROGRESS**

Canonical specification: `docs/CODEX_PRODUCT_UI_REDESIGN.md`

## Current UI structure audit

The redesign started from a 1,982-line `MainActivity.kt` that combined:

- Activity/share-intent handling and ViewModel wiring,
- root navigation and transient screen state,
- Search Home and search result cards,
- Import Sheet and developer diagnostics,
- Library and Playlist screens/dialogs,
- Mini Player state synchronization and controls,
- full Now Playing audio/video rendering,
- Appearance and skin previews,
- theme construction, navigation, and formatting helpers.

Playback remains correctly owned by the existing Media3 service/session. The UI split
must retain that single state machine while moving presentation responsibilities out of
the Activity file.

## Checkpoint 1 — UI architecture split

### Checkpoint 1A

- [x] created `ui/theme` and moved native WMS theme construction
- [x] created `ui/navigation` and moved the existing bottom navigation shell
- [x] created `ui/appearance` and moved the complete Appearance screen and skin tile
- [x] created `ui/components` and moved shared duration/time/file-size formatters
- [x] created `ui/playback` and moved the playback request presentation model
- [x] added unit coverage for moved formatter behavior
- [x] reduced `MainActivity.kt` from 1,982 to 1,754 lines without changing navigation or playback behavior
- [x] `testDebugUnitTest`
- [x] `lintDebug`
- [x] `assembleDebug`

Verified commit: `2166f67`

### Checkpoint 1B

- [x] moved the persistent Mini Player and its Media3 state synchronization into `ui/playback`
- [x] moved MediaController connection lifecycle into `ui/playback`
- [x] moved the complete audio visualizer/video Now Playing surface into `ui/player`
- [x] retained the playback service/session as the single playback state owner
- [x] reduced `MainActivity.kt` from 1,754 to 1,317 lines without changing playback behavior
- [x] `testDebugUnitTest`
- [x] `lintDebug`
- [x] `assembleDebug`

Real-device verification is deferred until a presentation or interaction change is made;
this checkpoint only relocates already verified Compose code.

Verified commit: `7482ca7`

### Checkpoint 1C

- [x] moved Home/Search presentation, search result cards, thumbnail loading, and developer status into `ui/home`
- [x] moved direct-URL classification into a shared `ui/components` utility
- [x] added unit coverage for direct URL versus search-term classification
- [x] reduced `MainActivity.kt` from 1,317 to 980 lines without changing Home/Search behavior
- [x] `testDebugUnitTest`
- [x] `lintDebug`
- [x] `assembleDebug`

Real-device verification is deferred for the same reason as Checkpoint 1B.

Verified commit: `6e30217`

### Checkpoint 1D — architecture split complete

- [x] moved the Import Sheet and its format/error presentation into `ui/importer`
- [x] moved the Library screen into `ui/library`
- [x] moved the current Playlist screen/dialog into `ui/library` in preparation for consolidation
- [x] retained `MainActivity.kt` as Activity/share intake and root state/navigation orchestration
- [x] reduced `MainActivity.kt` from 980 to 295 lines
- [x] `testDebugUnitTest`
- [x] `lintDebug`
- [x] `assembleDebug`

Real-device verification is deferred for the same reason as Checkpoint 1B. Checkpoint 1
is complete with no intentional presentation or interaction changes.

Next: Checkpoint 2 adds the metadata/artwork foundation with an explicit Room migration,
then the practical Mini Player redesign begins.

Verified commit: `4600914`

## Checkpoint 2 — metadata / artwork foundation

- [x] extended acquisition Probe/results with source author and HTTPS thumbnail metadata
- [x] persisted acquired author and source artwork instead of writing `artworkUrl = null`
- [x] advanced Room schema from v2 to v3 with explicit `MIGRATION_2_3`; no destructive fallback
- [x] committed generated Room schema v3
- [x] propagated real author/artwork into Media3 metadata with WMS artwork as fallback
- [x] added one reusable media presentation model for Home, Library, Playlist, Mini Player, and Player
- [x] established artwork priority: persisted source thumbnail, embedded audio art/local video frame, WMS fallback
- [x] added a bounded HTTPS/local/embedded/frame artwork resolver with in-memory caching
- [x] added unit coverage for metadata persistence and artwork ordering/fallback behavior
- [x] `testDebugUnitTest`
- [x] `lintDebug`
- [x] `assembleDebug`

Device migration check on Redmi 12 5G / Android 15:

- [x] update installation over retained A8 data succeeded
- [x] app restarted without Room or AndroidRuntime errors
- [x] existing Recently Added data and selected Mini Player media remained visible

Next: connect the common artwork/presentation model to the practical Mini Player and
verify thumbnail, seek, Previous, Play/Pause, Next, and MediaSession synchronization.

Verified commit: `624cedf`

## Checkpoint 3 — practical Mini Player

- [x] replaced the generic WMS emblem with the common media artwork component
- [x] show title plus author/provider secondary metadata
- [x] keep current position, duration, and seek control visible in the compact player
- [x] keep Previous, Play/Pause, and Next visible with queue-aware enabled states
- [x] synchronize playback, timeline, and media transitions from the existing MediaController
- [x] tapping the non-control media body opens Now Playing
- [x] removed the separate expanded Mini Player control layout
- [x] `testDebugUnitTest`
- [x] `lintDebug`
- [x] `assembleDebug`

Target-device verification on Redmi 12 5G / Android 15:

- [x] retained Instagram video displays a generated local video frame instead of WMS fallback artwork
- [x] three-item Playlist starts with MediaSession playback and queue-aware controls
- [x] Next transitions to the following item and disables at the queue end
- [x] Previous transitions back to the preceding item
- [x] seek moved playback from approximately `1:04` to `3:12`
- [x] Play/Pause matched MediaSession `PLAYING` / `PAUSED` state
- [x] tapping the media body opened Now Playing

Next: redesign Home around a Current Media Stage with first-class audio visualizer and
inline video, then use artwork in Recently Added.

## Checkpoint 4 — Home Current Media Stage

- [x] added a shared `MediaVisualSurface` so audio and video use the same media-first
  presentation contract in Home and Player
- [x] added the Home Current Media Stage above search, with title, author/provider,
  tap-to-open Player, and the selected visualizer for audio
- [x] render playing video inline on Home with the existing Media3 session rather than
  requiring a separate Player screen
- [x] use the common artwork resolver in Recently Added cards
- [x] added Android Back handling so Player and Appearance return to the underlying
  WMS screen instead of closing the app
- [x] `testDebugUnitTest`
- [x] `lintDebug`
- [x] `assembleDebug`

Target-device verification on Redmi 12 5G / Android 15:

- [x] an audio item rendered the artwork and selected visualizer in Home Current Media
  Stage
- [x] an inline YouTube video rendered in Home and visibly advanced across two captured
  playback frames without opening Player
- [x] tapping the Current Media Stage opened Now Playing
- [x] Android Back from Now Playing returned to Home
- [x] update installation retained the existing media collection and playback selection

Next: Checkpoint 5 consolidates the separate Playlist destination into Library's
All / Playlists experience, then changes primary navigation to Home / Library / Player.

## Checkpoint 5 — Library / Playlist consolidation

- [x] replaced the separate Playlist destination with `All` / `Playlists` inside Library
- [x] retained playlist creation, rename, deletion, media add/remove, ordering, and
  playlist queue playback through the existing PlaylistViewModel
- [x] rebuilt All as a media collection with first-class artwork, title, source,
  audio/video label, duration, file size, availability, and a safe delete action
- [x] changed primary navigation from Search / Library / Playlist to Home / Library /
  Player; Player opens Now Playing directly
- [x] `testDebugUnitTest`
- [x] `lintDebug`
- [x] `assembleDebug`

Target-device verification on Redmi 12 5G / Android 15:

- [x] Library All displayed retained media and asynchronously resolved an Instagram
  video frame and a local YouTube video frame in their media rows
- [x] Library Playlists displayed the retained `GateA4__Mix` playlist under the new
  internal tab
- [x] selecting that playlist retained its item list plus Play, move-up, move-down,
  and remove controls
- [x] the bottom Player destination opened Now Playing

Next: Checkpoint 6 redesigns full Player around the media visual surface, timeline,
transport actions, and active queue while retaining Media3 as the sole playback owner.

## Checkpoint 6 — full Player and product polish

- [x] rebuilt Now Playing around the shared media visual surface, timeline, elapsed /
  total time, Previous, Play/Pause, Next, and a queue-aware Up next section
- [x] added an explicit Home action in Player and hid the Mini Player / bottom navigation
  while full Player is open
- [x] made non-emblem audio visualizers the primary media surface: artwork and the WMS
  fallback are no longer permanently overlaid on them
- [x] aligned native visualizer families with the Web/PWA reference: rainbow ring,
  oscilloscope, spectrum bars, and kaleidoscope-style radial presentation
- [x] expanded native skin treatment with retro monospace and warm serif typography,
  plus style-specific geometry already provided by the theme shapes
- [x] added OpenDocument local media intake; users can select an audio
  or video file from device folders directly from Home, which is copied into WMS-managed
  storage and added to Library with metadata
- [x] removed the unused YouTube / device-search shortcut row in favour of the explicit
  `端末から音声・動画を選ぶ` action
- [x] added a persisted, skin-independent static background choice: Plain, Grid, Dots,
  or Scanlines. The Canvas background is drawn once with the UI and performs no polling
  or animation.
- [x] replaced the placeholder Developer Tools card with on-screen diagnostic state:
  engine readiness, yt-dlp version, supported intake routes, Library count, selected
  media, active skin / visualizer / reduced-motion state, and app version. It reads
  already-held UI state and performs no background measurement or monitoring.
- [x] `testDebugUnitTest`
- [x] `lintDebug`
- [x] `assembleDebug`

Target-device verification on Redmi 12 5G / Android 15:

- [x] installed the debug APK over the retained app data and restarted successfully
- [x] selected Grid under Appearance → Background; it immediately covered the Home
  canvas behind cards and remained associated with the selected skin
- [x] opened Developer Tools and confirmed actual values including `Engine: READY`,
  yt-dlp `2026.08.19`, Library item count, selected local media, Pixel Arcade /
  rainbow-ring appearance, and the debug app version
- [x] Home continued to show the audio visualizer as the main visual rather than a WMS
  icon overlaid on it

Next: commit this verified checkpoint, then continue the remaining product polish items
from `docs/CODEX_NEXT_TASK.md` without changing the A0–A8 playback/acquisition contract.

## Checkpoint 7 — UI consistency audit

- [x] replaced disabled Card/TextButton navigation with Material 3 selected navigation
  items for Home, Library, and Player
- [x] moved Home search / URL and local multi-file intake ahead of Current Media
- [x] made Full Player media-first again; restored the A2 ±10-second seek actions and
  moved visualizer selection to a compact secondary menu
- [x] reduced Mini Player to artwork, title/provider, progress, Play/Pause, and Next
- [x] renamed the Library collection to “すべてのメディア” and moved Share/Delete to
  row overflow actions; WMS-managed files can be shared through Android's chooser
- [x] converted Appearance to skin previews and user-facing visualizer labels while
  keeping persisted IDs unchanged
- [x] made MP3 192 the Import default, with container/video controls in 詳細オプション;
  removed developer diagnostics from the normal import flow without removing rights
  confirmation
- [x] replaced Home's skin-breaking Neon literals with Material color tokens and fixed
  the theme-level default content color so dark skins do not render default text black
- [x] documented parity findings and deferred Player parity candidates in
  `PRODUCT_UI_CONSISTENCY_AUDIT.md`

Initial local verification before the final theme fix:

- [x] `testDebugUnitTest`
- [x] `lintDebug`
- [x] `assembleDebug`
- [x] final APK installed on Redmi 12 5G / Android 15; Midnight Neon/Grid Home,
  compact Mini Player, selected navigation, and the dark-skin text correction inspected

Next: Player parity pack decision, then final designer polish/regression including the
new Library overflow, Import layout, and non-default skin sweep. This is a checkpoint,
not Product UI Redesign FINAL PASS.

## Checkpoint 8 — Player parity pack

- [x] added service-owned Media3 playback speed choices: 0.5x, 0.75x, 1x, 1.25x,
  1.5x, and 2x
- [x] added persisted Media3 Repeat Off / All / One and shuffle controls
- [x] added advanced session-only A-B loop controls with strict `A < B` validation,
  clear, and service-side looping during background playback
- [x] kept Full Player transport hierarchy as Previous / -10 / Play-Pause / +10 / Next
- [x] kept Mini Player compact and excluded parity controls from it
- [x] added testable playback-option helpers and unit coverage for speed, repeat,
  shuffle, A-B invalid ranges, clear, and loop validation
- [x] added `PLAYER_PARITY_PACK.md`

Target-device verification on Redmi 12 5G / Android 15:

- [x] user-confirmed Playback Speed, Repeat, Shuffle, and A-B loop interaction
- [x] retained Home, Mini Player, Full Player, Playlist, background/screen-off playback,
  notification controls, and position restore
- [x] final APK installed over retained app data

The visual/interaction polish of these controls remains part of the final Designer Polish
checkpoint; this is not Product UI Redesign FINAL PASS.

## Checkpoint 9 — Visualizer Diversity Redesign and custom background

- [x] audited the existing Canvas implementation: Oscilloscope/Wave, Spectrum City/Bars,
  and Rainbow Ring/Kaleido previously shared the same visual structure.
- [x] retained all twelve persisted visualizer IDs and remapped them to distinct V2 visual
  languages without deleting DataStore-compatible IDs.
- [x] began distinct Canvas renderers for Hyper Tunnel, Phosphor Lissajous, Spectrum City,
  Glyph Rain, Strange Attractor, Voronoi Shards, Emblem Reactor, Liquid Metaballs, Flow
  Field, Wireframe Terrain, Spectrogram Waterfall, and Minimal.
- [x] added a signed waveform channel and capped PCM-analysis publications to 20 Hz to
  avoid excessive Compose redraw work.
- [x] added custom background-image persistence: selected images are copied into WMS app
  storage, and DataStore retains the managed path plus a 0–24 dp blur amount.
- [x] added Appearance controls for selecting/replacing the device image and changing blur.
- [x] ran final `testDebugUnitTest`, `lintDebug`, and `assembleDebug`
- [x] installed the final debug APK over retained data on Redmi 12 5G on 2026-09-15
- [x] user-confirmed the Redmi device verification as OK
- [x] documented duplicate audit, visual languages, compatibility, performance limits,
  inspiration research, and deferred GPU work in `VISUALIZER_DIVERSITY_REDESIGN.md`

The Appearance selector can receive a separate static-preview tile polish in the final
Designer Polish checkpoint. Product UI Redesign remains **IN PROGRESS**, not FINAL PASS.
