# WMS Android — Sound Control design (v0.2)

Date: 2026-09-17 JST
Status: **Design revision; boosted audio is NOT implemented or verified.**
Scope: `goroyattemiyo/web-media-studio-android`. This is an independent design Draft PR #8, based on `plan/product-ui-redesign` at `3aaffa95479eaaffc05d99721e6bd6e9f3e2a145`. Playback regression PR #7 remains separate. Do not merge, run GitHub Actions, or publish a release without user approval.

## 1. Product requirement and limitations

- Add a fourth bottom tab: `Home / Library / Player / 音質` with volume, EQ, presets, and persistent mini-player; navigation must not interrupt playback.
- **Revised user requirement:** audio may be too quiet when connected to a car/Bluetooth receiver, so WMS must offer an optional level ABOVE 100%, with digital clipping protection. Do not retain the former 'no boost' requirement.
- Default sound remains 100% (unity), boost OFF. Never automatically start boosted playback after install, route change or device reconnection. Never change Android system volume or Bluetooth absolute-volume settings.
- **No guarantee of distortion-free sound at arbitrary gain:** a limiter can reduce WMS-side digital clipping, but an already-distorted recording, intersample peaks, codec artifacts, car amplifier/speaker saturation, device output limits and OS/Bluetooth volume caps remain outside WMS's control. On loud masters a limiter may prevent any increase in perceived loudness. Avoid marketing the feature as a fix for all Bluetooth volume defects.

## 2. UX and navigation

```
[Home] [Library] [Player] [音質]
Sound Control
  WMS volume: 0–100% normal / >100% BOOST region (experimental maximum 200%)
  [mute]  ─────── 100% ─────── BOOST ─────── 200%
  100% = original level; >100% = digital gain + safety limiter
  [Boost enable: OFF by default] [Reset to 100%]
  EQ [ON/OFF] [preset dropdown: built-in / My Presets]
           +dB  │  │  │  │  │  vertical real-device faders
             0 ─┼──┼──┼──┼──┼─
           -dB  │  │  │  │  │
  [Flat] [Save as] [Rename] [Delete]
  [existing mini-player]
```

- User-approved initial **8 immutable built-ins**: フラット, ジャズ, ロック, ポップス, クラシック, ボーカル, 低音重視, 高音重視. Named My Presets support save/select/rename/delete, sensible name validation, explicit overwrite confirmation and persistence error handling. User edits become `カスタム（未保存）` rather than silently modifying a saved profile.
- Layout concept is five vertical faders left-to-right low→high, but actual displayed band count, frequency and dB limits MUST be derived from the device EQ effect; never show five imaginary independently working bands. Zero markers, actual center-frequency labels and accessible numeric adjustment required. EQ OFF or unsupported must be honestly indicated.
- An explicit boost indicator and one-tap return to unity are required; clearly show when boost cannot operate. Do not represent the unimplemented feature with a functional-looking slider.
- BOOST maximum 200% (+about 6 dB) is a **provisional engineering ceiling**, not a validated acoustic or loudness guarantee. Start device testing at a lower cap (for example 125–150%) and adjust downward based on clipping, limiter gain reduction, listening and route behavior. Review UI maximum only after tests.

## 3. Volume model and ownership

- Normal WMS level 0–100% maps to `MediaController`/ExoPlayer `volume` 0.0–1.0. 100% = unity; Media3 player volume is NOT an above-unity amplifier.
- For a requested level above 100%, cap `player.volume` at 1.0 and apply *separate* controlled PCM gain and limiter in the playback pipeline. Do not pass `2.0f` to `player.volume` and assume it works.
- Persist requested percentage, last audible volume, explicit boost permission and any limiter preference safely; distinguish **requested** gain from actually applied limiter gain and expose fallback status. Mute remembers and restores the last nonzero requested level, but boost must not unexpectedly reactivate after output route changes or service recreation. Prefer dropping to <=100% until the user explicitly re-enables boost on a new route.
- Keep PlaybackService the authoritative state owner, with MediaSession commands and observable UI state. Activity, Sound tab and mini-player must not keep separate unvalidated state. The current `feat/sound-controls-local-s1` / Draft PR #9 is **only a 0–100% baseline**, not fulfillment of the revised >100% requirement.
- Never change hardware system stream volume, force developer options, or override hearing-safety settings. A user-selectable enhancement affects WMS playback only.

## 4. Gain + limiter DSP proposal and constraints

- Integrate the gain/limiter into the existing Media3 PCM audio processor chain. Current `AnalysisRenderersFactory` installs `TeeAudioProcessor(analysisSink)` through `DefaultAudioSink.Builder.setAudioProcessors`; review relative processor order and whether FFT visualizers should observe the unboosted or post-processed signal.
- Conceptual flow for PCM: decoded audio → EQ (when safely integrated) → smooth variable gain → peak/true-peak-aware limiter → output; the implementation must verify the real Android EQ effect's position. If an Android AudioEffect EQ operates AFTER the PCM limiter, it can reintroduce clipping. Until ordering/headroom is verified, disable simultaneous boost+positive EQ gains or apply a conservative reduction; never claim the limiter protects a later unknown stage.
- Prototype a fast-attack, controlled-release or limited-lookahead limiter with output ceiling below full-scale (candidate -1 dBFS), smooth gain changes and explicit saturation counters/gain-reduction telemetry. An instantaneous hard clip is NOT acceptable as a substitute. The candidate ceiling does not guarantee true-peak safety or downstream distortion freedom; test intersample peaks and codec/route effects.
- Process PCM without changing number of samples or timeline. Validate 16-bit and float PCM, mono/stereo, format changes, seek/pause/resume, playback speed, queue transitions, video sync and CPU/battery overhead. Avoid allocation/blocking on the audio render thread.
- Media3 audio processors work with PCM, not audio passthrough/offload. When boost is enabled, deliberately require a PCM processing route or visibly disable boost for unsupported output; avoid silently skipping the limiter. Preserve existing playback and visualizers if audio effect initialization fails, falling back to safe unity.
- Evaluate Android AudioEffect Equalizer session lifecycle and DSP order jointly with the limiter. Clean up effect on audio session changes/service stop. Device EQ presets have hardware-dependent bands, so save WMS logical frequency-to-dB profiles and remap/clamp rather than relying on device preset IDs.

Official API references (verified September 2026):
- `DefaultAudioSink.Builder#setAudioProcessors` and `setAudioProcessorChain`: https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink.Builder
- `DefaultAudioSink#setVolume` recommends 0.0–1.0: https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink
- Android Bluetooth absolute volume documentation: https://source.android.com/docs/core/connect/bluetooth/services

## 5. EQ and presets

- Probe Equalizer support and actual band count, center frequencies, valid band gain range against the current nonzero audio session. Fail gracefully (EQ unsupported, playback unaffected). Query actual Media3 callback APIs for pinned version before coding.
- EQ defaults OFF and Flat; built-in settings are *reference shapes*, not measured acoustic guarantees. Suggested five conceptual dB points for sound review: Flat `[0,0,0,0,0]`, Jazz `[1,1,0,1,1]`, Rock `[3,1,-1,2,3]`, Pop `[1,2,1,2,1]`, Classical `[1,0,0,1,2]`, Vocal `[-2,0,3,2,1]`, Bass `[4,3,0,-1,-1]`, Treble `[-1,-1,0,3,4]`. Clamp and validate on device before shipping.
- Custom named presets are local, versioned logical profiles; validate length (candidate 1–40 characters), uniqueness and delete/overwrite confirmation; preserve unsaved state. No arbitrary fixed count, but never promise infinite storage.
- Verify EQ combined with booster, avoiding gain stacking that bypasses safety limiter. Until verified, boosted and positively boosted-EQ modes are mutually exclusive.

## 6. Roadmap / gates

| Gate | Scope | Required acceptance |
|---|---|---|
| S0 | Baseline + route diagnosis | Compare WMS 100%/EQ OFF with a known-good player, Android and receiver volume, Bluetooth absolute-volume status; identify if DSP can actually help. |
| S1 | Existing PR #9 normal volume | 4th Sound tab, 0–100%, mute, persistence, mini-player; local build/unit/lint and real device PASS. This gate does **not** satisfy >100%. |
| S1B | Optional boost + limiter | Tested Media3 PCM chain, controlled up-to-provisional-200% UI, hard technical safety fallback, smooth changes and telemetry; compare quiet/loud MP3 and car Bluetooth for actual benefit, artifacts, distortion, latency and power. |
| S2 | Device-aware EQ | ON/OFF, correct band count/frequencies and vertical faders; reliable lifecycle; boost/EQ combined path safe or explicitly disallowed. |
| S3 | Presets | 8 built-ins plus My Presets, persistence/migration and clipping checks. |
| S4 | Regression and release gate | Device speaker, wired if available, Bluetooth; MP3/video, 19s seek, A-B, Next/Previous, screen-off/background, notification, FFT visualizer, changes in route. User acceptance. |

Use PS-only local flow: static review → `./scripts/local-verify.ps1 -Install` (PowerShell) → device checks. Draft PRs throughout, no heavyweight GitHub Actions during iteration, no merge to `plan/product-ui-redesign` or `main`, and no release without explicit user approval.

## 7. Current status / non-goals

- This file is the revised **design only**. Existing Draft PR #9 includes S1 baseline but **does not** amplify >100%, implement a limiter or implement EQ/presets. Do not tell the user that installing PR #9 solves their quiet car Bluetooth problem.
- User's actual car Bluetooth level/route has not yet been measured; no assurance WMS-side amplification will overcome a hardware or OS cap. Examine Android audio routes independently without root, hidden-settings patches or automatic modifications.
- Preserve Preview 1 tag/assets and independent regression Draft PR #7. Keep each feature's tests and acceptance explicit.
