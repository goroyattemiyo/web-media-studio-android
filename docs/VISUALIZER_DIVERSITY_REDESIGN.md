# Visualizer Diversity Redesign

Date: 2026-09-15  
Branch: `plan/product-ui-redesign`

## Duplicate audit

The former Canvas renderer had three direct duplicate families: Oscilloscope/Wave
(single line path), Spectrum City/Bars (vertical bars), and Rainbow Ring/Kaleido
(radial spokes). Remaining modes shared the same circle-plus-lines fallback. The V2
renderer keeps the twelve persisted IDs but gives each one a distinct screen language.

## V2 visual languages and audio mapping

| Persisted ID | V2 language | Primary mapping |
| --- | --- | --- |
| `minimal` | Minimal | low-motion center line |
| `emblem` | Emblem Reactor | level expands concentric reactor rings |
| `oscilloscope` | Phosphor Lissajous | signed waveform deforms XY trace |
| `wave` | Spectrogram Waterfall | spectrum history scrolls as colour rows |
| `spectrum-city` | Spectrum City | spectrum controls skyline height |
| `pulse` | Liquid Metaballs | bands expand overlapping liquid blobs |
| `orbit` | Flow Field | phase and level bend short vector trails |
| `particles` | Voronoi Shards | bands move radial cell-shard boundaries |
| `bars` | Wireframe Terrain | spectrum forms receding terrain contours |
| `kaleido` | Strange Attractor | audio parameters deform an iterative path |
| `neon-tunnel` | Glyph Rain | spectral thresholds illuminate glyph-grid drops |
| `rainbow-ring` | Hyper Tunnel | level/phase animate perspective ring depth |

## Legacy compatibility

No visualizer ID was removed or renamed. Existing Appearance DataStore values therefore
continue to resolve. `VisualizerMode.fromPersistedId` supplies a safe Emblem fallback for
unknown future/legacy values. The UI uses V2 display names rather than raw IDs.

## Performance tier

All implemented V2 modes are Tier 1 Compose Canvas. No external visualizer dependency,
MilkDrop preset, shader, asset, or copied code is included. PCM analysis is capped at
20 Hz before it reaches Compose; only the Waterfall retains a bounded 18-frame spectrum
history. Playback remains owned by Media3.

Tier 2 candidates deferred: GPU feedback/liquid simulation, true Voronoi distance-field,
3D GPU terrain, boids, and stereo-accurate scope. These require an explicit renderer and
thermal/frame-time budget rather than a dependency added opportunistically.

## Inspiration research

The design draws on the *ideas* of MilkDrop/projectM's beat-synchronised preset scenes,
Butterchurn's WebGL2 rendering model, and TouchDesigner’s practice of mapping normalized
audio control channels to visual parameters. No source, shader, preset, or asset is copied.
Butterchurn is MIT-licensed but is not used; projectM is LGPL-2.1 and is not linked.

References: [Butterchurn](https://github.com/jberg/butterchurn),
[projectM](https://projectm-visualizer.org/docs), and
[TouchDesigner audio-reactive workflow](https://joemighty.github.io/CreativeCoding/touchdesigner/beginner/06-audio-reactive-visuals/).

## Appearance background image

Appearance can now copy a selected device image into WMS app storage and persist its
managed path plus 0–24 dp blur in the existing Appearance DataStore. This prevents a
gallery URI permission change from breaking the selected background.

## Verification

User confirmed the current device verification as OK on 2026-09-15. Final local full
test/lint/build results are recorded with the implementation commit.
