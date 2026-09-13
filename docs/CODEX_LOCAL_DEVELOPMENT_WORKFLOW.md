# WMS Android — Codex Local Development Workflow

Last updated: 2026-09-12 JST

This document is the operating guide for continuing WMS Android development on the local Windows PC with Codex while minimizing GitHub Actions usage.

The intended mode is not limited to Gate A2. Codex may continue sequentially through later roadmap Gates as long as each Gate is locally verified, documented, and isolated in its own branch/checkpoint.

## Read this first

When Codex starts work on this repository, read these files before changing code:

1. `AGENTS.md`
2. `docs/CODEX_LOCAL_DEVELOPMENT_WORKFLOW.md`
3. `docs/CURRENT_IMPLEMENTATION.md`
4. `docs/ARCHITECTURE.md`
5. `docs/ROADMAP.md`
6. the current Gate-specific device/checklist document when one exists

Do not assume older chat history is the source of truth when these repository documents say otherwise.

## Current development model

GitHub Actions minutes are limited and must not be used as the normal debugging loop.

Use this division of work:

```text
ChatGPT
  -> architecture / review / GitHub PR management

Local Codex on the home PC
  -> implementation
  -> local static review
  -> local unit tests
  -> local lint
  -> local APK build
  -> fix local build errors
  -> continue to the next Gate after local PASS

Target Android device
  -> real-device verification

GitHub Actions
  -> final verification only, normally once per Gate / feature
```

The rule is:

> CI is not the primary debugging tool. CI is the final verification of a locally validated candidate.

## Autonomous continuation policy

Codex should not stop automatically after Gate A2.

After the current Gate reaches local PASS, Codex should:

1. update the Gate documentation and local checkpoint,
2. commit and push the verified Gate head,
3. create the next Gate branch from that verified head,
4. continue implementation according to `docs/ROADMAP.md`,
5. keep all later work local/Draft while GitHub Actions minutes are exhausted,
6. never merge to `main` without explicit instruction.

The expected sequence is currently:

```text
Gate A2 — Managed acquisition jobs
  -> Gate A3 — Persistent Local Library
  -> Gate A4 — Persistent Playlists
  -> Gate A5 — Background playback / MediaSession / Now Playing
  -> Gate A6 — Production-quality UX / WMS appearance parity
  -> Gate A7 — Provider matrix
  -> Gate A8 — Development distribution
```

Codex may continue through these Gates in order when technically reasonable.

Do not skip a Gate dependency. Later Gates may be built on top of a locally verified earlier Gate even if that earlier Gate has not yet been merged to `main` because Actions minutes are unavailable.

## Stacked local branch policy while Actions are unavailable

Because Gate A2 cannot currently receive final GitHub CI, later work may be stacked locally.

Example:

```text
main
  |
  +-- feat/gate-a2-managed-acquisition
          |
          +-- feat/gate-a3-local-library
                  |
                  +-- feat/gate-a4-playlists
                          |
                          +-- feat/gate-a5-background-playback
```

Rules:

- Each Gate gets its own branch.
- Create the next Gate branch from the locally verified head of the previous Gate.
- Push each branch so work is backed up to GitHub.
- Keep PRs Draft while Actions minutes are unavailable.
- Do not squash multiple Gates into one branch merely to move faster.
- Do not merge stacked branches into `main` out of order.
- When Actions minutes become available again, finalize Gates in order.

Finalization after Actions reset should be:

```text
A2 local PASS -> final CI -> merge A2 to main
A3 rebase/retarget onto new main -> final CI -> merge A3
A4 rebase/retarget onto new main -> final CI -> merge A4
A5 ...
```

If a later stacked branch conflicts after an earlier Gate is squash-merged, rebase or recreate the later branch from the new `main` and carry only the intended Gate changes forward.

## When Codex must stop and ask instead of continuing

Codex may proceed autonomously for implementation details that follow the approved architecture and roadmap.

Stop and ask the user before:

- changing the product's core direction,
- removing an existing user-facing capability,
- introducing DRM/authentication/cookie bypass behavior,
- adding account-cookie based acquisition,
- adding proxy rotation,
- making destructive or irreversible data migrations,
- publishing to Play Store or a public release channel,
- adding paid external infrastructure or services,
- changing repository visibility or security settings,
- merging a PR to `main`,
- spending GitHub Actions minutes intentionally when the user has not approved it.

Normal implementation decisions inside an approved Gate do not require repeated confirmation.

## GitHub Actions policy

- Keep active development in a **Draft PR**.
- Normal commits pushed to a Draft PR must not start the expensive Android CI.
- Do not mark the PR Ready just to see whether the code compiles.
- Do not repeatedly rerun failed CI without identifying the cause.
- Do not run full Android CI for docs-only changes.
- Do not trigger an extra full CI automatically after merging the same verified change to `main`.
- Use `workflow_dispatch` only when an intentional additional verification is needed.
- Aim for **one full GitHub Actions run per Gate / feature**.

If a workflow fails before any step starts (`steps = null`, empty, or zero steps), do not treat it as a code/Gradle failure. Check Actions minutes, Billing/Budget, GitHub service status, permissions, and runner availability first.

Current known account condition as of 2026-09-12:

```text
GitHub Free Actions minutes: 2000 / 2000 used
Actions storage: approximately 0.1 / 0.5 GB used
```

Therefore GitHub Actions must not be used as a development loop until the included minutes reset or the user explicitly changes the billing policy.

## Artifact policy

- Debug APK artifact name: `wms-android-debug`
- Retention: 1 day
- Keep only the newest 1–2 WMS Android APK artifacts.
- Old `wms-android-*` artifacts may be pruned.
- Artifact cleanup saves storage; it does **not** restore consumed Actions minutes.

## Local Windows setup

Expected repository path:

```powershell
C:\dev\web-media-studio-android
```

Required toolchain:

- JDK 17
- Android SDK
- compileSdk 36
- Android platform 36
- build-tools 35.0.0
- Gradle 8.13 when the repository wrapper is not available

Before starting work:

```powershell
cd C:\dev\web-media-studio-android
git status
git fetch origin
```

Switch to the branch specified by the current task. For current Gate A2 work:

```powershell
git switch feat/gate-a2-managed-acquisition
git pull --ff-only origin feat/gate-a2-managed-acquisition
```

Do not develop directly on `main`.

## Local development loop

For each implementation chunk:

```text
1. Read the relevant implementation and docs.
2. Make the smallest coherent change.
3. Review imports / APIs / types statically.
4. Review lifecycle / cancellation / concurrency / cleanup paths.
5. Add or update unit tests where practical.
6. Run the local verification commands.
7. Fix all local failures locally.
8. Build the APK locally.
9. Perform the required real-device check when the change needs device behavior.
10. Commit and push only after local verification is acceptable.
11. Keep the PR Draft until the Gate is ready for final CI.
12. If the Gate reaches local PASS, create the next Gate branch and continue.
```

Avoid the loop:

```text
change -> push -> GitHub CI -> inspect error -> change -> push -> GitHub CI
```

Use instead:

```text
change -> local test/lint/build -> fix locally -> local PASS -> push
```

## Local verification commands

Prefer the repository Gradle wrapper if one is present:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
```

If this repository does not provide a Gradle wrapper, use installed Gradle 8.13:

```powershell
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
```

Do not skip `testDebugUnitTest` or `lintDebug` just because `assembleDebug` succeeds.

Expected APK path after a successful local build:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Local helper script

Codex should create and maintain a helper such as:

```text
scripts/local-verify.ps1
```

The helper should run local checks only, for example:

```text
testDebugUnitTest
-> lintDebug
-> assembleDebug
-> print resulting APK path
```

It must not trigger GitHub Actions.

## What Codex should check before declaring local PASS

At minimum review:

- implementation completeness
- imports and API availability
- Kotlin type correctness
- Android lifecycle behavior
- cancellation paths
- coroutine / race-condition behavior
- error handling
- cleanup of temporary files
- foreground-service rules
- Android Manifest declarations
- notification permission behavior
- persistence/migration behavior when Room/DataStore is involved
- unit tests
- lint
- APK assembly
- relevant documentation
- real-device test procedure

Do not report a Gate as complete only because the APK compiles.

For local-only progress while Actions are exhausted, distinguish:

```text
LOCAL PASS
```

from:

```text
FINAL PASS
```

`LOCAL PASS` means local tests/lint/build and required local device checks passed.

`FINAL PASS` requires the project's final GitHub CI and merge criteria when applicable.

## Commit and push policy

After local verification:

```powershell
git status
git diff
git add <intended files>
git commit -m "<clear commit message>"
git push origin HEAD
```

Before committing:

- inspect the diff,
- ensure no local secrets or machine-specific paths were added,
- ensure generated build output is not committed,
- ensure unrelated files are not included.

After pushing, keep the PR Draft unless the user explicitly decides the Gate is ready for final CI.

Codex must not merge the PR unless explicitly instructed.

## Gate transition procedure

When a Gate reaches LOCAL PASS:

1. Update `docs/CURRENT_IMPLEMENTATION.md`.
2. Update `docs/ROADMAP.md` with the local verification status.
3. Add or update a Gate-specific verification/checkpoint document.
4. Commit and push the verified Gate head.
5. Record the commit SHA in the checkpoint document.
6. Create the next Gate branch from that SHA.
7. Continue work on the next Gate.

Example after Gate A2 LOCAL PASS:

```powershell
git status
git add <intended files>
git commit -m "docs: record Gate A2 local pass"
git push origin feat/gate-a2-managed-acquisition

git switch -c feat/gate-a3-local-library
git push -u origin feat/gate-a3-local-library
```

Then continue Gate A3 without waiting for GitHub Actions.

## Final Gate workflow when Actions are available

Once local implementation and real-device verification are ready:

```text
Draft PR
  -> local implementation complete
  -> local unit tests PASS
  -> local lint PASS
  -> local assembleDebug PASS
  -> local APK produced
  -> target-device checks PASS where possible
  -> push final candidate
  -> mark PR Ready for review
  -> GitHub full CI once
  -> CI PASS
  -> final target-device verification if required
  -> squash merge
```

If final GitHub CI exposes a real code issue:

```text
identify exact cause
  -> return PR to Draft if further work is needed
  -> fix locally
  -> rerun local verification
  -> push
  -> run final CI again only when justified
```

Never rerun CI repeatedly without changing or diagnosing anything.

## Completed Gate A2 handoff

Current branch:

```text
feat/gate-a2-managed-acquisition
```

Current PR:

```text
#5 — feat: Gate A2 managed foreground acquisition
```

Keep PR #5 Draft while developing locally.

Gate A2 implementation currently includes:

- foreground `dataSync` acquisition service
- one active acquisition job
- progress notification
- notification cancel action
- notification tap returns to WMS
- Activity/ViewModel reconnect to managed acquisition state
- first-save notification permission request
- `.mp3.part` staging before final MP3 exposure
- cancel-aware finalization and cleanup
- temporary job-directory cleanup
- state-transition unit tests
- real-device Gate A2 checklist

Gate A2 reached **LOCAL PASS** on 2026-09-12 JST at verified code checkpoint
`3716ab5afe6d34821dfcddcb2d3b820afe66e1e1`. Local unit tests, lint, APK build,
successful playback, UI cancellation, notification cancellation, and cleanup checks passed.
Final CI and merge to `main` were intentionally not run.

Required Gate A2 real-device path:

```text
start MP3 save
  -> leave/recreate WMS Activity
  -> foreground notification continues
  -> tap notification and return to WMS
  -> same source/job/progress is visible
  -> save completes successfully
```

Also verify cancellation:

```text
start another save
  -> cancel from WMS or notification
  -> acquisition stops
  -> no incomplete final MP3 remains
  -> temporary job output is cleaned
```

See `docs/GATE_A2_DEVICE_CHECK.md` for the detailed checklist.

## Gate A3 target — Persistent Local Library

After Gate A2 reaches LOCAL PASS, continue to Gate A3.

Expected scope:

- Room database/schema
- media entity and DAO
- register successful acquisitions automatically
- persist title/provider/path/created time and required metadata
- Library screen in bottom navigation
- list all saved WMS media
- open/play a saved item
- delete item from WMS safely
- retain Library across app restart
- recover gracefully if a media file is missing
- persistent Mini Player shell using Library state where appropriate
- migration/version strategy documented
- unit tests for DAO/repository behavior where practical

Do not introduce destructive migration shortcuts merely to make development easier.

Current Gate A3 checkpoint on 2026-09-13 JST:

```text
branch: feat/gate-a3-local-library
verified implementation: f301580c1235d6d9ba767ada2c9d3002d168e67b
status: LOCAL PASS
```

Room schema v1, metadata persistence, acquisition registration, existing-file backfill,
Library listing/playback, persistent Mini Player, managed deletion implementation, and
repository tests are present. Local unit tests, lint, APK build, device installation,
two-item listing, and list playback passed.

Restart/position persistence, managed duplicate deletion, controlled missing-file recovery,
and the final local three-task verification passed on 2026-09-13 JST. See
`docs/GATE_A3_DEVICE_CHECK.md` for the evidence. GitHub Actions and merge to `main`
were not run.

Gate A3 local exit target:

```text
save authorized media
  -> item appears in Library
  -> restart app
  -> item is still present
  -> play item
  -> delete item
  -> database and local file lifecycle remain consistent
```

## Gate A4 target — Persistent Playlists

After Gate A3 reaches LOCAL PASS, continue to Gate A4.

Expected scope:

- playlist entity/schema
- create / rename / delete playlists
- add/remove Library items
- ordered playlist entries
- persistent ordering
- add acquired item to a playlist where product flow allows
- Playlist bottom navigation destination
- playlist detail screen
- previous / next behavior based on ordered entries
- queue restoration foundation

Gate A4 local exit target:

```text
create playlist
  -> add multiple Library items
  -> reorder / persist order
  -> restart app
  -> playlist and order remain intact
  -> play items in expected order
```

Current Gate A4 checkpoint on 2026-09-13 JST:

```text
branch: feat/gate-a4-playlists
status: LOCAL PASS — local automation and target-device exit checks passed
```

Room schema v2 with an explicit v1-to-v2 migration, playlist CRUD, ordered entries,
import-time playlist selection, persisted active-queue selection, and Previous/Next are
implemented. Repository unit tests and local `testDebugUnitTest`, `lintDebug`, and
`assembleDebug` pass. The APK installed over retained A3 data without a migration crash.

Target-device create/rename/delete, multiple-item add and reorder, restart persistence,
ordered Previous/Next, and acquisition into the selected playlist all passed. The final
import flow defaults to the active playlist and explicitly displays its destination.
Gate A4 is LOCAL PASS at `d496b42dd721edcf83cf7083a14fa1919cf425e5`.

## Gate A5 target — Background playback / MediaSession / Now Playing

After Gate A4 reaches LOCAL PASS, continue to Gate A5 if the architecture remains consistent with `docs/ARCHITECTURE.md`.

Expected scope:

- `MediaLibraryService`
- MediaSession ownership
- ExoPlayer owned by playback service instead of transient UI
- audio focus
- media notification/system controls
- screen-off playback
- Play/Pause/Previous/Next
- queue and position restore foundation
- full native Now Playing screen
- preserve WMS player identity
- visualizer renderer boundary
- lightweight visual modes first

This Gate requires careful real-device verification. Do not claim local PASS without testing background/screen-off behavior on the target Android device.

Current Gate A5 checkpoint on 2026-09-13 JST:

```text
branch: feat/gate-a5-background-playback
verified implementation: 1cc703d09eae5dd89bf78e5ee8c1246889e3ab0c
status: LOCAL PASS
```

`MediaLibraryService`/`MediaSession`, service-owned ExoPlayer, audio focus, foreground
media notification, system controls, queue/position restore, native Now Playing, and the
WMS visualizer boundaries and six lightweight modes are implemented. Local
`testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass.

On Redmi 12 5G / Android 15, playback continued after Home and with the screen off,
system Play/Pause/Next worked, screen-off Next advanced the active playlist, and a
force-stop/restart restored the three-item queue, active item, and saved position. See
`docs/GATE_A5_DEVICE_CHECK.md` for the evidence. GitHub Actions and merge to `main`
were not run.

## Gate A6 target — Production-quality UX and appearance parity

Current Gate A6 checkpoint on 2026-09-13 JST:

```text
branch: feat/gate-a6-production-ux
verified implementation: 901aca8e3fec0fc37639819ad1f4ce6373a98b99
status: LOCAL PASS
```

Search Home and Import have production-oriented presentation, controlled MP3/M4A/MP4
presets, canonical URLs, and explicit failure states. Appearance provides all canonical
skin and visualizer IDs with DataStore persistence, PCM playback analysis, and reduced
motion. Local `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass.

On Redmi 12 5G / Android 15, Appearance settings persisted across restart, M4A was
acquired and played through the WMS MediaSession, and MP4 was acquired and played on
the native video surface. See `docs/GATE_A6_DEVICE_CHECK.md`. GitHub Actions and merge
to `main` were not run.

## Gates A7–A8

Codex may continue beyond A6 according to `docs/ROADMAP.md`, but preserve the same rules:

- one Gate per branch,
- local verification first,
- no unnecessary GitHub Actions,
- no merge without explicit approval,
- no security/product-boundary expansion without approval,
- document LOCAL PASS checkpoints before moving forward.

## Bottom navigation product direction

The main product direction is:

```text
Search | Library | Playlist
```

Library and Playlist are not placeholders once Gates A3 and A4 are implemented.

## Final operating instruction to Codex

When starting from this document:

> Continue the current Gate to LOCAL PASS using local test/lint/build and required real-device checks. Do not use GitHub Actions as a development loop. Once a Gate reaches LOCAL PASS, document the checkpoint, push the Gate branch, create the next Gate branch from that verified head, and continue through the roadmap in order. Keep work in Draft/unmerged branches while Actions minutes are exhausted. Stop only for explicit approval boundaries listed in this document or when a blocking technical decision cannot be safely resolved from the repository architecture and roadmap.
