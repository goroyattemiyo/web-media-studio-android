# Gate A3 — Local Library device checkpoint

Date: 2026-09-13 JST

Status: **IN PROGRESS**

Branch: `feat/gate-a3-local-library`

Verified implementation checkpoint: `f301580c1235d6d9ba767ada2c9d3002d168e67b`

APK SHA-256: `4F0F45844176A127AF120CEFC78D3E518669F7658F323969A809B2B6A603FE1B`

Target device: Redmi 12 5G (`23076RA4BR`), Android 15 / API 35.

## Completed

- [x] install versionCode 7 / `0.3.0-a3-local-library` over Gate A2
- [x] create Room database `wms.db`
- [x] commit schema version 1 without destructive-migration fallback
- [x] import the two existing non-empty managed MP3 files
- [x] display both items in Library
- [x] play a Library item
- [x] show the selected item in the persistent Mini Player above bottom navigation
- [x] pass local `testDebugUnitTest`, `lintDebug`, and `assembleDebug`

## Resume here

### A3-1 Restart persistence and position

1. Play one Library item and seek to a recognizable position.
2. Pause it and note the displayed position.
3. Force-stop WMS or fully close it from Recents.
4. Reopen WMS.

PASS when the two Library rows remain, the selected item remains selected, its position is restored, and playback resumes from that position.

### A3-2 Managed deletion

The acquired directory currently contains two completed test MP3 files of 7,167,788 bytes each. Use the newer duplicate test item for deletion, retaining the other playable copy.

1. In Library, tap `削除` for one duplicate.
2. Confirm the destructive dialog.

PASS when exactly one Library row and its corresponding managed file disappear, the other row/file remains, and the remaining item still plays.

### A3-3 Missing-file recovery

Create a controlled missing-file condition only for a disposable test item.

PASS when Library shows `ファイルが見つかりません`, disables playback, and allows the stale Room row to be removed without affecting other files.

### A3-4 Final verification

Run locally:

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Then update `docs/CURRENT_IMPLEMENTATION.md`, `docs/ROADMAP.md`, and this file to Gate A3 LOCAL PASS, commit, and push. Do not run GitHub Actions and do not merge to `main`.
