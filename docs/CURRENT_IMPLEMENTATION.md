# Current Implementation

Last updated: 2026-09-13 JST

## Repository state

Repository: `goroyattemiyo/web-media-studio-android`

Current active branch:

`feat/gate-a3-local-library`

No Gate A3 PR has been created. Work is on the pushed remote branch only.

Merged baseline on `main`:

- Gate A0 — PASS
- Gate A1 — PASS on target Android device
- post-Gate-A1 system-bar safe-area fix merged in PR #4

Latest `main` baseline before Gate A2 branch:

`a296b5ace6e1ceb1340986f664128e40b147fd2f`

## Gate A1 proven product path

The following path passed on the target Android device:

`keyword Search -> thumbnail/duration result -> WMSに追加 -> automatic Probe -> rights confirmation -> MP3 save -> 閉じて再生 -> 最近追加したメディア -> Media3 playback`

Direct URL intake and Android share-sheet intake also passed end-to-end.

Android keyword Search uses on-device yt-dlp `ytsearch`; the obsolete Cloud Run YouTube Search dependency was removed.

The Search/update race found during Gate A1 is handled by serializing local Search behind the yt-dlp singleton monitor used by updates.

## Gate A2 — current implementation

Goal: acquisition must survive leaving/recreating the Activity and remain controllable through a foreground notification.

Implemented on PR #5:

- `ManagedAcquisitionService` foreground `dataSync` service owns MP3 acquisition
- ViewModel no longer owns/cancels the active acquisition coroutine when Activity/ViewModel is cleared
- one active acquisition job at a time
- progress notification (`WMS 保存中`)
- notification cancel action
- completion notification (`WMS 保存完了`)
- failure notification
- `ManagedAcquisitionBus` exposes RUNNING / SUCCEEDED / FAILED / CANCELED state
- Activity/ViewModel recreation restores active job source URL, progress, and terminal result when the recreated UI has no current source
- automatic yt-dlp stable refresh is skipped while a managed acquisition job is active
- acquisition is serialized against yt-dlp update/local Search after engine initialization
- invalid Service start intents are rejected safely
- foreground-start failure becomes a user-facing managed-acquisition error
- cancel with no active job no longer creates a false canceled result
- Android 13+ notification permission is declared
- MP3 acquisition continues using a per-job cache directory
- per-job cache directory is deleted from `finally`, covering success/failure/cancel unwind paths
- only a non-empty final MP3 is published as an `AcquisitionResult`
- managed acquisition state-transition unit tests added
- `docs/GATE_A2_DEVICE_CHECK.md` defines the real-device acceptance procedure
- saved-media player now exposes seek, elapsed/total time, 10-second skip controls, and playback state
- Import Sheet actions stay above the Android navigation bar

Gate A2 app version on the branch:

- versionCode `6`
- versionName `0.2.0-a2-managed-acquisition`

## Gate A2 — LOCAL PASS

Local Windows verification on 2026-09-12 JST:

- Gradle Wrapper 8.13 is now included with distribution checksum verification
- `:app:testDebugUnitTest` passed with JDK 17
- `:app:lintDebug` passed
- `:app:assembleDebug` passed and produced `app/build/outputs/apk/debug/app-debug.apk`
- the unit test suite is path-portable on Windows

Target-device verification on 2026-09-12 JST:

- device: Redmi 12 5G (`23076RA4BR`), Android 15 / API 35
- APK update installation passed
- leaving WMS during acquisition kept the managed job/foreground notification alive
- notification tap returned to WMS and the managed state remained available
- successful acquisition produced a non-empty MP3 and `閉じて再生` played it through Media3
- seek, elapsed/total time, 10-second skip, pause, and resume controls passed on device
- WMS UI cancellation stopped the Service, removed the foreground notification, exposed no partial final MP3, and left the job cache empty
- notification-action cancellation passed with the same cleanup result and no duplicate job
- a controlled storage-permission failure surfaced a user-facing failure instead of success and cleaned temporary output

Verified code checkpoint: `3716ab5afe6d34821dfcddcb2d3b820afe66e1e1`.

Gate A2 is LOCAL PASS. GitHub Actions and merge to `main` were intentionally not run.

## Gate A3 — checkpoint IN PROGRESS

Current verified implementation checkpoint:

`f301580c1235d6d9ba767ada2c9d3002d168e67b`

Implemented:

- app versionCode `7`, versionName `0.3.0-a3-local-library`
- Room 2.8.5 database version 1 with committed schema JSON
- future schema changes must use explicit Room migrations; no destructive fallback is configured
- KSP 2.3.12 Room code generation
- `MediaEntity`, `MediaDao`, `WmsDatabase`, and `MediaRepository`
- title/provider/source URL/path/MIME/type/duration/file size/created time/last position persistence
- acquisition success is exposed only after Room registration succeeds
- registration failure removes the just-produced final file and reports `LIBRARY_REGISTER_FAILED`
- first A3 launch imports existing non-empty MP3 files from the managed acquired-media directory
- Library bottom-navigation screen with all-media cards
- missing files are reported and cannot be played
- confirmed deletion removes only files inside the WMS managed-media directory, then removes the Room row
- selected media and playback position are persisted
- Room-backed Mini Player remains visible above bottom navigation on Search and Library
- repository unit tests cover registration, backfill, deletion, position clamping, and managed-path enforcement

Local Windows verification on 2026-09-13 JST:

- `:app:testDebugUnitTest` passed
- `:app:lintDebug` passed
- `:app:assembleDebug` passed
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- APK SHA-256: `4F0F45844176A127AF120CEFC78D3E518669F7658F323969A809B2B6A603FE1B`

Target-device checkpoint on Redmi 12 5G, Android 15 / API 35:

- A3 APK update installation passed
- Room database was created
- the two existing non-empty MP3 files appeared through the Room-backed Mini Player/Library state
- user confirmed Library shows both items and list playback works

Still required before Gate A3 LOCAL PASS:

1. restart/force-stop WMS and confirm the same Room rows, selection, and playback position return,
2. delete the known duplicate test item and confirm both its Room row and managed file disappear while the other item remains playable,
3. simulate or create a missing-file row and confirm graceful display/removal behavior on device,
4. run the final local three-task verification after any fixes,
5. update the Gate A3 checkpoint from IN PROGRESS to LOCAL PASS.

No GitHub Actions or merge to `main` was run.

## Actions / artifact policy

The original workflow ran the expensive Android build on every PR commit and again on `main`, producing ~110 MB APK artifacts repeatedly.

The Gate A2 branch changes the policy to reduce usage:

- Draft PR commits do **not** run Android CI
- full CI runs when the Draft PR is explicitly marked Ready for review
- no automatic second build after squash merge to `main`
- `workflow_dispatch` remains as a deliberate escape hatch
- concurrency cancels obsolete duplicate runs for the same target
- artifact name is `wms-android-debug`
- APK artifact retention is 1 day
- after a successful run, old `wms-android-*` artifacts are pruned so only the newest 2 remain, including legacy `wms-android-gate-a0-debug` artifacts

Current CI blocker:

Recent runs fail before any workflow step starts (`steps = 0`). Old APK artifacts were manually deleted on 2026-09-12. GitHub storage usage may take several hours to reflect deletions; avoid repeated CI retries while waiting.

## Acquisition storage behavior

Temporary work directory:

`cacheDir/wms-acquisition-jobs/job-<uuid>`

Successful final media directory:

`<app-specific external Music>/wms/acquired`

The engine creates each acquisition in its temporary job directory, finds the produced MP3, moves/copies the completed output to the final directory, validates that the final file exists and is non-empty, then removes the temporary job directory in `finally`.

No artificial fixed duration or file-size ceiling is imposed. Storage exhaustion is surfaced as a user-facing capacity error.

## SearchProvider boundary

```text
Search Home
   |
SearchViewModel
   |
SearchProvider
   |
LocalYoutubeSearchProvider (yt-dlp ytsearch)
   |
canonical YouTube URL
   |
MediaAcquisitionEngine / ManagedAcquisitionService
```

Search support and acquisition support remain separate capabilities. A source must not be described as downloadable merely because it appears in Search.

## Deferred to later gates

Gate A3:

- Room Local Library
- persistent acquired-media metadata
- delete from WMS
- reopen app and retain library

Gate A4:

- persistent playlists
- ordered entries / active queue

Gate A5+:

- MediaLibraryService / MediaSession background playback
- screen-off playback/system controls
- production skins and visualizers
- broader provider matrix

## Product/security boundaries

- permitted/public or otherwise authorized media only
- local acquisition remains explicit; Search never auto-downloads
- rights confirmation remains mandatory
- no account-cookie bypass
- no proxy rotation
- no DRM bypass
- no authentication/access-control bypass
- no arbitrary yt-dlp flags in normal UI

## Next engineering step

While Actions storage/usage accounting settles, continue safe Draft-only Gate A2 work without triggering CI.

When ready to verify:

`Draft PR #5 -> mark Ready once -> full CI -> download Gate A2 APK -> run docs/GATE_A2_DEVICE_CHECK.md -> if PASS, squash merge`
