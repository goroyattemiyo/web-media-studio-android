# Player Parity Pack

Date: 2026-09-15  
Branch: `plan/product-ui-redesign`

## Web/PWA parity target

This checkpoint brings the primary Web/PWA playback controls to Android without replacing
the proven Media3 architecture: speed, repeat, shuffle, and an advanced A-B loop. Existing
timeline seek, ±10 seconds, previous/next, queue ordering, position restore, visualizers,
and system playback controls remain unchanged.

## Implementation

- Playback speed: 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, and 2.0x, applied through
  `MediaController.playbackParameters` / the service-owned ExoPlayer.
- Repeat: Off, All, and One, mapped directly to Media3 repeat modes.
- Shuffle: toggles `shuffleModeEnabled`; it does not replace the active queue or force a
  media transition.
- A-B loop: Set A, Set B, Start/Stop loop, and Clear. B is accepted only when `A < B`.
  The loop is owned by `PlaybackService` and uses a 200 ms service-side check only while
  a loop is active, so it continues during background and screen-off playback without a
  Compose polling loop.

## UI placement

Primary Player controls remain Previous, -10 seconds, Play/Pause, +10 seconds, and Next.
Speed, repeat, shuffle, and A-B loop are inside the collapsible **再生オプション** section.
Mini Player remains compact and intentionally has none of these new controls.

## Persistence and MediaSession alignment

Speed, repeat mode, and shuffle state are persisted in the existing `wms_playback`
preferences owned by `PlaybackService`, then restored before its saved queue. This keeps
the ExoPlayer/MediaSession service as state owner. A-B points are intentionally session-only.

A-B actions are MediaSession custom commands. Compose sends commands through its existing
`MediaController`; it does not manage playback or run a second playback loop.

## Unit tests

`PlaybackOptionsTest` covers:

- supported speed values and normalization
- Media3 repeat-mode mapping
- shuffle toggle handling
- A-B validation (`A < B`), enablement, loop boundary behavior, clear, and resetting A

## Verification

- Final local `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` passed
  on 2026-09-15 after the complete batch.
- Redmi 12 5G accepted the debug APK. Full Player displayed the five primary transport
  actions and the collapsed advanced section; expanding it showed the speed, repeat,
  shuffle, and A-B controls with invalid B/loop controls disabled until A is set.
- User confirmed the Redmi 12 5G device verification as OK, including the retained
  background/screen-off, notification-control, playlist, and position-restore regression
  scope. The final Designer Polish checkpoint will repeat this as the final UI sweep.

## Deferred items

- richer repeat/shuffle icons and accessibility descriptions
- an explicit A-B indicator in the MediaSession notification (not required for normal
  system transport controls)
- final all-speed audio and full playlist/device regression sweep as part of final polish

This is not Product UI Redesign FINAL PASS.
