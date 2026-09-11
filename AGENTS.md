# AGENTS.md

## Purpose

This repository is the Android-native WMS implementation. Repository documents are the source of truth. Do not invent implementation status.

## Source of truth order

1. `docs/CURRENT_IMPLEMENTATION.md` — what actually exists now
2. `docs/ARCHITECTURE.md` — approved Android technical structure
3. `docs/ROADMAP.md` — gate/phase plan
4. `README.md` — public/project overview
5. parent Web/PWA repository docs — product context only where not overridden here

When documents conflict, stop and resolve the conflict instead of guessing.

## Development workflow

For non-trivial work:

1. Plan the smallest gate-aligned change.
2. Use a `feat/*`, `fix/*`, or `docs/*` branch.
3. Implement only the current gate scope.
4. Run relevant build, lint and tests.
5. Open a PR into `main`.
6. Review regressions, storage/privacy and Android lifecycle behavior.
7. Squash merge only after checks pass.
8. Mark real-device support only after a real-device test.

`main` must remain buildable.

## Current product goal

The primary Android loop is:

`Share URL -> local acquisition -> Local Library -> playlist -> screen-off playback`

The saved local file becomes the playback source. Remote provider playback is not the background-playback foundation.

## Architecture rules

- Kotlin + native Android UI.
- Jetpack Compose for application UI.
- Media3/ExoPlayer for playback.
- `MediaLibraryService` will own long-lived playback once the background-playback phase begins.
- Room will own persistent library/playlist metadata once persistence begins.
- Acquisition must be behind `MediaAcquisitionEngine`; Compose screens must not call yt-dlp directly.
- App-specific storage is canonical for WMS-managed media in the first implementation.
- Do not build a server dependency into the core Android player.
- Treat all shared URLs and remote metadata as untrusted input.
- No secrets, cookies, auth tokens, keystores or passwords in Git.

## Acquisition boundary

Allowed scope:

- permitted public HTTP(S) media sources
- local extraction/download on the user's device
- controlled audio/video presets chosen by WMS

Not implemented/allowed in the MVP:

- account-cookie import
- proxy rotation
- DRM bypass
- authentication/access-control bypass
- generic arbitrary yt-dlp flags from the UI
- automatic scraping workarounds for private/login-only media

Provider support must be claimed conservatively. A provider is not marked supported until a real-device end-to-end acquisition succeeds.

## Gate A0 rule

Gate A0 exists only to prove the riskiest assumption: local Android acquisition can work.

Do not add Room, full playlists, rich player visuals, recorder features, Chrome-extension integration or broad provider-specific fallback logic before A0 exits.

A0 passes only when the target Android device can produce at least one permitted local audio file and play it through Media3.

## youtubedl-android compatibility caution

The library is a feasibility candidate, not a permanent architecture dependency. WMS may perform a controlled yt-dlp stable update check no more than once per 24 hours after the acquisition engine initializes. The update must be best-effort: failure must preserve and continue with the currently installed/bundled yt-dlp, and normal acquisition must not depend on an update server being reachable. Keep the manual Developer-tools update action only as an explicit force-check/fallback. If the packaged runtime becomes incompatible with current extraction requirements, keep `MediaAcquisitionEngine` and replace only the engine implementation.

## Testing expectations

Before merging code, run as applicable:

- Gradle debug assemble
- unit tests
- Android lint
- dependency/runtime initialization checks where CI can cover them

Real-device-only claims require explicit device validation. CI compilation does not prove extractor runtime compatibility.

## Documentation discipline

After a meaningful change:

- update `docs/CURRENT_IMPLEMENTATION.md`
- update `docs/ROADMAP.md` only for verified milestones
- record newly discovered Android/provider/runtime constraints

Do not describe planned work as implemented work.
