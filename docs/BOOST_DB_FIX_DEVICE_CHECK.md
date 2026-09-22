# BOOST dB fix — local and device acceptance

Date: 2026-09-23 JST
Branch: `fix/boost-db-gain-telemetry` (Draft PR #11, base `main`)
Status: candidate only. No new APK or device confirmation for this revision yet.

## Why Preview 2 could show ON without audible gain

- Preview 2 only enabled a 100–200% extension of a combined volume slider. At the initial 100%, the PCM boost multiplier stayed at 1.0 even with BOOST ON.
- `SafeBoostProcessor.onReset()` reset its target multiplier to 1.0; after an audio-renderer reconfiguration the UI could remain armed without reapplying that target.
- A full-scale/loud master cannot be made uniformly louder by +6 dB inside a 0 dBFS digital ceiling without altering its dynamics or clipping. The existing sample-peak limiter can reduce effective RMS gain, even below 0 dB on very loud material.

## Candidate behavior

- Normal app volume 0–100% and boost gain 0–+6.0 dB are separate controls. When explicitly enabled, boost starts at +3.0 dB; normal volume is unchanged. Boost is OFF on service startup and on output-route changes.
- The boost multiplier is `10^(requested_dB/20)` on accepted PCM. The limiter retains its -1 dBFS sample-peak ceiling. EQ and boost remain mutually exclusive. Unsupported PCM/offload paths must reject boost instead of pretending it is active.
- The Sound tab reports requested dB and the actual PCM output/input **RMS ratio** observed over a short window. This is not the acoustic level (SPL), a LUFS measurement, the final Android/receiver output, nor proof of no distortion.
- After a supported PCM format/renderer reconfiguration, the service reapplies the explicit boost setting. It must not rearm following an output-route change or service restart.

## Static and local verification (no GitHub Actions)

1. Confirm clean branch and unchanged release tags; do not re-tag or replace Preview 1/2.
2. Run `./scripts/local-verify.ps1 -Install` once when exactly one authorized ADB device is connected. This runs `testDebugUnitTest`, `lintDebug`, `assembleDebug`, then `adb install -r` and version verification. If the device is unavailable, run `./scripts/local-verify.ps1` (no `-Install`) first and install the produced APK later without rerunning Gradle.
3. Check `BUILD SUCCESSFUL`, `Local verification PASS`, APK filename/SHA-256, `Install/update verification PASS`, and library/playlist/data retention. No uninstall.
4. Check the new `BoostGainTest`, `AppVolumeStateTest`, `SafeBoostProcessorTest`: unity byte-exact, +3 dB quiet PCM, +6 dB after explicit rearm, and sample-peak limiting.

## Device listening and measurement acceptance

1. At safe Android/receiver volume, play a *quiet* locally imported MP3. EQ OFF, app volume fixed at 100%. Compare BOOST OFF and ON: ON must immediately request +3.0 dB, and the PCM measured RMS ratio should approach +3.0 dB on sufficiently quiet material; verify audible change without changing system volume. Test +0, +3, +6 dB.
2. Play a loud/normalized MP3 at low hardware volume. Check that the UI indicates actual RMS gain lower than requested when the limiter intervenes; no guarantee that this track becomes louder. Do not advertise +6 dB as guaranteed final output.
3. Change tracks, seek, pause/resume and restart renderer; boost must not silently revert to unity while showing armed. If measured gain disappears or is inconsistent, stop publication and collect reproduction data.
4. Change speaker/Bluetooth output route: boost disarms. Restart app/service: boost OFF. Change normal app volume, mute/unmute, use EQ/presets and verify boost/EQ mutual exclusion. Verify library state, background playback, video sync and no 19-second A–B seek regression.
5. For suspected audio processing bypass, verify the screen says measurement pending rather than falsely claiming +3 dB; check PCM support and route without forcing system/Bluetooth gain.

## Publication gate

- Do not merge Draft PR #11 or release the old Preview 2 APK as the fix before local and device PASS.
- When preparing a *new* public release, bump `versionCode` above 14 and use a new versionName/tag (e.g. Preview 3), then rebuild, verify checksum, preserve the same signing certificate for an in-place update, and publish new APK and checksum. Leave Preview 2 assets and tag immutable.
- Do not invoke heavyweight GitHub Actions merely to discover compile errors; local verification is the first gate.
