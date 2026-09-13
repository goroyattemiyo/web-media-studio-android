# Gate A7 — Provider matrix

Last updated: 2026-09-13 JST

Branch: `feat/gate-a7-provider-matrix`

Status: **IN PROGRESS**

Search, Probe, Acquire, and Playback are recorded independently. A provider is never
marked supported solely because yt-dlp contains an extractor.

| Provider | Search | Probe | Acquire | Playback | Target-device result |
| --- | --- | --- | --- | --- | --- |
| YouTube public | PASS | PASS | MP3/M4A/MP4 PASS | audio/video PASS | Verified through Gates A1 and A6 |
| Direct public media URL | N/A | PASS | MP3 PASS | audio PASS | Wikimedia Commons direct Ogg URL |
| TikTok public post | N/A | SKIPPED | SKIPPED | SKIPPED | Explicitly skipped by user decision |
| Instagram public Reel | N/A | PASS | MP4 PASS | video PASS | Public no-login Reel verified on device |

## Direct public media URL evidence

- Source: Wikimedia Commons `Testecon.ogg`, a 7.1-second test sound released into the public domain by its author.
- Direct host: `upload.wikimedia.org`; no provider extractor identity was implied.
- Probe returned `Testecon` and displayed `upload.wikimedia.org`.
- MP3 192 acquisition produced a non-empty 171,824-byte managed file.
- The Room-backed recent card recorded `upload.wikimedia.org · audio`.
- `閉じて再生` played the complete item and the Mini Player reached `再生完了`.

## Supplied social URL results

- TikTok `https://www.tiktok.com/ja-JP/` is a landing page rather than a public post URL. Probe surfaced `UNSUPPORTED_SOURCE`; it was not used to judge TikTok post support and no acquisition was attempted.
- The supplied Instagram post returned `LOGIN_REQUIRED` during Probe. WMS displayed that login or cookies would be required and treated it as outside the supported product boundary. No acquisition was attempted.
- A second public Instagram Reel probed successfully as `Video by otohachan._.n` / `Instagram` without cookies or login.
- The authorized Reel acquired as an 8,876,779-byte MP4, registered in Library, and played through the WMS MediaSession.
- Native Now Playing exposed a live video `SurfaceView`; Android's MPEG4 extractor opened the exact managed MP4 and no playback/video-renderer fatal error was reported.
- TikTok post evaluation was explicitly skipped by user decision; the matrix makes no TikTok support claim.

## Result vocabulary

- `PASS`: exercised successfully on the target device with an authorized public URL.
- `FAIL`: exercised on the target device and failed; record the surfaced WMS error code.
- `BLOCKED`: a current provider or runtime condition prevented a meaningful test.
- `OUT OF SCOPE`: login, cookies, DRM, private media, or bypass behavior would be required.
- `SKIPPED`: deliberately not evaluated; no support claim is made.
- `N/A`: the capability is intentionally not offered for that provider.

## Product boundaries

- Test only public media the user is authorized to save.
- Do not add cookies, login automation, proxy rotation, arbitrary yt-dlp flags, or bypasses.
- Keep the rights-confirmation step mandatory for every acquisition.
- Record a tested failure honestly; do not broaden support claims to an entire provider.
