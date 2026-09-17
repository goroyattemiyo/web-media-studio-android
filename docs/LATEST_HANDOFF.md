# Latest Handoff — WMS Android

Date: 2026-09-18 JST

## Current source of truth

- Repository: `goroyattemiyo/web-media-studio-android`
- `main` is now the development source of truth. The user approved promoting the latest Sound Control branch to main.
- Fast-forward only (no force push): `main` was advanced from `a296b5ace6e1ceb1340986f664128e40b147fd2f` to `79c3195f9116f3aa7481bca4db1452debbaa44e7` on 2026-09-18 JST. A post-update comparison confirmed identical code/history to `feat/sound-controls-full-local` at that checkpoint.
- This handoff document may add a subsequent documentation-only commit on main; use `git rev-parse origin/main` to resolve the current HEAD, not the fixed application checkpoint above.

## Already released (leave unchanged)

- Developer Preview 1: `android-v0.9.0-preview.1`, versionCode 13, commit `de53418eb7abacde670f2e111c2601303f1aa1cc`.
- Historical release notes: `docs/HANDOFF_2026-09-17_PREVIEW1_RELEASE.md`.
- Preserve its tag, APK and checksum; do not retag or replace assets.

## Unreleased Preview 2 candidate

- Application version in source: `0.9.0-preview.2`, versionCode 14.
- Includes A-B loop / 19-second seek fix (PR #7), Sound tab/basic volume (PR #9), boost/limiter, device-dependent EQ and presets (PR #10), plus the latest boost-switch UI feedback fix `79c3195f9116f3aa7481bca4db1452debbaa44e7`.
- User confirmed boost operation on a real device on 2026-09-18 JST. This is user-reported functional PASS for boost, NOT blanket confirmation of all scenarios, no clipping, car Bluetooth compatibility, unit tests, lint or APK checks.
- The earlier failure was `UnsafeOptInUsageError` in four PlaybackService locations; fix commit `fdeea712fdd70f907bb7d99695a44b294196bc07`. A post-fix complete verification log has not been supplied in this chat.
- Verify `scripts/local-verify.ps1 -Install` and inspect `Local verification PASS`, `Install/update verification PASS`, artifact name and SHA-256. Check Library data, playback beyond 19 seconds, EQ/preset persistence, MP3/video, Bluetooth route changes and background playback as applicable before publishing.
- Do not label stable, publish Preview 2 or claim all gates PASS until verified. The latest boost is still experimental and DSP limiter cannot guarantee downstream distortion-free audio.

## Pull request bookkeeping

- PRs #7, #9 and #10 were stacked Draft PRs; their code reached main by fast-forwarding the top branch, **not** by GitHub PR merge operations. Mark their status appropriately when closing out bookkeeping, without claiming they were individually merged through GitHub.
- PR #8 contains the independent `docs/AUDIO_CONTROLS_DESIGN.md` design specification and was not part of the Sound Control implementation branch; handle separately if its design document is to be added to main.
- Do not run GitHub Actions automatically or repeat expensive CI. Draft development, static review, local tests, and one final gate follow the Actions-saving policy.

## Next steps

1. From local repo: `git fetch origin`, protect local changes, switch to `main`, `git pull --ff-only origin main`.
2. Confirm current HEAD/version and complete the local and device release checks.
3. Only after checks pass, create `android-v0.9.0-preview.2` from the verified application commit, upload versioned APK and `.sha256` to a pre-release and verify checksum and links.
