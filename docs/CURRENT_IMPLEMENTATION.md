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

Gate A1 is still OPEN until keyword Search passes on the target Android device.

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
- first concrete provider targets the existing WMS Media Worker YouTube search endpoint
- search results model title, author, provider and canonical source URL
- selecting `WMSに追加` passes the result URL into the existing Import Sheet / acquisition flow
- `元サイト` opens the canonical source URL externally
- native Search now sends the allowed first-party WMS `Origin` required by the Media Worker
- Search HTTP errors now surface the worker `detail` message so provider/config failures are visible on-device
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

Android CI #41 for native Search Origin fix head `56543eba68d4fc541e2825bb71fafc68f13e49c1`:

- build: PASS
- unit tests: PASS
- lint: PASS
- debug APK artifact: PASS

Android CI #34 for automatic yt-dlp refresh head `e405eeefe01fccb09847ab46ce2326fdb7f1686e` also passed build / tests / lint / APK upload.

## Verified on the real device for Gate A1

### Direct URL intake — PASS

`URL intake -> automatic Probe -> Import Sheet -> rights confirmation -> MP3 save -> Search Home mini player -> Media3 playback`

Verified sample:

`Michael Jackson - Beat It (Official 4K Video)`

### Android share intake — PASS

`YouTube app/browser -> Android share sheet -> WMS -> Import Sheet -> automatic Probe -> rights confirmation -> MP3 save -> Search Home mini player -> Media3 playback`

### Keyword Search — FIX BUILT / RE-TEST REQUIRED

The previously tested build failed native keyword Search.

Root cause found in code review:

- the WMS Media Worker requires an allowed `Origin` header for `/video/providers` and `/video/search`,
- the Web/PWA receives that header naturally from the browser,
- the Android `HttpURLConnection` search client did not set it,
- therefore native Search could be rejected before provider search ran.

Fix landed in commit `56543eba68d4fc541e2825bb71fafc68f13e49c1` and CI #41 passes. Real-device re-test is still required before Search can be marked PASS.

If the worker returns another non-2xx status, Android now displays the worker detail text in addition to the HTTP code so the next blocker can be identified directly.

## Still to verify before closing Gate A1

- keyword search returns YouTube results on the target Android device
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

Using the CI #41 APK on the target Android device:

1. launch Search Home,
2. enter a normal keyword,
3. confirm YouTube search results appear,
4. choose `WMSに追加`,
5. confirm automatic Probe,
6. confirm rights and save MP3,
7. close the sheet and play from the Search Home mini player.

Keep PR #3 Draft until this Search route passes.