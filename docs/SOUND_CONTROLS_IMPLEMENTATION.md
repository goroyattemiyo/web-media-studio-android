# Sound Control implementation candidate — local verification gate OPEN

Date: 2026-09-17 JST
Branch: `feat/sound-controls-full-local`, Draft PR #10 based on Draft PR #9 (which depends on Draft playback fix PR #7).
Design: `docs/AUDIO_CONTROLS_DESIGN.md` on `docs/audio-controls-design` (Draft PR #8).
**No Android build, APK installation, car Bluetooth test, or real-device acceptance has been confirmed for this candidate.** Never advertise it as a verified volume-defect fix.

## Implemented in source

- Fourth 音質 tab (inherited from PR #9), persistent mini-player on that tab.
- WMS volume, mute and last-audible restore, local settings persistence.
- Explicit BOOST toggle, optional 100–200% requested volume; default 100%, BOOST OFF. Media3 volume stays 0–1; extra gain runs in a separate PCM processor.
- Linked multi-channel sample-peak limiter with fast attenuation, slower release, smooth gain ramp, -1 dBFS sample ceiling, read-only limited-frame count. No duration/sample-count change; original audio remains available to the FFT visualizer.
- BOOST drops to <=100% on output-device add/remove, service recreation, unavailable PCM, or explicit OFF. If Android device-route monitoring fails, BOOST is disabled.
- Android `Equalizer` on a valid current audio session, runtime physical band count/frequencies/gain range, vertically rotated faders and honest unsupported state; release on session change/destroy.
- Eight logical built-in profiles, device-frequency interpolation/clamping; save/select/rename/delete for locally stored custom profiles; versioned JSON and input validation. Manual edits are not silently applied to saved profiles.
- BOOST and platform EQ are mutually exclusive **by design** until downstream DSP ordering is proven. Turning one on while the other is active returns an error; users must turn the other OFF first.

## Limitations and safety

- 200% is a provisional engineering UI cap, NOT tested loudness or distortion-free output. Begin actual hearing tests at 100%, then 125%/150%, with car receiver at a conservative setting.
- Limiter controls discrete sample peaks before Android output stages only. It does not prevent intersample clipping, Bluetooth codec issues, distortion already in recordings, amplifier/speaker saturation or system/receiver volume restrictions. For brick-wall loud files, perceived loudness may not increase.
- A platform EQ effect can be downstream of PCM processing. Therefore simultaneous BOOST and EQ is intentionally blocked, even for EQ cuts, until route-specific behavior can be verified.
- PCM processor is inactive for unsupported formats and reports BOOST unavailable. No system-wide volume, Android developer settings, permission escalation, root or Bluetooth absolute-volume changes.
- Active playback must remain unchanged if EQ effect creation fails; the UI displays EQ unsupported. Test different device routes, audio formats and visualizer operation before distribution.

## PowerShell local verification (run on user's Windows development PC)

```powershell
Set-Location 'C:\dev\web-media-studio-android'
if (git status --porcelain) { throw '未保存の作業があります。先に保護してください。' }
git fetch origin
if ($LASTEXITCODE -ne 0) { throw 'fetch失敗' }
git switch feat/sound-controls-full-local
if ($LASTEXITCODE -ne 0) { git switch --track origin/feat/sound-controls-full-local }
if ($LASTEXITCODE -ne 0) { throw '切替失敗' }
git pull --ff-only origin feat/sound-controls-full-local
if ($LASTEXITCODE -ne 0) { throw '更新失敗' }
New-Item -ItemType Directory -Force .\dist | Out-Null
& .\scripts\local-verify.ps1 -Install *>&1 | Tee-Object -FilePath .\dist\sound-full-local-verify.log
if ($LASTEXITCODE -ne 0) { throw 'ローカル検証失敗。ログを確認してください。' }
```

Review `BUILD SUCCESSFUL`, `Local verification PASS`, `Install/update verification PASS` and reported APK hash. Do not state PASS until the script output is available; PowerShell pipeline status must also be checked against reported markers and the actual script exit code.

## Real-device acceptance checklist (not yet run)

1. Open Sound: four tabs, mini-player retained, music continues on tab navigation, correct UI at narrow width.
2. Normal WMS level 0/35/100%, mute and return to last level, MP3/video and app restart: normal volume persists.
3. Start MP3: BOOST OFF and EQ OFF. Turn BOOST ON and try 125%, then 150% only if distortion-free; listen to quiet and already-loud recordings, watch limited-frame count. Verify 100% reset and mute restoration. The 200% endpoint is experimental, not a mandated listening level.
4. BOOST ON -> unplug/replug or change Bluetooth route: BOOST automatically OFF and level <=100. Stop/restart app while BOOST set: it must start <=100 and not arm itself.
5. EQ ON with BOOST OFF: real device band/frequency labels, vertical faders, Flat, all eight presets, manual edit shows unsaved, custom save/select/rename/delete, duplicated names refused, app restart loads saved state. Unsupported EQ device must continue playing.
6. Verify BOOST refuses to enable while EQ ON, EQ refuses while BOOST ON; turning other effect OFF permits requested mode.
7. Regression: normal and mini seeking beyond 19 seconds, A-B clearing/track change, Next/Previous, Library, background/screen-off, notification controls, visualizer, video sync, playback speed and repeat.
8. Compare same locally saved MP3 on WMS 100%/EQ OFF with another player at the same Android and car receiver levels to distinguish WMS-side quiet source vs OS/device restriction.

Keep Draft PR #7, #8, #9 and #10 unmerged; no GitHub Actions or release without approval. Capture logs and actual observations before closing any acceptance checkbox.
