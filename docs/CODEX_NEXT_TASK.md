# Codex Next Task

Date: 2026-09-14 JST

## Active work branch

`plan/product-ui-redesign`

Base branch:

`feat/gate-a8-development-distribution`

## Read first

1. `docs/CODEX_PRODUCT_UI_REDESIGN.md`
2. `docs/CURRENT_IMPLEMENTATION.md`
3. `docs/UI_UX_DESIGN.md`
4. `docs/CODEX_LOCAL_DEVELOPMENT_WORKFLOW.md`
5. `docs/ROADMAP.md`

## Goal

Redesign WMS Android from a feature/demo-oriented UI into a finished media-player product without removing the proven A0–A8 capabilities.

The product UI specification and acceptance criteria are canonical in:

`docs/CODEX_PRODUCT_UI_REDESIGN.md`

Key requirements include:

- media-first Home,
- real thumbnails/artwork instead of generic WMS icons when available,
- audio visualizer restored as a normal first-class playback surface,
- video visible in normal flow without requiring the user to open full Now Playing,
- Mini Player with thumbnail, seek/progress, Previous, Play/Pause, Next,
- Home / Library / Player primary navigation,
- playlists integrated into Library,
- simpler Add-to-WMS flow with advanced format choices collapsed,
- screen-level Compose extraction from the oversized `MainActivity.kt`,
- no regression of A0–A8 persistence, acquisition, playback, provider, appearance, or update-install behavior.

## Execution rule

Implement incrementally in the sequence defined by `docs/CODEX_PRODUCT_UI_REDESIGN.md`.

For every checkpoint:

- local unit tests,
- local lint,
- local APK build,
- target-device verification where playback/visual behavior changes,
- document the verified checkpoint before continuing.

Do **not**:

- run GitHub Actions,
- create a GitHub Release,
- merge to `main`,
- remove security/rights boundaries,
- rewrite proven playback/acquisition architecture without a concrete need.

Stop at the next explicit user approval boundary after the redesign reaches LOCAL PASS.