# WMS Android — Sound Control design (v0.1)

Date: 2026-09-17 JST
Status: **Design proposal recorded / no implementation**
Scope: WMS Android (`goroyattemiyo/web-media-studio-android`). Based on `plan/product-ui-redesign` at `3aaffa95479eaaffc05d99721e6bd6e9f3e2a145`. Keep the separate playback regression PR #7 independent. Do not merge to `main`, run GitHub Actions or release without approval.

## 1. Agreed product direction

- Change the bottom navigation from **Home / Library / Player** to **Home / Library / Player / 音質** (Sound). Do not overload Now Playing with EQ controls.
- The Sound tab contains WMS app volume, EQ ON/OFF, a **horizontal row of vertical EQ faders**, built-in presets and custom presets. Keep the existing mini-player visible on this tab; navigating to Sound must not interrupt playback.
- Initial built-in preset catalog: **8 entries**: フラット, ジャズ, ロック, ポップス, クラシック, ボーカル, 低音重視, 高音重視.
- Provide user-named **custom presets** (save as / select / rename / delete), not a fixed total of eight presets. No arbitrary small count limit, but validate names and handle storage limits gracefully. Built-ins are immutable.
- These are WMS playback controls only, not controls for other apps, a Bluetooth volume-cap bypass, or a promise of amplified volume.

## 2. User experience / layout

```
Sound Control (音質)
  WMS音量                         80%
  [mute]  ─────────●──────────  0–100%
  イコライザー                  [ON/OFF]
  プリセット [current ▼] [保存 / 名前を付けて保存]
                +6 dB
            │  │  │  │  │
            │  │  │  │  │   vertical faders
         0 ─┼──┼──┼──┼──┼─   clear zero reference
            │  │  │  │  │
                −6 dB
           bass → treble
  マイプリセット [select ▼] [rename] [delete]
  [persistent mini-player]
  [Home] [Library] [Player] [音質]
```

- Show the five-fader layout as a **design target**, not as an assertion that every phone provides five EQ hardware bands. Actual fader count, center frequencies and gain limits must reflect what the active effect reports; if fewer bands are available, use that smaller number rather than presenting fictitious independent controls. Keep the vertical layout on all supported devices.
- Each fader displays its real center frequency and current dB gain. Center/zero marker, ± gain labels, touch targets and accessible numeric alternatives must be available; allow a reset-to-flat action.
- Preset selection uses a compact dropdown/sheet, not eight permanently visible buttons. Separate built-in and My Presets sections. The tab shows the selected preset name; any manual fader edit changes the selected state to `カスタム（未保存）` without silently modifying a saved preset.
- Save-as asks for a name; trim whitespace, reject empty names, duplicate names require explicit rename/overwrite choice, validate length (proposed 1–40 characters) and display persistence errors. Rename/delete need confirmation only when destructive, and deletion restores Flat or retains the current unsaved values with an explicit label (decide during UI review).
- When there is no playing media, volume remains editable and persisted; EQ capability-dependent actions remain disabled or clearly labeled until an audio session is available. The mini-player appears whenever the current WMS navigation would ordinarily show it and media exists.

## 3. Volume semantics

- WMS volume range: 0–100%; map to Media3/ExoPlayer player-volume gain 0.0–1.0. **100% is unity**, not volume boost.
- Initial WMS volume: 100%. Persist changes across track changes, app restart and service recreation.
- Mute stores the last nonzero value and restores it on unmute; changing the slider from 0 un-mutes. A device/system volume change does not silently rewrite WMS volume.
- Maintain normal Android volume controls, audio focus, noisy-audio handling and MediaSession notification/system transport buttons. Do not modify global device volume or hidden Bluetooth/absolute-volume settings.

## 4. EQ capabilities and presets

- Probe `android.media.audiofx.Equalizer` support on the player's audio session ID (nonzero/valid) and query actual band count, center frequencies, and level range at runtime. Verify required Media3 audio-session callbacks against the pinned Media3 version before implementation.
- EQ defaults OFF with Flat selected; OFF must bypass the EQ and preserve chosen settings for re-enable. Selecting a preset explicitly may switch EQ ON (document in the UI). Set Flat to zero gain for all supported bands.
- Eight built-ins are **logical sound profiles**, not a promise of universal dB results. Store a versioned frequency-to-gain profile rather than relying on device-specific preset IDs. On each device, map profile gains to supported bands by center frequency with clamping to the reported supported range. Any interpolation or clamping must be tested and documented; no unsupported band can be adjusted.
- Preset reference shapes for sound review (five conceptual low → high points, in dB, **not yet acoustically validated**): Flat `[0,0,0,0,0]`; Jazz `[1,1,0,1,1]`; Rock `[3,1,-1,2,3]`; Pop `[1,2,1,2,1]`; Classical `[1,0,0,1,2]`; Vocal `[-2,0,3,2,1]`; Bass `[4,3,0,-1,-1]`; Treble `[-1,-1,0,3,4]`. Adapt to real supported ranges; confirm musical tuning and distortion risk on device before shipping.
- Save custom presets as logical frequency/gain values plus schema version and user name; on a device with different hardware capabilities, remap and clamp rather than mislabel saved gains as identical physical results. Do not promise unlimited device storage.
- Only affect WMS audio, including video audio when that session supports it. Do not install device-global effects. Support devices that report no EQ by showing a clear unsupported state while preserving playback, volume and saved presets.
- Prevent clipping: avoid adding a >100% digital amplifier or positive preamp in initial scope; listen for clipping and reduce individual preset boosts if necessary. Include an obvious Flat/OFF escape route.

## 5. Ownership / lifecycle

```
Compose Sound tab + mini-player (UI)
       ↕ commands and observed playback state
MediaController / MediaSession
       ↕
PlaybackService — single authoritative owner
       ├── volume and mute state
       ├── EQ capability, enabled flag, band levels, preset selection
       ├── lifecycle-bound EQ effect on current ExoPlayer audio session
       └── persisted configuration (DataStore or established app preferences)
ExoPlayer → audio output
```

- Avoid independent remembered UI truth: refresh controls from the service on connection and screen re-entry, and distribute changes to all UI surfaces. Validate MediaSession custom command availability and choose an observable state channel compatible with existing same-process architecture before coding.
- On player audio-session change, detach/release the old effect and safely attach the new one. Release effects on service destruction and handle missing sessions and EQ initialization errors without terminating playback.
- Confirm how the existing FFT visualizer/analysis renderer interacts with AudioEffects and session changes; preserve visuals and video playback. Avoid altering A-B, position, playlist or audio focus logic.
- Version the persistence schema and write tests for serialization, preset migration, clamping, duplicate names and fallback behavior.

## 6. Roadmap, verification and gate conditions

| Gate | Work | Acceptance |
|---|---|---|
| S0 | Capability spike, inspect Media3 session/volume API and actual device EQ information | Document session ID, band count, gain range, supported routes, known limits; no playback regression |
| S1 | 4th bottom tab + persisted app volume/mute | Full/mini/Sound state agrees; 0/100 and mute restore; track change, screen off and restart preserve intended values |
| S2 | EQ ON/OFF + vertical actual-band UI + Flat | Service-owned state, safely attached/released effect; unsupported device continues playing |
| S3 | 8 built-ins + custom save/select/rename/delete | No accidental overwrite; persistence/restart/mapping/clamping and manual-edit unsaved state tested |
| S4 | Physical route and regression testing | Device speaker, wired if available, Bluetooth; MP3/video, seek beyond 19s, A-B, Next/Previous, screen-off/background, notification, FFT visualizer |

Verification workflow: static review → PS local unit tests/lint/debug build (`.\scripts\local-verify.ps1 -Install`) → real-device tests → user acceptance. Prefer Draft PR, keep heavy GitHub Actions off during iterative development, do not merge into `plan/product-ui-redesign`/`main` or release without explicit approval. All instructions and commands given to the user should be PowerShell.

## 7. Explicitly out of scope / pending decisions

- No claim that WMS volume or EQ fixes an Android/Xiaomi/Bluetooth hardware or system-level volume defect. Diagnose independently by comparing WMS 100%/Flat/OFF against another player and the device's own volume; note connected-device volume and Bluetooth absolute-volume interactions.
- No system-wide EQ, root patch, hidden settings modification, sound boost above unity or hearing-safety override.
- Before coding, confirm actual UI width and accessibility for variable band counts, exact storage solution, error strings, cross-process expectations and whether saved custom profiles should sync/export (initial scope: device-local only).

No runtime behavior is changed by this document. PR #7's 19-second seek fix remains a separate Draft pending its own acceptance checks.
