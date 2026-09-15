# Audio Analysis V2 → Visualizer Renderer V3

Date: 2026-09-15  
Branch: `plan/product-ui-redesign`

Status: **PHASE A LOCAL PASS / PHASE B DEVICE SMOKE PASS**

## Previous pseudo-spectrum problem

V2 treated absolute PCM amplitude in consecutive time slices as `spectrum`. Those values
contained no frequency transform, so bass, mid, high, spectral history, and transient
behavior could not be distinguished reliably. Renderer diversity was therefore limited
even when the geometry differed.

## Phase A architecture

```text
Media3 / ExoPlayer
  -> TeeAudioProcessor.AudioBufferSink
     -> PCM16 or PCM-float decode only
     -> fixed 2048-frame L/R ring (no callback-time arrays or boxed samples)
  -> single bounded analysis worker
     -> stable latest-window snapshot (contested snapshots are dropped)
     -> Hann window
     -> in-repo radix-2 O(N log N) FFT
     -> 48 logarithmic bins + bands + features + attack/release
  -> AudioAnalysisFrame
  -> StateFlow, at most 25 updates/second
  -> Compose visualizer
```

Playback remains owned by the existing `MediaLibraryService`, `MediaSession`, and
ExoPlayer. Analysis never seeks, pauses, changes the queue, or creates another player.

### Threading, buffering, and allocation

- The audio callback decodes the supplied buffer with absolute reads, preserving its
  position. It writes directly into preallocated primitive L/R rings.
- FFT, snapshot reduction, smoothing, and frame creation never execute on the audio
  callback.
- The worker is single-threaded, permits only one pending analysis job, and publishes at
  no more than 25 Hz. Backpressure drops visual frames rather than queueing work.
- A seqlock-style counter lets the worker reject a ring snapshot that overlaps an audio
  write. The audio callback never waits for analysis.
- Worker snapshot arrays, Hann coefficients, FFT real/imaginary arrays, magnitudes,
  previous bins, and smoothing arrays are reused. Only the immutable arrays placed in a
  published frame are copied.
- `flush` increments a format generation, clears the ring/frame, and serializes analyzer
  reset through the worker. A result from an older generation cannot be published.
- `PlaybackService.onDestroy` closes the analyzer worker after releasing the player.

### FFT design

- Window: 2048 mono-downmixed samples. At 48 kHz this represents about 42.7 ms; at
  44.1 kHz about 46.4 ms.
- Window function: Hann, with magnitude normalization by the Hann coefficient sum.
- Transform: a small in-repository iterative Cooley–Tukey radix-2 FFT. No O(N²) DFT and
  no external dependency are used.
- PCM16 is normalized with divisor 32768; PCM float is clamped to `[-1, 1]`.
- Mono uses the left ring only. Stereo FFT uses `(L + R) / 2`, while both original
  channels remain separately available for Lissajous rendering.

### Sample rate, logarithmic bins, and bands

FFT-index frequency is always calculated as `index * sampleRate / windowSize`. Forty-eight
logarithmic bins span 20 Hz to `min(20 kHz, Nyquist)`, retaining more display resolution
in low frequencies than linear FFT-index buckets.

Band energy uses the same sample-rate-derived indices:

- bass: 20–160 Hz
- low mid: 160–500 Hz
- mid: 500–2000 Hz
- high: 2000 Hz to `min(20 kHz, Nyquist)`

Each band uses root-sum-square FFT magnitude. This avoids diluting a tone merely because
the high band contains more FFT indices.

### Centroid, flux, onset, and smoothing

- Spectral centroid is the power-weighted mean frequency in Hz.
- Spectral flux is the mean positive difference between the current and previous raw
  logarithmic bins.
- `onsetStrength` combines maximum positive bin change, mean flux, and positive peak
  change. The first analyzed frame intentionally cannot trigger onset, preventing a false
  flash when playback starts; a later silence-to-signal transition can.
- FFT bins, RMS, peak, and named bands use separate attack/release EMA coefficients:
  fast attack (`0.72`) and slower release (`0.16`). Flux remains an unsmoothed transition
  feature so a steady tone falls back close to zero.
- This is transient detection for visual mapping, not a BPM or beat-grid detector.

## Phase A synthetic tests

The JVM suite covers silence, 80 Hz bass, 300 Hz low-mid, 1 kHz mid, 8 kHz high,
low/high centroid ordering, steady-tone flux, silence-to-tone onset, 44.1/48 kHz mapping,
stereo waveform separation, and PCM16/PCM-float sink paths.

Phase A verification result on 2026-09-15: `testDebugUnitTest`, `lintDebug`, and
`assembleDebug` all PASS using the Gradle 8.13 wrapper and JDK 17.

## Phase B renderer status

Code checkpoint: `2c36719879a363b8f75ab1893a638b1bc9370049`

Phase B replaces seven V2 Canvas renderers with dedicated V3 implementations while
preserving every canonical DataStore ID:

| Persisted ID | V3 renderer | Primary analysis inputs |
| --- | --- | --- |
| `pulse` | Liquid Metaballs | bass, mid, high, onset |
| `orbit` | Flow Field | bass, mid, high, centroid, onset, FFT bins |
| `particles` | Voronoi Shards | FFT bins, flux, onset |
| `wave` | Spectrogram Waterfall | 48-bin FFT history |
| `bars` | Wireframe Terrain | 48-bin FFT history |
| `neon-tunnel` | Glyph Rain | bass, mid, high, onset |
| `oscilloscope` | Phosphor Lissajous | separate left/right waveforms, high band |

`rainbow-ring`, `spectrum-city`, `kaleido`, `emblem`, and `minimal` retain their V2
renderers for this checkpoint. Their IDs and saved preferences remain valid.

### Rendering and compatibility

- API 33+ uses Android `RuntimeShader` for Liquid Metaballs and Voronoi Shards.
- API 26–32 uses Compose Canvas fallbacks for both shader-backed modes.
- Flow Field and Glyph Rain own bounded 30 Hz state loops which are canceled when their
  composables leave composition.
- Phase animation is limited to renderers that need time even when no new PCM frame is
  published. Spectrum and waveform-history renderers update only from analysis frames.
- Reduced motion replaces the selected renderer with the static `minimal` renderer and
  does not start V3 animation loops.
- Appearance uses static representative previews, so opening the selector does not start
  twelve live visualizers.
- Pausing playback clears the last PCM analysis frame to prevent stale energy from
  remaining visible.

### Phase B local verification

Local Windows verification on 2026-09-15 JST passed:

- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:assembleDebug`
- canonical persisted-ID compatibility test
- explicit mapping test for all seven V3 renderer types

The locally produced dirty-checkpoint APK is:

`dist/wms-android-0.8.0-a8-dev-distribution-v12-bdda22468776-dirty-debug.apk`

SHA-256:

`2EF84F0CAF8A4DE1F146B12AFF3E192F3D6DF4EA28448309C86DA633B9DB03E7`

### Phase B target-device smoke verification

Verified on Redmi 12 5G / Android 15 (API 35) on 2026-09-15:

- dirty-checkpoint APK installed with `adb install -r`; package version remained
  `0.8.0-a8-dev-distribution` / versionCode `12`
- the update retained the Room database, DataStore preferences, selected media, artwork,
  and existing playback position
- all seven V3 modes were selected in Full Player and entered composition without a
  process restart, fatal exception, or RuntimeShader error
- both API 33+ shader modes, Liquid Metaballs and Voronoi Shards, rendered without a
  shader-initialization failure
- a device screenshot confirmed Voronoi Shards produced a non-empty Home media surface
- Wireframe Terrain changed from its paused empty grid to populated FFT history during
  muted playback, demonstrating the real PCM -> FFT -> renderer path
- enabling reduced motion disabled the visualizer choices; it was then turned off
- screen-off playback remained `PLAYING` through MediaSession and a screen-off media
  pause returned the session to `PAUSED`
- the original `particles` preference, paused state, and media volume were restored after
  verification

This is a renderer/runtime smoke pass, not a final human aesthetic sign-off for every
mode. Product UI Redesign as a whole must not be called LOCAL PASS until its remaining
designer-polish and preservation checks pass.

Product UI Redesign remains **IN PROGRESS**, not FINAL PASS.
