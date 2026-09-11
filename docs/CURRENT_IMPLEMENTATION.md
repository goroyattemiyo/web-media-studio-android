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

Gate A1 is still OPEN because keyword Search is failing on the target Android device.

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
- selecting `WMSに追加` is designed to pass the result URL into the existing Import Sheet / acquisition flow
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

Android CI #34 for automatic yt-dlp refresh head `e405eeefe01fccb09847ab46ce2326fdb7f1686e`:

- build: PASS
- unit tests: PASS
- lint: PASS
- debug APK artifact: PASS

Automatic daily yt-dlp stable refresh was added in commit `e405eeefe01fccb09847ab46ce2326fdb7f1686e`. The policy is best-effort and rate-limited to once per 24 hours using app preferences; normal use falls back to the current version if the update check fails.

A later same-URL re-intake bug was fixed in commit `e43e6593cb70d55e38f64378796a340f38ddde87` by moving Probe initiation into the intake path and resetting stale per-URL state.

## Verified on the real device for Gate A1

### Direct URL intake — PASS

The following direct-URL intake flow is verified on the target Android device:

1. a YouTube URL is accepted by the Gate A1 Import Sheet,
2. Probe succeeds,
3. title/provider are displayed under `取得候補`,
4. rights confirmation can be enabled,
5. MP3 save completes successfully,
6. the sheet displays `保存完了` with the saved title,
7. closing the sheet returns to Search Home,
8. the saved item plays successfully from the Search Home mini player.

Verified sample:

`Michael Jackson - Beat It (Official 4K Video)`

Confirmed path:

`URL intake -> automatic Probe -> Import Sheet -> rights confirmation -> MP3 save -> Search Home mini player -> Media3 playback`

### Android share intake — PASS

The external Android sharing route is verified on the target device:

`YouTube app/browser -> Android share sheet -> WMS -> Import Sheet -> automatic Probe -> rights confirmation -> MP3 save -> Search Home mini player -> Media3 playback`

### Keyword Search — FAIL / OPEN

Keyword Search is not yet verified and currently fails on the target Android device.

Root cause found in code review:

- the WMS Media Worker requires an allowed `Origin` header for `/video/providers` and `/video/search`,
- the Web/PWA receives that header naturally from the browser,
- the Android `HttpURLConnection` search client did not set it,
- therefore native Search can be rejected before provider search runs.

The Android provider must be corrected and then re-tested end-to-end.

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

Fix native Search request compatibility with the WMS Media Worker, then verify on the target Android device:

1. app launches to Search Home,
2. enter a normal keyword,
3. receive YouTube search results,
4. choose `WMSに追加`,
5. automatic Probe succeeds,
6. confirm rights and save MP3,
7. close the sheet and play from the Search Home mini player.

Keep PR #3 Draft until this Search route passes.