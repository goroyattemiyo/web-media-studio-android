# Sound Control S1 — local update and verification

Date: 2026-09-17 JST. Status: implementation candidate, NOT built or device-verified by the assistant.

Dependency: this branch is based on the head of Draft PR #7 (19-second seek fix) and remains separate from design Draft PR #8. Draft PR #9 targets PR #7's branch. No merges, releases or GitHub Actions are authorized.

## Scope of this first local update

- Four tabs: Home, Library, Player, 音質.
- WMS player volume 0–100% (100% = unity gain), mute with last nonzero restore.
- PlaybackService persists the player-accepted volume and publishes the state across Sound navigation. Volume should continue to apply during track changes, background playback and restart.
- Mini-player remains available on the Sound tab.
- EQ, vertical faders, eight presets and custom-preset management are **not implemented in S1**. The screen labels them pending instead of exposing inert controls. Implement in later gates S2/S3 only after real-device EQ capability is checked.
- No system volume setting, absolute Bluetooth setting or >100% gain is changed.

## Local PowerShell sequence

Use a clean checkout at `C:\dev\web-media-studio-android`. The following commands must be run locally on the Windows PC connected to the target Android phone; they cannot be run by a GitHub connector.

```powershell
$repo = 'C:\dev\web-media-studio-android'
if (-not (Test-Path -LiteralPath $repo -PathType Container)) { throw "Repository not found: $repo" }
Set-Location -LiteralPath $repo
if ((git status --porcelain)) { throw 'Uncommitted changes: save or commit them before switching branches.' }
git fetch origin
if ($LASTEXITCODE -ne 0) { throw 'git fetch failed' }
git switch feat/sound-controls-local-s1
if ($LASTEXITCODE -ne 0) { git switch --track origin/feat/sound-controls-local-s1 }
if ($LASTEXITCODE -ne 0) { throw 'Branch switch failed' }
git pull --ff-only origin feat/sound-controls-local-s1
if ($LASTEXITCODE -ne 0) { throw 'Fast-forward failed' }
New-Item -ItemType Directory -Force -Path .\dist | Out-Null
.\scripts\local-verify.ps1 -Install *>&1 | Tee-Object -FilePath .\dist\sound-s1-local-verify.log
if ($LASTEXITCODE -ne 0) { throw 'Local verification failed. Inspect the log; do not rerun blindly.' }
```

The repository's local verification script runs unit tests, lint and assembles a debug APK, copies it to `dist` with a commit-specific filename and SHA-256, then uses adb `install -r` and checks installed package version. Confirm log lines `Local verification PASS` and `Install/update verification PASS`. If the script fails, report the *first real error* from the log instead of launching GitHub Actions or endlessly rerunning.

## Real-device acceptance

1. Open 音質 while a local MP3 plays: mini-player stays visible and playback continues.
2. Move volume 100 -> 35 -> 0 -> 100; both sound and numeric display follow. Restore to 35, press Mute and Unmute: 35 returns.
3. With volume at 35, switch tracks and tabs; setting remains 35. Turn screen off and back on; audio continues at the same setting. Restart WMS or let the service recreate; stored volume and last audible value restore.
4. Check Bluetooth and device speaker separately. WMS 100% is not guaranteed to repair a low-volume Bluetooth route.
5. Regression: play past 19 seconds, seek to second half using full and mini players, test A-B outside-range seek, switch MP3/video, Next/Previous, and visualizers.
6. Verify that the EQ area explicitly says not yet implemented; there must be no fake EQ toggle.

Record test, lint, build, install and every device check as PASS/FAIL separately. If the target is not connected, run without `-Install` to check compilation first, but do not claim device PASS.
