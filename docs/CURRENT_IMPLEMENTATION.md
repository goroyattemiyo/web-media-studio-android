# Current Implementation

Last updated: 2026-09-15 JST

## Repository state

Repository: `goroyattemiyo/web-media-studio-android`

Current active branch:

`plan/product-ui-redesign`

Gate A8 remains LOCAL PASS at the branch base. Product UI Redesign is in progress on top
of that verified baseline. No GitHub Actions run, Release creation, or merge to `main`
was performed.

## Product UI Redesign — current checkpoint

Completed checkpoints through the verified Visualizer Diversity V2 work are recorded in
`docs/PRODUCT_UI_REDESIGN_PROGRESS.md`.

The current Audio Analysis V2 / Visualizer Renderer V3 work has two distinct statuses:

- Audio Analysis V2 is committed at `bdda224687761cd9b888edbd44e607d506b16d9b` and
  locally passes unit tests, lint, and debug assembly.
- Seven dedicated Visualizer V3 renderers, API 26–32 Canvas fallbacks, static Appearance
  previews, persisted-ID mapping, and pause-time analysis clearing are implemented in the
  working checkpoint and locally pass unit tests, lint, and debug assembly.
- The dirty-checkpoint APK installed over retained data on Redmi 12 5G / Android 15.
  All seven V3 modes entered composition without fatal/RuntimeShader errors, reduced
  motion disabled their selectors, Wireframe Terrain populated from real FFT data, and
  screen-off playback plus media-key pause remained functional.
- A final human aesthetic sign-off for every mode and the remaining Product UI Redesign
  preservation checks have not been completed.

See `docs/AUDIO_ANALYSIS_V2_VISUALIZER_V3.md` for the implementation and verification
boundary. Product UI Redesign is still **IN PROGRESS**, not LOCAL PASS.

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

## Gate A3 — LOCAL PASS

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
- force-stop/restart retained the Room rows, selected media, and the saved `4:56 / 4:58` playback position
- deleting one duplicate removed exactly its Room row and managed file; the remaining item stayed playable
- a disposable missing-file item showed a clear missing state, disabled playback, and allowed safe stale-row deletion
- the disposable test copy and Download backup were removed; the valid `wms-9d75...` item remains
- final `testDebugUnitTest`, `lintDebug`, and `assembleDebug` all passed after device verification

Gate A3 is LOCAL PASS at code checkpoint `f301580c1235d6d9ba767ada2c9d3002d168e67b`.

No GitHub Actions or merge to `main` was run.

## Gate A4 — LOCAL PASS

Verified on 2026-09-13 JST at `d496b42dd721edcf83cf7083a14fa1919cf425e5`.

Implemented locally:

- app versionCode `8`, versionName `0.4.0-a4-playlists`
- Room database version 2 with an explicit v1-to-v2 migration and committed schema JSON
- playlist and ordered-entry entities, DAO, repository, and persistence
- playlist create, rename, delete, add, remove, and reorder UI
- optional playlist selection during acquisition; registration is rolled back if playlist insertion fails
- the active playlist is the default import destination and the destination is displayed explicitly before saving
- persisted active-playlist selection and Mini Player Previous/Next controls based on its ordered entries
- repository unit tests for name validation, duplicate prevention, dense ordering, move, and removal

Verified at this checkpoint:

- `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass locally
- the A4 APK installs over the existing A3 app data without a Room migration crash
- device package reports version `0.4.0-a4-playlists` / versionCode `8`
- playlist create, rename, delete, add, remove, and reorder passed on Redmi 12 5G / Android 15
- selected playlist and entry order survived force-stop and restart
- ordered Previous/Next played the expected real managed files
- an authorized acquisition increased `GateA4＿Mix` from two to three entries and the new item played
- final `testDebugUnitTest`, `lintDebug`, and `assembleDebug` all passed
- final APK SHA-256 is `BB4ED94A75F391BC1C4A48D0A2B7F64C33938F3030B4468C31AFA36E7986F728`

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

Device audit note from 2026-09-15: two old job directories from earlier sessions remained
under this cache root while no acquisition service was active. The current `finally`
cleanup covers normal success/failure/cancel unwind, but a process death or app update can
bypass it; no startup stale-job cleanup is implemented. The directories were left intact
for diagnosis. This is a recorded follow-up constraint, not evidence that the current
normal completion path failed.

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

## Gate A5 — LOCAL PASS

Verified on 2026-09-13 JST at `1cc703d09eae5dd89bf78e5ee8c1246889e3ab0c`.

Implemented and verified:

- app versionCode `9`, versionName `0.5.0-a5-background-playback`
- `MediaLibraryService` owns ExoPlayer and a `MediaLibrarySession`
- the Compose UI connects through a Media3 `MediaController`
- media audio attributes, audio-focus handling, and becoming-noisy handling
- foreground media notification and system Play/Pause/Previous/Next controls
- persisted playlist queue, active index, and playback position restoration
- full native Now Playing view with WMS emblem presentation
- visualizer renderer and future audio-analysis data-source boundaries
- lightweight `emblem`, `pulse`, `orbit`, `bars`, `wave`, and `minimal` modes
- screen-off playback and screen-off Next on the target Android device
- service/process restart restored a three-item queue paused at the saved item and position
- local `testDebugUnitTest`, `lintDebug`, and `assembleDebug` passed
- APK SHA-256: `80ACD8D9AF0AEE8AE2D9F2FD21B0EA88679E38F12566604597AEBE73491FE6D0`

See `docs/GATE_A5_DEVICE_CHECK.md` for the device evidence.

## Gate A6 — LOCAL PASS

Verified on 2026-09-13 JST at `901aca8e3fec0fc37639819ad1f4ce6373a98b99`.

Implemented and verified:

- app versionCode `10`, versionName `0.6.0-a6-production-ux`
- polished Search Home with canonical URLs on result cards and a compact Mini Player
- Import Sheet with `Audio / Video`, MP3 192 kbps default, controlled M4A 192 kbps and MP4 presets, and expandable details
- explicit unsupported, private/login-required, unavailable, format, and storage failure states
- all 14 canonical WMS skin IDs with native palette/shape styling and Appearance preview tiles
- all 12 canonical visualizer IDs, persisted skin/visualizer/reduced-motion preferences in Preferences DataStore
- PCM-derived playback level, waveform, and spectrum-bucket analysis without microphone permission
- reduced motion forces the static `minimal` renderer
- native Media3 video surface on Now Playing for video Library items
- local `testDebugUnitTest`, `lintDebug`, and `assembleDebug` passed
- APK SHA-256: `9CD0E8BD47C6AE7E87A1316AD8BC4AA5BDF7080E6D1E33A76A825BCEF7A8DD52`
- Redmi 12 5G / Android 15 verified Appearance persistence, M4A acquisition/playback, and MP4 acquisition/video playback

See `docs/GATE_A6_DEVICE_CHECK.md` for the device evidence.

## Gate A7 — LOCAL PASS

Verified on 2026-09-13 JST at `f6430929763821d836cfc36d2cc2d7d48bcf04d2`.

Implemented and verified:

- app versionCode `11`, versionName `0.7.0-a7-provider-matrix`
- provider identity catalog separates provider recognition from keyword-search capability
- host normalization tests cover YouTube, TikTok, Instagram, direct web, and suffix spoofing
- public-domain Wikimedia direct URL passed Probe, MP3 acquisition, Library registration, and playback
- a public no-login Instagram Reel passed Probe, MP4 acquisition, Library registration, and native video playback
- a login-required Instagram post was classified `LOGIN_REQUIRED` without cookie or login bypass
- a TikTok landing page was rejected as `UNSUPPORTED_SOURCE`; TikTok post evaluation was explicitly skipped by user decision
- local `testDebugUnitTest`, `lintDebug`, and `assembleDebug` passed
- APK SHA-256: `2F529B4E92D3555AE26FADBCC87A786CA6BF5EB171F00DB8E0A01EEA04BA3F04`

See `docs/GATE_A7_PROVIDER_MATRIX.md` for the complete matrix and device evidence.

## Gate A8 — LOCAL PASS

Verified on 2026-09-14 JST at `7b738b943f0572cace827e582461f07f69b6b874`.

Implemented and verified:

- app versionCode `12`, versionName `0.8.0-a8-dev-distribution`
- `scripts/local-verify.ps1` runs unit tests, lint, and APK build before publishing a local artifact
- versioned artifact names contain versionName, versionCode, and clean Git checkpoint; dirty builds are labeled `-dirty`
- a sibling SHA-256 file is produced for every local distribution artifact
- optional `-Install -Serial <serial>` performs an `adb install -r` update and verifies the installed package version
- dedicated development-keystore/secrets strategy and certificate continuity are documented without adding secrets
- update and rollback procedures explicitly protect Room/DataStore and managed media from destructive uninstall
- clean artifact: `wms-android-0.8.0-a8-dev-distribution-v12-7b738b943f05-debug.apk`
- APK SHA-256: `F24F2AB335EF4287C9BF61468D195E7C8ED7516657A050734C2B908742FE03B6`
- current local debug certificate SHA-256: `31cec40b17394a7e722f17ca337e94818a2393e3472c665256cd8c76934ba4d9`
- versionCode 12 updated over A7 on Redmi 12 5G / Android 15 while retaining Room, DataStore, nine managed media files, and playback

See `docs/DEVELOPMENT_DISTRIBUTION.md` and `docs/GATE_A8_DEVICE_CHECK.md`.

## Deferred external approval boundary

- final GitHub CI run
- optional private Release creation
- merge to `main`

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

All roadmap Gates are LOCAL PASS. Stop before GitHub Actions, Release creation, or merge to `main` unless the user explicitly approves those external actions.
