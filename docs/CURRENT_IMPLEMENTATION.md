# Current Implementation

Last updated: 2026-09-11 JST

## Repository state

Repository: `goroyattemiyo/web-media-studio-android`

Current active branch:

`feat/gate-a1-share-intake`

Draft PR:

`#3 feat: Gate A1 Search Home and share intake shell`

Gate A0 is merged to `main` and remains the proven local-acquisition baseline.

Current target:

**Gate A1 — Search Home + Android share/URL intake productization.**

## Gate A0 baseline already proven

On the target Android device a permitted/public YouTube sample completed:

`Update yt-dlp stable -> Probe -> rights confirmation -> Save MP3 192 -> non-empty local file -> Media3 Play`

Successful active yt-dlp version: `2026.08.19`.

## Implemented on Gate A1 branch

- canonical WMS neon-diamond emblem adapted into Android resources and app identity
- default launch surface changed from diagnostic screen to WMS Search Home
- one primary input accepts either a search query or a direct HTTP(S) URL
- direct URL bypasses keyword search and opens the Import Sheet
- Android `ACTION_SEND text/plain` shared URL opens the same Import Sheet path
- shared/manual URL automatically runs Probe before save
- rights/permission confirmation remains mandatory before local acquisition
- proven MP3 192 local-acquisition path is preserved
- yt-dlp update and verbose diagnostics moved behind Developer tools
- saved local media appears in a WMS-styled mini player on Search Home
- future `Search / Library / Playlist` navigation shell is visible without pretending Library/Playlist are implemented
- search is isolated behind a `SearchProvider` abstraction
- first concrete provider uses the existing WMS Media Worker YouTube search endpoint
- search results expose title, author, provider and canonical source URL
- selecting `WMSに追加` passes the result URL into the existing Import Sheet / acquisition flow
- `元サイト` opens the canonical source URL externally
- future TikTok / Instagram / Web providers remain disabled until actual search integration exists

## Current SearchProvider boundary

```text
Search Home
   |
SearchViewModel
   |
SearchProvider
   |
WmsMediaSearchProvider (YouTube first)
   |
canonical URL
   |
MediaAcquisitionEngine
```

Search and acquisition support are deliberately separate. A source must not be described as downloadable merely because it can appear in search results.

## CI status

Android CI #28 for implementation head `2a4e90e16d7eb1a847dba513c73ae96e0fd2bba8`:

- build: PASS
- unit tests: PASS
- lint: PASS
- debug APK artifact: PASS

The prior CI #24 compile failure was corrected by removing the invalid Compose `weight` import/usage and opting into the experimental Material3 bottom-sheet API explicitly.

## Not yet verified on the real device for Gate A1

- Search Home visual layout on the target Android device
- keyword search returning YouTube results from the WMS Media Worker
- selecting a search result -> automatic Probe -> Import Sheet
- complete search-result -> rights confirmation -> MP3 save -> Media3 play path
- Android share from a browser/YouTube app into the new Import Sheet UI
- screen rotation / narrow-device layout behavior

## Still deferred

- persistent search history
- persistent Room Local Library
- persistent playlists
- production bottom navigation
- foreground acquisition service
- MediaLibraryService/background playback
- production skin switching
- production audio-reactive visualizers
- TikTok/Instagram/Web search providers

## Product/security boundaries

- permitted/public or otherwise authorized media only
- local acquisition remains explicit; search never auto-downloads
- rights confirmation remains mandatory
- no account cookies in MVP
- no proxy rotation
- no DRM bypass
- no authentication/access-control bypass
- no arbitrary yt-dlp flags in normal UI

## Next acceptance event

Install the CI #28 APK on the target Android device and verify:

1. app launches to the WMS Search Home,
2. WMS emblem and dark/neon visual identity look correct,
3. search a normal keyword and receive YouTube results,
4. choose `WMSに追加`,
5. confirm automatic Probe and Import Sheet,
6. confirm rights and save MP3,
7. close sheet and play from the Search Home mini player,
8. separately share a public URL from another Android app into WMS and confirm the Import Sheet opens prefilled.

Keep PR #3 Draft until this real-device Gate A1 flow passes.
