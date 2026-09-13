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

Next: continue Checkpoint 1 by extracting Home/Search, Library/Playlist, Import, Mini
Player, and full Player surfaces before changing their product layout.
