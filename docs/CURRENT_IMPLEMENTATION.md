# Current Implementation

Last updated: 2026-09-12 JST

## Repository state

Repository: `goroyattemiyo/web-media-studio-android`

Current active branch:

`feat/gate-a1-share-intake`

PR:

`#3 feat: Gate A1 Search Home and share intake shell`

Gate A0 is merged to `main` and remains the proven local-acquisition baseline.

**Gate A1 — Search Home + Android intake shell: PASS on the target Android device.**

The branch is ready for final CI and squash merge.

## Gate A0 baseline already proven

On the target Android device a permitted/public YouTube sample completed:

`Update yt-dlp stable -> Probe -> rights confirmation -> Save MP3 192 -> non-empty local file -> Media3 Play`

Successful active yt-dlp version during the original Gate A0 run: `2026.08.19`.

## Implemented in Gate A1

- canonical WMS neon-diamond emblem adapted into Android resources and app identity
- default launch surface changed from developer diagnostics to WMS Search Home
- one primary field accepts either a keyword query or direct HTTP(S) URL
- direct URL bypasses keyword search and opens the Import Sheet
- Android `ACTION_SEND text/plain` shared URL opens the same Import Sheet path
- shared/manual URL automatically runs Probe before save
- repeated intake of the same URL forces a fresh Probe
- stale Probe completions cannot overwrite the current intake state
- changing/reimporting a URL resets rights confirmation to OFF
- rights/permission confirmation remains mandatory before local acquisition
- proven MP3 192 local-acquisition path is preserved
- yt-dlp stable is checked automatically at most once per 24 hours
- automatic yt-dlp update failure preserves the existing/bundled version and does not block normal use
- local Search waits for yt-dlp update/initialization so Search cannot race the updater
- Developer tools retains manual yt-dlp update only as a force-check/fallback plus diagnostics
- saved local media appears in a WMS-styled mini player on Search Home
- `Search / Library / Playlist` navigation shell is visible without pretending unfinished destinations are implemented
- Search is isolated behind a `SearchProvider` abstraction
- Android keyword Search uses on-device yt-dlp `ytsearch`
- local Search parses title, author, canonical YouTube URL, thumbnail and duration
- Search result cards show actual thumbnails when available
- Search result cards display duration as `m:ss` or `h:mm:ss`
- `WMSに追加` routes a selected result into the same Probe / Import Sheet / save flow
- `元サイト` opens the canonical source URL externally
- obsolete Cloud Run YouTube Search code was removed from the Android app
- the previous fixed 30-minute / 250 MB acquisition limits were removed so long-form authorized media can be attempted
- storage exhaustion is surfaced as a user-facing storage-capacity error instead of being treated as a length limit
- current Gate A1 app build is `0.1.2-a1-search-lock`, versionCode `4`
- future TikTok / Instagram / Web providers remain disabled until actual integration exists

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

Search and acquisition support remain separate capabilities. A source must not be described as downloadable merely because it appears in Search.

## Final CI baseline before Gate A1 acceptance

Android CI #61 for head `fd6ca7c4aef29ec46b7af07a8fd1e62932f0550d`:

- build: PASS
- unit tests: PASS
- lint: PASS
- debug APK artifact: PASS

A final docs-only CI is expected after this acceptance record is committed.

## Real-device verification — Gate A1 PASS

### Direct URL intake — PASS

`URL intake -> automatic Probe -> Import Sheet -> rights confirmation -> MP3 save -> Search Home mini player -> Media3 playback`

Verified sample:

`Michael Jackson - Beat It (Official 4K Video)`

### Android share intake — PASS

`YouTube app/browser -> Android share sheet -> WMS -> Import Sheet -> automatic Probe -> rights confirmation -> MP3 save -> Search Home mini player -> Media3 playback`

### Keyword Search — PASS

Target-device verification on 2026-09-12 JST confirmed on-device yt-dlp Search works without the previous Cloud Run HTTP 503 dependency.

Observed sample query:

`マイケル`

Observed result count:

`8`

After serializing Search behind the yt-dlp update lock, the target device again returned results successfully. The successful build also displays thumbnails and durations.

### Search-result import/save/playback — PASS

Final target-device verification on 2026-09-12 JST completed:

`keyword Search -> thumbnail/duration result -> WMSに追加 -> automatic Probe -> rights confirmation -> MP3 save -> 閉じて再生 -> 最近追加したメディア -> Media3 playback`

This satisfies the Gate A1 exit condition.

**Gate A1: PASS**

## Non-blocking follow-up quality items

- correct Search Home top inset so `Find media` does not overlap the Android status bar
- screen rotation / narrow-device layout behavior
- observe automatic yt-dlp stable refresh after the 24-hour check window
- improve adaptive launcher icon parity with the canonical Web/PWA icon where needed

## Deferred to later gates

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
- local acquisition remains explicit; Search never auto-downloads
- rights confirmation remains mandatory
- no account cookies in MVP
- no proxy rotation
- no DRM bypass
- no authentication/access-control bypass
- no arbitrary yt-dlp flags in normal UI

## Next engineering step

Finish PR #3 with final CI and squash merge. After merge, start the next branch from `main`.

The roadmap currently places managed acquisition jobs at Gate A2 and persistent Local Library at Gate A3. The known Search Home top-inset issue can be fixed as a small post-Gate-A1 quality change before or alongside the next gate.
