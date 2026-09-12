# WMS Android — Codex Local Development Workflow

Last updated: 2026-09-12 JST

This document is the operating guide for continuing WMS Android development on the local Windows PC with Codex while minimizing GitHub Actions usage.

## Read this first

When Codex starts work on this repository, read these files before changing code:

1. `AGENTS.md`
2. `docs/CODEX_LOCAL_DEVELOPMENT_WORKFLOW.md`
3. `docs/CURRENT_IMPLEMENTATION.md`
4. `docs/ARCHITECTURE.md`
5. `docs/ROADMAP.md`
6. `docs/GATE_A2_DEVICE_CHECK.md` when working on Gate A2

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

Target Android device
  -> real-device verification

GitHub Actions
  -> final verification only, normally once per Gate / feature
```

The rule is:

> CI is not the primary debugging tool. CI is the final verification of a locally validated candidate.

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

## Optional local helper script

A future helper may wrap the standard checks in a single command such as:

```text
scripts/local-verify.ps1
```

The script should only orchestrate local checks; it must not automatically trigger GitHub Actions.

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
- unit tests
- lint
- APK assembly
- relevant documentation
- real-device test procedure

Do not report a Gate as complete only because the APK compiles.

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

## Final Gate workflow

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

## Current Gate A2 handoff

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

Gate A2 is **not complete** until build and device verification pass.

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

## After Gate A2

The planned order remains:

```text
Gate A2 — Managed acquisition jobs
Gate A3 — Persistent Local Library (Room)
Gate A4 — Persistent Playlists
Gate A5 — Background playback / MediaSession / Now Playing
```

The main bottom navigation product direction is:

```text
Search | Library | Playlist
```

Do not jump ahead in a way that destabilizes the current Gate unless the user explicitly changes priorities.
