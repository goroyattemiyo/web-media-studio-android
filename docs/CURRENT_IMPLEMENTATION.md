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

Gate A1 is still OPEN because keyword Search still needs to pass on the target Android device.

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
- repeated intake of the same URL now forces a fresh Probe instead of relying on URL-keyed Compose side effects
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
- Android keyword Search now defaults to an on-device yt-dlp `ytsearch` provider
- local search parses flat JSON results into title, author, canonical YouTube URL and optional thumbnail
- selecting `WMSに追加` passes the result URL into the existing Import Sheet / acquisition flow
- `元サイト` opens the canonical source URL externally
- the previous WMS Media Worker search provider remains available in code for future provider work, but is no longer the default Android YouTube search path
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

Android CI #45 for local yt-dlp Search head `c51f7f68a7bf7aaf276724010b793248280fe14c`:

- build: PASS
- unit tests: PASS
- lint: PASS
- debug APK artifact: PASS

Android CI #41 for the prior Worker-Origin fix also passed, but real-device Search then returned HTTP 503.

Automatic daily yt-dlp stable refresh was added in commit `e405eeefe01fccb09847ab46ce2326fdb7f1686e`. The policy is best-effort and rate-limited to once per 24 hours using app preferences; normal use falls back to the current version if the update check fails.

A same-URL re-intake bug was fixed in commit `e43e6593cb70d55e38f64378796a340f38ddde87` by moving Probe initiation into the intake path and resetting stale per-URL state.

## Verified on the real device for Gate A1

### Direct URL intake — PASS

`URL intake -> automatic Probe -> Import Sheet -> rights confirmation -> MP3 save -> Search Home mini player -> Media3 playback`

Verified sample:

`Michael Jackson - Beat It (Official 4K Video)`

### Android share intake — PASS

`YouTube app/browser -> Android share sheet -> WMS -> Import Sheet -> automatic Probe -> rights confirmation -> MP3 save -> Search Home mini player -> Media3 playback`

### Keyword Search — LOCAL FIX BUILT / RE-TEST REQUIRED

Observed sequence:

1. initial native request omitted the Worker's required allowed `Origin` header,
2. Android was updated to send the allowed WMS origin,
3. real-device Search then reached the Worker but returned `SEARCH_HTTP_503`,
4. Cloud Run deployment logs confirmed `YOUTUBE_DATA_API_KEY` is empty,
5. `/video/providers` therefore reports YouTube search as disabled.

Rather than require a Cloud Run/API-key dependency for the native player, Android Search now defaults to local yt-dlp `ytsearch` using the same updated yt-dlp runtime already proven for Probe/acquisition.

This local Search path has passed CI #45 but still requires real-device verification.

## Still to verify before closing Gate A1

- local keyword search returns YouTube results on the target Android device
- selecting a result -> `WMSに追加` -> automatic Probe -> Import Sheet
- complete search-result -> rights confirmation -> MP3 save -> Search Home mini-player playback

Quality checks that can follow Gate A1 closure:

- screen rotation / narrow-device layout behavior
- automatic yt-dlp refresh behavior on-device after the 24-hour check window

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

Using the local-search APK on the target Android device:

1. launch WMS Search Home,
2. enter `Michael Jackson Beat It` or another normal keyword,
3. confirm YouTube results appear without `SEARCH_HTTP_503`,
4. choose `WMSに追加`,
5. confirm automatic Probe,
6. confirm rights and save MP3,
7. close the sheet and play from the Search Home mini player.

Keep PR #3 Draft until this local Search route passes.
