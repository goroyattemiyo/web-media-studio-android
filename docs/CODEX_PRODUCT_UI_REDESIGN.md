# WMS Android — Product UI Redesign for Codex

Date: 2026-09-14 JST

Branch: `plan/product-ui-redesign`

Base: `feat/gate-a8-development-distribution`

Status: DESIGN / IMPLEMENTATION INSTRUCTION

## 0. Why this redesign exists

Gates A0–A8 proved the core product capabilities, but the current UI still feels like a feature/demo shell rather than a finished media-player product.

The current usability problems are product-level, not isolated button-placement issues:

- currently playing media is not visually dominant,
- the Mini Player is too weak,
- visualizers are no longer prominent in everyday playback,
- video is hidden unless the user explicitly opens Now Playing,
- media rows use a generic WMS icon where a thumbnail/artwork preview should be used,
- Search / Import / Library / Playlist / Player feel like separate technical flows rather than one continuous user experience,
- too many implementation details are exposed to normal users,
- navigation requires the user to understand WMS internals.

This redesign must turn the existing A0–A8 implementation into a product that is immediately understandable as a media player.

Do not remove proven A0–A8 capability merely to simplify the UI. Reorganize and hide complexity behind progressive disclosure.

## 1. Product goal

WMS must feel like a finished native media player whose core loop is obvious without explanation:

`Find or receive media -> add to WMS -> see it in Library -> play -> control from anywhere`

The product must prioritize the media itself over technical controls.

Primary UX principle:

> The currently playing media must remain visible, identifiable, and controllable throughout the app.

For audio:

- show real artwork/thumbnail where available,
- show the WMS visualizer as a first-class playback surface,
- expose useful playback progress and transport controls.

For video:

- show the actual video surface in the normal product flow,
- do not require the user to open Now Playing just to see the picture.

Now Playing remains a full-screen / expanded playback experience, not the only place where playback becomes visible.

## 2. Non-goals and protected boundaries

Do not change or weaken these existing product/security boundaries:

- permitted/public or otherwise authorized media only,
- rights confirmation remains mandatory before local acquisition,
- Search must never auto-download,
- no cookie/login bypass,
- no proxy rotation,
- no DRM/auth/access-control bypass,
- no arbitrary yt-dlp flags in normal UI,
- existing Room/DataStore/media-file persistence must remain intact,
- existing background playback / MediaSession behavior must remain intact,
- existing provider classification behavior must remain intact,
- existing update/install continuity must remain intact.

Do not run GitHub Actions.
Do not create a GitHub Release.
Do not merge to `main`.
Use local test/lint/build and required real-device checks only until explicit user approval.

## 3. Navigation redesign

Replace the current mental model with three primary destinations:

1. **Home**
2. **Library**
3. **Player**

### Home

Home contains Search at the top and the normal discovery/intake experience.

Search is not a separate conceptual product area. It is the primary action of Home.

### Library

Library contains both:

- all saved media,
- playlists.

Do not force Playlist to exist as a separate top-level navigation destination.

Recommended internal Library tabs:

- `All`
- `Playlists`

### Player

Player opens the expanded Now Playing experience.

Now Playing is still reachable by tapping the Mini Player.

### Overflow / Settings

Appearance, Settings, and Developer Tools stay outside the primary bottom navigation.

Developer Tools must remain clearly separated from normal product use.

## 4. Global Current Media concept

Create one reusable current-media presentation model shared by Home, Library, Mini Player, and Player.

The UI must never fall back to unrelated placeholder information when real media metadata is available.

At minimum, expose:

- media id,
- title,
- author/artist when available,
- provider/source label when useful,
- media type (audio/video),
- artwork/thumbnail,
- duration,
- current position,
- playing/paused state,
- previous availability,
- next availability.

Playback state must remain driven by the existing Media3 / MediaSession architecture.

Do not create a second playback state machine in Compose.

## 5. Thumbnail / artwork policy

Replace generic WMS icons beside media titles whenever real media imagery is available.

Priority order:

1. source/provider thumbnail captured during Probe/Search/acquisition,
2. embedded audio artwork,
3. generated representative video frame for local video,
4. WMS artwork only as the final fallback.

This policy applies to:

- Search results,
- Recently Added,
- Library rows/cards,
- Playlist rows,
- Mini Player,
- full Player / Now Playing.

Persist thumbnail/artwork metadata so the UI does not need to re-resolve remote media every time the Library renders.

If schema evolution is required, use an explicit Room migration. Do not use destructive migration.

## 6. Home screen

Home should feel like a media-player home screen, not a diagnostic intake screen.

Recommended structure:

```text
┌──────────────────────────────┐
│ ◇ WMS                    ⋮   │
│                              │
│ 🔎 Search media or paste URL │
│                              │
│ CURRENT MEDIA                │
│ ┌──────────────────────────┐ │
│ │ audio -> visualizer      │ │
│ │ video -> video surface   │ │
│ └──────────────────────────┘ │
│                              │
│ Recently added               │
│ [thumb] Title                │
│         Artist               │
│ [thumb] Title                │
│         Artist               │
│                              │
│ Mini Player                  │
│ Home      Library     Player │
└──────────────────────────────┘
```

### Current Media Stage

When audio is active:

- show artwork/thumbnail,
- show the selected visualizer prominently,
- visualizer animation must again be visible in normal playback,
- respect reduced-motion/minimal preference.

When video is active:

- show the actual Media3 video surface in this stage,
- expose tap-to-expand into Player,
- playback must remain owned by the existing service/session.

Do not force the user into Now Playing just to see video.

When nothing is loaded, the stage may collapse or show a tasteful WMS empty state. Do not consume a large blank area unnecessarily.

### Recently Added

Use visual media rows/cards with thumbnails, not generic icons.

Primary action should be tapping the item to play/open it.

Avoid exposing file-system or extractor terminology.

## 7. Search results

Search result cards must be visual and immediately understandable.

Recommended card:

```text
[thumbnail]  Title
             Artist / Author
             Provider

[ + Add to WMS ]   [ ▶ Preview / Open ]
```

Keep canonical URL internally, but do not make the URL the visual focus.

Primary action is `Add to WMS`.

Secondary action may open/preview the source when appropriate.

Do not expose Probe as a user-facing action.

## 8. Import UX — simple by default

The current format capability is valuable, but normal users should not have to make many technical decisions every time.

Default path should be short:

`Add to WMS -> confirm metadata/rights -> Save`

Default audio behavior:

- MP3 192 kbps unless the current user preference says otherwise.

Advanced choices should be behind expandable details / advanced options:

- Audio / Video,
- MP3 / M4A / MP4,
- quality/preset,
- target playlist.

Do not remove these capabilities; move them out of the default decision path.

If the active playlist is a valid destination, it may be preselected and shown clearly.

The rights confirmation must remain explicit.

## 9. Mini Player redesign

The current Mini Player is insufficient if it only exposes Play/Pause and Next.

Required Mini Player information:

- thumbnail/artwork on the left,
- title,
- author/artist or useful secondary label,
- current position / total duration,
- seek/progress bar,
- Previous,
- Play/Pause,
- Next.

Recommended shape:

```text
┌──────────────────────────────┐
│ [thumb] Room 214             │
│         Elias Ray            │
│ 01:42 ━━━━━●━━━━━━ 04:18     │
│       ⏮     ❚❚     ⏭       │
└──────────────────────────────┘
```

Tapping the non-control body opens the full Player.

Keep controls touch-friendly and accessible.

Do not overload Mini Player with Appearance, visualizer selection, playlist editing, or developer controls.

## 10. Library redesign

Library must look like a media collection, not a database/file list.

Recommended structure:

```text
Library

[ All ] [ Playlists ]

Recently added
[thumb] Title
        Artist
        4:18

[thumb] Video title
        Video
        24:16
```

Requirements:

- thumbnails/artwork are first-class,
- show useful metadata without clutter,
- clearly differentiate audio/video when needed,
- tapping a playable item should play/open it,
- secondary actions belong in an overflow/context menu,
- Playlist create/rename/delete/reorder functionality remains available under the Playlists section,
- missing-file states remain clear and safe.

## 11. Player / Now Playing redesign

### Audio Player

Visualizer returns as a first-class product surface.

Recommended hierarchy:

```text
← Title                         ⋮

        VISUALIZER
       / ARTWORK

Title
Artist / Provider

01:42 ━━━━━●━━━━━━ 04:18

     ⏮      ❚❚      ⏭

     shuffle / repeat

Up next / active playlist
```

The visualizer must use the existing selected visualizer and PCM-analysis boundary from Gate A6.

Do not regress to a decorative/static placeholder when audio-reactive data is available.

### Video Player

The video surface is the hero element.

Recommended hierarchy:

```text
← Video title                   ⋮

┌──────────────────────────────┐
│                              │
│          VIDEO               │
│                              │
└──────────────────────────────┘

12:42 ━━━━━●━━━━━━ 24:16

       ⏮     ❚❚     ⏭
```

Support orientation/fullscreen polish if practical, but do not make that a prerequisite for the first redesign checkpoint.

## 12. Visualizer requirements

The visualizer must no longer be hidden as an optional/developer-like feature.

For audio playback:

- selected visualizer should be visible on Home Current Media Stage,
- selected visualizer should be prominent in full Player,
- Mini Player may remain non-visualizer for compactness,
- reduced motion must force or strongly prefer `minimal`, consistent with existing preference behavior.

Preserve all existing canonical visualizer IDs and preference compatibility.

## 13. Appearance continuity

Preserve the existing canonical skin system and DataStore settings.

The redesign must work under all canonical skins.

Do not design only for `midnight-neon` and leave other skins unreadable.

Acceptance must include at least:

- dark canonical skin,
- light skin,
- one stylized/retro skin,
- reduced-motion mode.

## 14. Implementation architecture requirement

`MainActivity.kt` has grown too large and must not become the permanent home of the redesigned product UI.

Refactor UI into screen-level and reusable Compose components while preserving behavior.

Suggested direction:

```text
ui/
  navigation/
  home/
  library/
  player/
  importsheet/
  components/
    CurrentMediaStage
    MiniPlayer
    MediaThumbnail
    MediaRow
    PlaybackControls
    SeekBar
```

Exact package names may differ, but responsibilities must be split cleanly.

Do not perform an unrelated architecture rewrite of repositories/services/playback just for aesthetic reasons.

The goal is UI separation and product UX clarity, not replacing proven A0–A8 backend architecture.

## 15. Preservation checklist

Before considering the redesign locally complete, verify no regression in:

- keyword Search,
- pasted URL intake,
- Android share URL intake,
- Probe -> rights confirmation -> acquisition,
- MP3 acquisition/playback,
- M4A acquisition/playback,
- MP4 acquisition/video playback,
- managed foreground acquisition/cancel,
- Library persistence,
- Playlist persistence/order,
- background/screen-off playback,
- notification/system Play/Pause/Previous/Next,
- queue/position restore,
- appearance persistence,
- visualizer persistence,
- provider classification/error states,
- update install retaining Room/DataStore/managed media.

## 16. Product UI acceptance criteria

The redesign reaches **LOCAL PASS** only when the following are true on the target Android device.

### Navigation

- [ ] primary navigation is understandable as Home / Library / Player,
- [ ] playlists are reachable naturally inside Library,
- [ ] Developer Tools are not exposed in normal primary navigation.

### Home

- [ ] Search remains immediately accessible,
- [ ] current audio shows real artwork/thumbnail and visible visualizer,
- [ ] current video is visible without opening full Player,
- [ ] Recently Added uses thumbnails/artwork.

### Mini Player

- [ ] thumbnail/artwork is shown,
- [ ] title and secondary metadata are readable,
- [ ] Previous / Play-Pause / Next work,
- [ ] seek/progress works,
- [ ] tapping the body opens Player.

### Library

- [ ] saved media is visually scannable using thumbnails/artwork,
- [ ] audio/video items are understandable at a glance,
- [ ] playlists are integrated into the Library experience,
- [ ] existing Library/Playlist persistence behavior still passes.

### Player

- [ ] audio visualizer is again a first-class visible surface,
- [ ] video uses the real video surface,
- [ ] progress and primary transport controls are obvious,
- [ ] system/background playback behavior is unchanged.

### Import

- [ ] normal Add-to-WMS flow is simpler than the current format-first flow,
- [ ] advanced Audio/Video/format choices remain available,
- [ ] rights confirmation remains explicit.

### Product feel

- [ ] normal use does not expose Probe/yt-dlp/raw diagnostics,
- [ ] no generic WMS icon is shown where a real media thumbnail/artwork is available,
- [ ] the UI can be understood without knowing WMS architecture or Gate terminology.

## 17. Work sequence for Codex

Do not attempt the entire redesign as one uncontrolled patch.

Recommended local sequence:

1. **UI architecture split**
   - extract screen/components from `MainActivity.kt`,
   - keep behavior unchanged,
   - local test/lint/build.

2. **Metadata / thumbnail foundation**
   - establish one media-artwork model,
   - persist/resolve artwork safely,
   - explicit Room migration if required,
   - local tests.

3. **Mini Player + playback component**
   - implement thumbnail, progress, seek, Prev/Play/Next,
   - verify MediaSession synchronization.

4. **Home Current Media Stage**
   - audio visualizer,
   - direct video surface,
   - Recently Added visual cards.

5. **Library + Playlist consolidation**
   - All / Playlists under one Library experience,
   - thumbnail-first rows/cards.

6. **Full Player redesign**
   - audio visualizer hero,
   - video hero,
   - queue/playlist context.

7. **Import simplification**
   - defaults first,
   - advanced options collapsed,
   - rights confirmation preserved.

8. **Product polish + real-device regression**
   - narrow screen,
   - system bars,
   - light/dark/stylized skins,
   - reduced motion,
   - rotation where practical,
   - full A0–A8 preservation checklist.

At every step:

- use local tests/lint/build first,
- perform real-device checks where visual/playback behavior changes,
- do not use GitHub Actions as a development/debug loop,
- do not merge to `main`,
- do not create a Release,
- document the verified checkpoint before moving on.

## 18. Definition of success

Success is not "all existing controls still exist."

Success means a person can open WMS and immediately understand:

- where to find/add media,
- what is currently playing,
- how to pause/seek/skip,
- where saved media lives,
- where playlists live,
- how to see audio visuals,
- how to see video,

without being taught WMS internals.

The existing A0–A8 engineering work becomes the invisible foundation. The redesigned UI becomes the product.