# WMS Android Architecture

Last updated: 2026-09-11 JST

## 1. Product loop

The Android app is built around one reliable loop:

`Share URL -> local acquisition -> Local Library -> playlist -> native background playback`

Once acquisition succeeds, the **saved local file** is the playback source. This avoids depending on a remote provider session for screen-off/background playback.

## 2. Target stack

- Kotlin
- Jetpack Compose
- AndroidX Media3 / ExoPlayer
- `MediaLibraryService` for background/library playback
- Room for persistent metadata
- app-specific Android storage for canonical WMS media
- replaceable `MediaAcquisitionEngine` for source acquisition

Initial build line:

- compile/target SDK 36
- min SDK 26
- JDK 17
- conservative Android Gradle Plugin line compatible with current Android Studio and the extraction candidate

## 3. Module boundaries

```text
app/
  intake/       shared text / URL validation
  acquisition/  probe, acquire, cancel
  library/      saved media lifecycle
  playlist/     persistent ordered playlists
  playback/     Media3 player/session/service
  storage/      Room + app media files
  ui/           Compose screens/sheets
  common/       typed utilities
```

Provider/extractor details must stay inside `acquisition`.

## 4. URL intake

Primary Android entry point:

- `ACTION_SEND`
- MIME `text/plain`

The intake layer:

1. extracts the first HTTP(S) URL from shared text,
2. rejects credential-bearing URLs,
3. normalizes the URL,
4. passes a typed source into the acquisition UI,
5. performs no irreversible work until the user explicitly saves.

Manual URL paste remains available for testing and sources that do not expose a useful Android share action.

## 5. Acquisition engine

The UI depends on an interface, not yt-dlp directly:

```kotlin
interface MediaAcquisitionEngine {
    suspend fun initialize(): EngineState
    suspend fun probe(sourceUrl: String): ProbeResult
    suspend fun acquireAudio(sourceUrl: String, preset: AudioPreset): AcquisitionResult
    fun cancel()
}
```

The engine owns:

- extractor/runtime initialization
- controlled request options
- temporary job paths
- progress translation
- output discovery
- sanitized errors
- cancellation

The engine does **not** expose generic command-line options to the UI.

## 6. Gate A0 extractor candidate

First spike: `io.github.junkfood02.youtubedl-android` plus its FFmpeg module.

Why it is only a candidate:

- Android packaging is convenient and proven in downloader projects,
- but its public documentation currently advertises 0.18.1 while still mentioning bundled Python 3.8,
- current yt-dlp requirements may outgrow that embedded runtime.

Therefore:

- do not automatically update yt-dlp during A0,
- test the packaged runtime first,
- record exact runtime failure category,
- if the runtime boundary is unsuitable, replace this engine implementation without changing the rest of the app.

No provider-specific bypass chain should be added before this runtime question is isolated.

## 7. Gate A0 storage

A0 writes into app-specific media storage:

```text
getExternalFilesDir(Environment.DIRECTORY_MUSIC)/wms/acquired/
```

with internal-files fallback.

A0 uses a deterministic output name based on source media ID where possible, rather than trusting the remote title as a filesystem path.

Later phases may export user-selected files through MediaStore or the Storage Access Framework. Shared Downloads is not the canonical WMS database location.

## 8. Persistent data model (post-A0)

Minimum Room entities:

### MediaEntity

- id
- title
- provider
- originalUrl
- localUri/path
- mimeType
- mediaType
- durationMs
- fileSize
- artwork/thumbnail reference
- createdAt
- lastPositionMs

### PlaylistEntity

- id
- name
- createdAt
- updatedAt

### PlaylistEntryEntity

- playlistId
- mediaId
- position

### AcquisitionJobEntity

- id
- sourceUrl
- status
- output mode/preset
- progress
- sanitizedError
- createdAt/updatedAt

No cookies, tokens or raw extractor diagnostics belong in Room.

## 9. Playback architecture (post-A0 preview)

A0 only proves that a produced file plays through Media3.

The production playback shape is:

```text
Compose UI
   |
MediaController
   |
MediaLibraryService
   |
MediaSession
   |
ExoPlayer
   |
local WMS files
```

The service owns playback so the Activity can disappear while audio continues.

Required background phase behavior:

- audio focus
- media notification/system controls
- play/pause/seek
- previous/next
- queue/position restore
- screen-off continuation with local files on the tested target device

## 10. UI principles

Keep the native UI smaller than the Web/PWA studio until the core loop is proven.

A0 screen:

- shared/manual URL
- Probe
- detected title/provider/duration
- rights/permission confirmation
- `Save audio (MP3 192)`
- progress/status
- cancel
- play produced file

Later:

- Home / Now Playing
- Share Import sheet
- Downloads/Jobs
- Library
- Playlists

Codec detail belongs behind a simple `Audio / Video` decision where possible.

## 11. Security/privacy

- acquisition occurs locally on the device
- no hidden upload to WMS infrastructure
- no account-cookie import in MVP
- no proxy rotation
- no DRM bypass
- no authentication/access-control bypass
- reject URL credentials
- sanitize remote metadata before display/path use
- one active acquisition initially
- failed/cancelled temp outputs are cleaned

## 12. Distribution

Current scope: development/private APK only.

CI should produce a debug APK artifact. Stable development signing can be added later with repository secrets; keystores/passwords must never be committed.

## 13. Architectural exit rule

No full library/playlist/background implementation begins until Gate A0 proves at least one permitted public source can be acquired locally and played through Media3 on the target Android device.
