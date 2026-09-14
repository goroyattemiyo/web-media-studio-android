# WMS Android — Product UI redesign progress

Last updated: 2026-09-14 JST

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
