# Gate A4 — Persistent playlists device checkpoint

Date: 2026-09-13 JST

Status: **LOCAL PASS**

Branch: `feat/gate-a4-playlists`

Verified implementation checkpoint: `d496b42dd721edcf83cf7083a14fa1919cf425e5`

APK SHA-256: `BB4ED94A75F391BC1C4A48D0A2B7F64C33938F3030B4468C31AFA36E7986F728`

Target device: Redmi 12 5G (`23076RA4BR`), Android 15 / API 35.

## Verification results

- [x] versionCode 8 / `0.4.0-a4-playlists` installed over retained Gate A3 data without a Room migration crash
- [x] existing Library media and playback position survived the Room v1-to-v2 migration
- [x] create, rename, and delete playlists without affecting Library media
- [x] add and remove Library items
- [x] reorder two playable items and retain the exact order after force-stop and restart
- [x] restore the selected active playlist after restart
- [x] play the expected items with Previous and Next according to playlist order
- [x] default a new import to the active playlist and show `保存先: GateA4＿Mix`
- [x] complete an authorized acquisition into that destination
- [x] observe the playlist count increase from two to three and play the newly acquired item
- [x] pass local `testDebugUnitTest`, `lintDebug`, and `assembleDebug`

The disposable ADB-created ordering file had a shell-owned SELinux label, was used only to
exercise persistence, and was removed. Final ordered playback and import checks used
app-owned managed MP3 files.

Gate A4 is LOCAL PASS. GitHub Actions and merge to `main` were not run.
