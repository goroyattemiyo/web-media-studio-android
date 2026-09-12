# Current Implementation

Last updated: 2026-09-12 JST

## Repository state

Repository: `goroyattemiyo/web-media-studio-android`

Current active branch:

`feat/gate-a2-managed-acquisition`

Current PR:

`#5 feat: Gate A2 managed foreground acquisition`

PR #5 is intentionally **Draft** while Gate A2 is still awaiting CI + real-device verification.

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

Gate A2 app version on the branch:

- versionCode `6`
- versionName `0.2.0-a2-managed-acquisition`

## Gate A2 acceptance still OPEN

Required target-device proof:

1. start a save,
2. leave/recreate the Activity while acquisition is active,
3. foreground notification continues showing progress,
4. return to WMS and see the same source/job/progress/result,
5. allow one job to finish and play the final MP3,
6. cancel one job from WMS,
7. cancel one job from notification,
8. confirm canceled/failed jobs do not expose a successful partial final file and temporary job output is cleaned.

Do not mark Gate A2 PASS until the above is verified on the target device.

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
