# Current Implementation

Last updated: 2026-09-12 JST

## Repository state

Repository: `goroyattemiyo/web-media-studio-android`

Current active branch:

`feat/gate-a1-share-intake`

Draft PR:

`#3 feat: Gate A1 Search Home and share intake shell`

Gate A0 is merged to `main` and remains the proven local-acquisition baseline.

Current target:

**Gate A1 — Search Home + Android share/URL intake productization.**

Gate A1 is still OPEN until the search-result import/save/playback route passes end-to-end on the target Android device.

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
- repeated intake of the same URL forces a fresh Probe
- stale Probe completions are prevented from overwriting the current intake state
- changing/reimporting a URL resets rights confirmation to OFF
- rights/permission confirmation remains mandatory before local acquisition
- proven MP3 192 local-acquisition path is preserved
- yt-dlp stable is checked automatically at most once per 24 hours after acquisition-engine initialization
- automatic yt-dlp update failure preserves the existing/bundled version and does not block normal use
- Developer tools keeps a manual yt-dlp update action only as a force-check/fallback
- verbose yt-dlp diagnostics remain behind Developer tools
- saved local media appears in a WMS-styled mini player on Search Home
- future `Search / Library / Playlist` navigation shell is visible without pretending Library/Playlist are implemented
- search is isolated behind a `SearchProvider` abstraction
- Android keyword Search uses on-device yt-dlp `ytsearch`
- local search parses flat JSON results into title, author, canonical YouTube URL and optional thumbnail
- selecting `WMSに追加` passes the result URL into the existing Import Sheet / acquisition flow
- `元サイト` opens the canonical source URL externally
- the obsolete Cloud Run YouTube search provider has been removed from the Android app source
- app version was bumped to `0.1.0-a1-local-search` / versionCode `2` to make local-search builds distinguishable from the earlier Worker-backed APK
- future TikTok / Instagram / Web providers remain disabled until actual search integration exists

## Current SearchProvider boundary

```text
Search Home
   |
SearchViewModel
   |
SearchProvider
   |
LocalYoutubeSearchProvider (yt-dlp ytsearch)
   |
canonical YouTube URL
   |
MediaAcquisitionEngine
```

Search and acquisition support are deliberately separate. A source must not be described as downloadable merely because it can appear in search results.

## CI status

Android CI #50 for the local-search-only APK head `e3242e10ecbe4b8d3520f78352c19db2a958a159`:

- build: PASS
- unit tests: PASS
- lint: PASS
- debug APK artifact: PASS

The previous Worker-backed Search path reached Cloud Run but returned HTTP 503 because `YOUTUBE_DATA_API_KEY` was not configured. Android Search no longer depends on that path.

## Verified on the real device for Gate A1

### Direct URL intake — PASS

`URL intake -> automatic Probe -> Import Sheet -> rights confirmation -> MP3 save -> Search Home mini player -> Media3 playback`

Verified sample:

`Michael Jackson - Beat It (Official 4K Video)`

### Android share intake — PASS

`YouTube app/browser -> Android share sheet -> WMS -> Import Sheet -> automatic Probe -> rights confirmation -> MP3 save -> Search Home mini player -> Media3 playback`

### Keyword Search result display — PASS

Target-device verification on 2026-09-12 JST confirmed that the local yt-dlp Search path returns YouTube results without `SEARCH_HTTP_503`.

Observed sample query:

`マイケル`

Observed result count:

`8`

This proves:

`Search Home -> local yt-dlp ytsearch -> result list`

The remaining Gate A1 acceptance check is the downstream result-action path:

`result -> WMSに追加 -> automatic Probe -> rights confirmation -> MP3 save -> Search Home mini-player playback`

## Still to verify before closing Gate A1

- select one local Search result and open `WMSに追加`
- confirm automatic Probe in the Import Sheet
- save MP3 after rights confirmation
- close the sheet and play the saved item from the Search Home mini player

Quality checks that can follow Gate A1 closure:

- screen rotation / narrow-device layout behavior
- observe automatic yt-dlp stable refresh after the 24-hour check window

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

On the target Android device:

1. choose one of the displayed YouTube Search results,
2. tap `WMSに追加`,
3. confirm automatic Probe,
4. confirm rights and save MP3,
5. close the sheet,
6. play the saved media from the Search Home mini player.

Keep PR #3 Draft until this final Search-result route passes.
