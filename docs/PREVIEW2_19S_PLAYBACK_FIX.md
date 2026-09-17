# Preview 2 candidate — playback unexpectedly returns to ~19 seconds

Date: 2026-09-17 (JST)
Status: User-confirmed real-device checks 1–5 PASS; local build, unit-test and lint logs have not been inspected, and remaining regression checks are open.
Base: `plan/product-ui-redesign` at `3aaffa95479eaaffc05d99721e6bd6e9f3e2a145`.
Release `android-v0.9.0-preview.1` and its assets must remain unchanged. This development build is `0.9.0-preview.2` / versionCode 14, not yet released.

## Report

During playback, dragging the seek bar to the latter part of a song returns to around 19 seconds. Normal playback also loops back before the end.

## Code-backed cause candidate

`PlaybackService` kept enabled A-B points while switching media. `NowPlayingScreen` used a separate local A-B state, resetting its displayed state when the screen/media changed while the service could continue looping. The 200-ms service ticker seeks to A after reaching B; with A near 19 seconds, this matches the report. This is a strongly supported code path, not a confirmed real-device root cause; damaged files or other position resets remain possible if the bug reproduces with A-B OFF.

## Changes on this branch

- Clear service-owned A-B points and cancel the loop ticker on any media-item transition.
- An explicit seek before A or to/after B cancels A-B, so the requested position wins. Seeks inside [A, B) preserve looping; the tick-driven seek back to A also remains valid.
- Share the service's A-B state to the in-process Now Playing UI through `AbLoopStateBus`. It is a UI mirror, not a replacement for MediaSession, and matches the existing same-process service configuration.
- Add pure-state regression tests for inside/outside range and inactive loop behavior.
- Prepare Preview 2 version metadata without modifying Preview 1 tag or assets.

## Real-device verification reported by user (2026-09-17 JST)

The user installed the candidate and explicitly reported all five requested functional checks as OK:

- [x] Ordinary playback proceeds beyond 19 seconds and does not jump back.
- [x] Seeking to the latter portion in the full player does not jump back to 19 seconds.
- [x] A-B looping and an explicit seek outside the loop behave as requested.
- [x] Seeking to the latter portion in the mini-player does not jump back.
- [x] Switching tracks after setting A-B looping plays normally.

These are user-reported device observations, not automated test results. The PowerShell `local-verify.ps1` output (unit tests, lint, APK build and installation verification) has not been provided for inspection. Do not mark those checks PASS without logs. Do not infer that every additional scenario below was tested.

## Review checklist before declaring fixed

1. Static review: Media3 `onPositionDiscontinuity` SEEK behavior, initial service restoration, loop ticker cleanup, and MediaSession custom-command callbacks.
2. Run local unit tests, lint and debug build using the repository's `scripts/local-verify.ps1`. Do not use GitHub Actions for debugging; inspect the PowerShell output.
3. On a device, with A-B OFF: play an MP3 past 19 seconds through the actual end; seek to 50% and near the end in both full and mini players. Test a second audio file and a video.
4. Set A=19s, B=60s, enable looping. Verify natural loop at B. Seek inside the interval: loop stays on. Seek to 120s or before A: seek is honored and loop UI shows OFF.
5. Start an A-B loop, switch songs using Next and Library. Verify loop state clears and the new track can finish normally. Return to Now Playing after navigating Home/Library: shown loop state must match the service.
6. Check playback while screen is off, pause/resume, repeat-one/all and notification controls for regressions.
7. If the issue persists when the visible A-B state is OFF, capture the track duration displayed in WMS versus the actual file duration, whether the same local file plays fully in another player, and Media3 discontinuity/transition events before changing extractor, files or persistent positions.

Functional checks 1–5 reported by the user are PASS as documented above; expanded coverage in this checklist remains open unless specifically verified. Acceptance remains OPEN pending local verification evidence and remaining regression checks. Do not merge this branch or publish Preview 2 automatically.
