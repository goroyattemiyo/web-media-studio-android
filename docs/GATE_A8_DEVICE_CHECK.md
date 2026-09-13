# Gate A8 — Development distribution checkpoint

Date: 2026-09-14 JST

Status: **LOCAL PASS**

Branch: `feat/gate-a8-development-distribution`

Verified implementation checkpoint: `7b738b943f0572cace827e582461f07f69b6b874`

Artifact: `wms-android-0.8.0-a8-dev-distribution-v12-7b738b943f05-debug.apk`

APK SHA-256: `F24F2AB335EF4287C9BF61468D195E7C8ED7516657A050734C2B908742FE03B6`

Debug certificate SHA-256: `31cec40b17394a7e722f17ca337e94818a2393e3472c665256cd8c76934ba4d9`

Target device: Redmi 12 5G (`23076RA4BR`), Android 15 / API 35.

## Local verification

- [x] Gradle Wrapper 8.13 with JDK 17
- [x] `testDebugUnitTest`
- [x] `lintDebug` (0 errors)
- [x] `assembleDebug`
- [x] versioned clean-checkpoint APK copied to ignored `dist/`
- [x] sibling checksum file contents match the APK SHA-256
- [x] dirty worktree artifact is visibly labeled `-dirty`

## Target-device verification

- [x] `local-verify.ps1 -Install -Serial 6167306007fe` completed `adb install -r`
- [x] script verified package `com.goroyattemiyo.wms`, versionCode 12, and `0.8.0-a8-dev-distribution`
- [x] app launched after update with no fatal or Room migration error
- [x] existing Room database and Appearance DataStore remained present
- [x] all nine managed media files remained present
- [x] previously selected Instagram video remained in Mini Player
- [x] system Play resumed it through the WMS MediaSession with `USAGE_MEDIA`

## Deferred external boundaries

- [ ] GitHub Actions final CI — not run by explicit instruction
- [ ] optional private GitHub Release — not created
- [ ] merge to `main` — not performed

Gate A8 is LOCAL PASS, not FINAL PASS. The deferred items require explicit user approval.
