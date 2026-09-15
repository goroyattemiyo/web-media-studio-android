# Audio Analysis V2 → Visualizer Renderer V3

Date: 2026-09-15  
Branch: `plan/product-ui-redesign`

Status: **PHASE A LOCAL PASS / PHASE B PENDING**

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

Renderer work has not started in this checkpoint section. V3 status and Redmi evidence
will be added only after Phase A is committed and the required renderers are verified.

Product UI Redesign remains **IN PROGRESS**, not FINAL PASS.
