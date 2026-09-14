# Product UI Consistency Audit

Date: 2026-09-14  
Branch: `plan/product-ui-redesign`

## Scope and invariants

This audit reviewed the Product UI Redesign against the Android design documents, the proven A0–A8 architecture, and the Web/PWA player.  The Media3 `MediaLibraryService` / `MediaSession` remains the only playback-state owner.  Compose continues to render and command the existing `MediaController`; it does not maintain a parallel player state machine.

The acquisition pipeline, Room library and playlists, background playback, notification/system controls, provider classification, DataStore appearance persistence, visualizer persistence, update flow, and rights boundary were left intact.

## Problems found and corrected

| Area | Finding | Correction |
| --- | --- | --- |
| Bottom navigation | The old Card/TextButton tabs used disabled state as the selected indication. | Replaced with Material 3 `NavigationBar` items with icon, label, and selected state. |
| Full Player | The header exposed redundant technical navigation and the visualizer list was a dense raw selector.  The Product redesign had also dropped ±10-second seek. | Simplified to Back + secondary display selector; restored `-10s` / `+10s`; retained Media3 timeline, previous, central Play/Pause, next, and queue preview. |
| Mini Player | It duplicated the Full Player with time labels and a full transport row. | Compressed to artwork, title/provider, seek bar, Play/Pause, and Next.  The whole information row still opens Now Playing. |
| Home | Current Media preceded the primary search/URL action. | Moved Search and local multi-file pick before Current Media; audio visualizer/video surface remains available. |
| Library | All media were labeled “Recently added”; Share/Delete were permanently exposed on every row. | Renamed the section to “すべてのメディア” and moved Share/Delete into an overflow menu. |
| Appearance | Raw Web/PWA IDs and technical wording were visible; skins/visualizers were long raw lists. | Skin previews and visualizer choices are two-column visual selectors using display names only.  Internal persisted IDs are unchanged. |
| Import | The standard saving flow exposed format controls and developer/yt-dlp diagnostics. | Standard flow is MP3 192 kbps; container/video choices live in 詳細オプション. Developer tools are removed from the import sheet. Rights confirmation remains mandatory. |
| Theme | Transparent screen surfaces inherited a black content color, making text unreadable on dark skins. Several Home accents were Midnight-Neon literals. | `WmsTheme` now supplies the skin's `onBackground` as the default content color. Home accents/thumbnail overlays use Material color tokens. |

## Player parity with Web/PWA

Verified in the Android implementation:

- playback position, resume persistence, timeline seeking
- previous / next and local playlist ordering
- restored ±10-second seek
- visualizer and reduced-motion persistence
- MediaSession, background playback, notification/system transport controls

Not implemented in this checkpoint (recorded candidates, not regressions):

- playback speed
- shuffle
- repeat off / all / one
- A-B loop
- full Web/PWA-style multiple-file import workflow parity (Android supports multi-select local import; broader parity needs separate design)

## Verification record

- `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` passed after the final theme batch on 2026-09-14.
- Redmi 12 5G (`6167306007fe`) accepted the final debug APK. Home, the compact Mini Player, and selected Bottom Navigation state were inspected in the persisted Midnight Neon / Grid appearance. The dark-skin text-color regression was confirmed fixed.
- Full device regression coverage from the prior checkpoints remains valid for Library, Player, Appearance, Import, background playback, system notification controls, screen-off playback, and retained Room/DataStore data. The new row overflow, Import layout, and non-default skin grid need a final hands-on sweep in the next designer-polish checkpoint.

## Next checkpoint candidates

1. Player parity pack: assess playback speed, shuffle/repeat, and A-B loop as a separately designed Media3 feature set.
2. Final designer polish/regression: full multi-skin visual pass and manual device checks for Home, Library, Mini Player, Player, Appearance, Import, background playback, notification controls, screen-off playback, and retained Room/DataStore data.

This checkpoint is not a Product UI Redesign FINAL PASS.
