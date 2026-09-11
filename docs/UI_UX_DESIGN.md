# WMS Android UI / UX Design

Last updated: 2026-09-11 JST

## 1. Design goal

The Android app should feel like the native/mobile continuation of the original Web Media Studio, not a separate product.

The product flow is:

`Search or Share -> resolve source URL -> Import -> local save -> Library / Playlist -> native playback`

The default launch destination is **Media Search**, not Library.

External Android share intents are the exception: when WMS receives a valid shared URL, it should bypass Search Home and open the Import Sheet directly.

## 2. WMS visual identity

Android must inherit the approved WMS identity from the Web/PWA implementation.

### App icon / emblem

Canonical source:

`web-media-studio/public/icons/app-icon.svg`

Characteristics to preserve:

- WMS monogram
- thick diamond frame
- cyan -> blue -> purple neon gradient
- dark rounded-square base
- subtle blue radial glow

The Android launcher icon should be generated as an adaptive icon while preserving the emblem geometry and safe-zone visibility.

The same emblem may appear in:

- app icon
- splash / cold start
- top app bar
- player visual mode `emblem`
- empty / loading states

Do not invent a second Android-only logo.

## 3. Primary navigation

Bottom navigation, once Library exists:

1. **Search** — default launch destination
2. **Library** — locally saved media
3. **Playlists** — persistent ordered lists

Now Playing is not a fourth permanent tab. A persistent Mini Player sits above bottom navigation whenever a local item is loaded; tapping it opens full Now Playing.

Settings / Appearance / Developer Tools belong under the top-right overflow menu.

## 4. Search Home

Search Home should resemble a modern media search landing page rather than a developer utility.

Suggested composition:

```text
┌──────────────────────────────┐
│ ◇ WMS                    ⋮   │
│                              │
│      Find media              │
│  曲名・アーティスト・URL       │
│                              │
│ [ 🔎 Search or paste URL   ] │
│                              │
│ [すべて] [YouTube] [...]     │
│                              │
│ 最近の検索 / 最近追加          │
│                              │
│          Mini Player          │
│ Search   Library   Playlists │
└──────────────────────────────┘
```

### Unified input behavior

The primary field accepts either:

- normal search text, or
- a complete HTTP(S) URL.

If input is a URL:

`URL -> validate -> Probe -> Import Sheet`

No search request is needed.

If input is normal text:

`query -> selected SearchProvider(s) -> result cards`

### Search provider abstraction

Do not hard-code the screen around YouTube.

Native model direction:

```kotlin
interface SearchProvider {
    val id: String
    val displayName: String
    suspend fun search(query: String): List<SearchResult>
}

data class SearchResult(
    val providerId: String,
    val sourceId: String?,
    val canonicalUrl: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
)
```

Initial implementation may expose only providers that are actually usable. Disabled/unimplemented providers must not be presented as supported.

Future candidates can include YouTube and other public-search sources only after their search path is explicitly implemented and tested.

The Web/PWA already uses a provider-aware search-result shape; Android should keep the same conceptual model while remaining native.

## 5. Search results

Each result card:

```text
┌──────────────────────────────┐
│ [thumbnail]  Title           │
│              Author          │
│              YouTube         │
│                              │
│ [ 取り込む ]      [ 元サイト ]│
└──────────────────────────────┘
```

Primary action: **取り込む**

This resolves the result's canonical URL and opens Import Sheet.

Secondary action: **元サイト**

This opens the provider page/app.

The normal Android product should not require users to manually copy URLs from search results.

## 6. Import Sheet

Entry points:

- Android share intent
- Search result
- pasted URL

Flow:

`source URL -> automatic Probe -> metadata preview -> explicit rights confirmation -> Save`

Normal UI should not expose a Probe button. Probe runs automatically.

Suggested sheet:

```text
┌──────────────────────────────┐
│ WMSに追加                     │
│                              │
│ [thumbnail]  Title           │
│              Provider        │
│                              │
│ 保存形式                      │
│ ● Audio  MP3 192 kbps        │
│ ○ Video  (when supported)    │
│                              │
│ □ 保存する権利・許可を確認     │
│                              │
│          [ 保存 ]             │
└──────────────────────────────┘
```

Gate A1 may keep format choices minimal; the visual shape should already match the production sheet.

During acquisition:

- show progress
- allow cancel
- keep source title/thumbnail visible
- avoid raw extractor diagnostics in the normal surface

On success:

- Play
- Open Library
- optionally Add to Playlist later

## 7. Mini Player and Now Playing

### Mini Player

Persistent above bottom navigation when local media is loaded:

- small artwork / WMS visual
- title
- play/pause
- next when queue support exists

Tap anywhere else on the Mini Player -> full Now Playing.

### Full Now Playing

The main player should carry the original WMS personality.

```text
┌──────────────────────────────┐
│ ‹  Now Playing          ⋮    │
│                              │
│        VISUALIZER            │
│        / ARTWORK             │
│                              │
│ Title                        │
│ Provider                     │
│ ───────●────────────         │
│                              │
│   ⏮      ▶ / ❚❚      ⏭     │
│                              │
│ Queue / Playlist             │
└──────────────────────────────┘
```

Visualizer is a first-class player surface, not a developer decoration.

## 8. Skin system

Preserve the Web/PWA canonical theme IDs so preferences can remain conceptually compatible:

- `midnight-neon`
- `obsidian`
- `studio-light`
- `analog-warm`
- `cyber-blue`
- `aurora-purple`
- `emerald-night`
- `crimson-noir`
- `sunset-glow`
- `sakura`
- `pixel-arcade`
- `led-marquee`
- `retro-terminal`
- `cassette-deck`

A skin is not just a color palette. It may change:

- background / surfaces
- typography treatment
- corner geometry
- borders
- shadows / glow
- player treatment
- subtle textures

Native architecture direction:

```kotlin
data class WmsSkin(
    val id: String,
    val colors: WmsColors,
    val shapes: WmsShapes,
    val typographyStyle: WmsTypographyStyle,
    val surfaceStyle: WmsSurfaceStyle,
)
```

Store the selected skin in DataStore.

If a Web skin has not yet received a full native renderer, fall back gracefully to `midnight-neon` rather than showing a fake/partial provider support claim.

## 9. Visualizer system

Preserve the Web/PWA canonical visual-mode IDs:

- `rainbow-ring`
- `oscilloscope`
- `spectrum-city`
- `neon-tunnel`
- `kaleido`
- `particles`
- `pulse`
- `orbit`
- `bars`
- `wave`
- `emblem`
- `minimal`

Two implementation classes:

### Lightweight / non-audio-reactive

- emblem
- pulse
- orbit
- bars
- wave
- minimal

These can be implemented first using Compose Canvas / animation.

### Audio-reactive

- rainbow-ring
- oscilloscope
- spectrum-city
- neon-tunnel
- kaleido
- particles

Architecture should separate visual drawing from audio analysis:

```kotlin
interface VisualizerDataSource {
    val spectrum: StateFlow<FloatArray>
    val waveform: StateFlow<FloatArray>
}
```

Prefer a playback-internal PCM/analyser path tied to WMS's own Media3 player rather than requesting microphone access merely to animate the player.

The selected visualizer is stored in DataStore.

Respect reduced-motion / accessibility preferences and offer `minimal` as a low-motion mode.

## 10. Appearance entry point

Top-right overflow -> Appearance

Sections:

- Skin
- Player Visualizer
- Motion / reduced effects

Use visual preview tiles rather than text-only dropdowns on Android.

Developer diagnostics are separate from Appearance.

## 11. Developer Tools

Gate A0 controls remain available but are removed from normal product flow:

Top-right overflow -> Developer Tools

Contains:

- Acquisition Engine state
- active yt-dlp version
- Update yt-dlp stable
- yt-dlp diagnostics
- sanitized last acquisition error

Normal users should never need this screen to import media.

## 12. Gate A1 UI acceptance

Gate A1 focuses on intake/navigation shell, not broad provider search implementation.

Required:

1. App opens on Search Home.
2. WMS emblem/visual identity is present.
3. Search field accepts pasted URL.
4. Shared HTTP(S) URL bypasses Search Home and opens Import Sheet prefilled.
5. Invalid/credential-bearing URLs are rejected clearly.
6. Import Sheet automatically probes; no normal Probe button.
7. Manual URL path remains possible through the Search field.
8. Existing Gate A0 acquisition path remains reachable through Import Sheet.
9. Developer diagnostics move out of the primary flow.

Search-by-keyword provider integration can land incrementally after this native shell is stable, using the SearchProvider boundary.

## 13. Long-term screen map

```text
Search Home (default)
  -> Search Results
      -> Import Sheet
  -> URL detected
      -> Import Sheet

External Share
  -> Import Sheet

Import Sheet
  -> Acquisition progress
  -> Saved
      -> Library item / Play

Library
  -> Media details
  -> Now Playing

Playlists
  -> Playlist detail
  -> Now Playing

Mini Player
  -> Now Playing

Overflow
  -> Appearance
  -> Settings
  -> Developer Tools
```

This screen map is the Android-native product baseline after Gate A0.