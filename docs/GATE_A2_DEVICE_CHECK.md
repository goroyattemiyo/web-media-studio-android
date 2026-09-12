# Gate A2 — Real-device verification checklist

Date: 2026-09-12 JST

Purpose: verify that acquisition is owned by the foreground service rather than the Activity and remains controllable when the UI leaves/recreates.

Status: **LOCAL PASS** on 2026-09-12 JST.

Verified code checkpoint: `3716ab5afe6d34821dfcddcb2d3b820afe66e1e1`.

Verified APK SHA-256: `0CCE4A1CC4AF6A45D7D11508D0F2C92CBE45392929DFBADC7929F45818CB1212`.

Target device: Redmi 12 5G (`23076RA4BR`), Android 15 / API 35.

## Preconditions

- install the Gate A2 debug APK over the existing WMS app
- use one public/permitted source already known to Probe successfully
- confirm enough free storage is available for the test media
- confirm Android notifications are allowed for WMS before the background-control checks

## A2-1 Start and continue outside the Activity

1. Launch WMS.
2. Search for a known media item or paste a known working URL.
3. Open `WMSに追加` and wait for automatic Probe.
4. Confirm the rights/permission checkbox.
5. Start `保存 / MP3 192 kbps`.
6. While progress is below 100%, press Home and leave WMS in the background.

PASS when:

- acquisition continues after leaving the Activity,
- `WMS 保存中` foreground notification remains visible,
- the notification shows progress/status text,
- returning to WMS shows the same active source and acquisition progress instead of starting a second job.

## A2-2 Activity recreation / return

While acquisition is still running:

1. leave WMS,
2. return through Recents or the notification,
3. if practical, rotate/recreate the Activity once,
4. inspect the Import Sheet.

PASS when:

- source URL is restored when the recreated UI has no current source,
- `acquiring` state remains active,
- current progress/status is restored,
- no duplicate acquisition starts,
- yt-dlp automatic update does not begin while acquisition is active.

## A2-3 Successful completion

Allow one acquisition to finish.

PASS when:

- notification changes to `WMS 保存完了`,
- WMS shows the saved title/path state,
- `閉じて再生` returns to Search Home,
- the resulting non-empty MP3 plays through Media3,
- only the completed final file remains in the acquired-media directory,
- the temporary job directory is removed.

## A2-4 Cancel from WMS

1. Start another acquisition.
2. Tap `キャンセル` in the Import Sheet before completion.

PASS when:

- acquisition stops,
- UI reports `キャンセルしました`,
- foreground notification is removed,
- no final partial MP3 is exposed as a successful result,
- temporary job output is cleaned.

## A2-5 Cancel from notification

1. Start another acquisition.
2. Leave WMS.
3. Tap the notification action `キャンセル`.
4. Return to WMS.

PASS when:

- the active yt-dlp process stops,
- WMS reports canceled state,
- no second job is created,
- temporary job output is cleaned.

## A2-6 Failure path

Use a controlled failure where practical, such as temporarily insufficient free space or an intentionally unsupported source. Do not bypass authentication/DRM/access controls for testing.

PASS when:

- Service exits foreground mode,
- WMS reports a user-facing error code/message,
- no empty/partial final file is registered as success,
- temporary job output is cleaned.

## Gate A2 exit

Gate A2 is PASS only after all essential flows below are confirmed on the target Android device:

`start -> leave Activity -> foreground notification continues -> return/recreate -> same job restored -> success playback`

and

`start -> cancel (UI or notification) -> process stops -> temporary output cleaned`

Do not merge PR #5 solely because CI is green; real-device verification is required.

## Verification record

- [x] A2-1 acquisition continued after leaving WMS and remained represented by its foreground notification
- [x] A2-2 notification return restored the managed job/result state without starting a duplicate job
- [x] A2-3 successful MP3 was non-empty, temporary job output was removed, and playback passed
- [x] A2-4 WMS UI cancellation stopped acquisition, removed its notification, created no partial final MP3, and emptied the job cache
- [x] A2-5 notification cancellation stopped acquisition, returned canceled state, created no duplicate/partial final item, and emptied the job cache
- [x] A2-6 controlled storage-permission failure surfaced a user-facing error, registered no success, and cleaned temporary output
- [x] saved-media seek, elapsed/total time, 10-second skip, pause, and resume controls passed

Post-cancel device inspection found no running `ManagedAcquisitionService`, no active `WMS 保存中` notification, and an empty internal `cache/wms-acquisition-jobs` directory. The acquired-media directory retained only the two already completed, non-empty MP3 files (7,167,788 bytes each).

This is a LOCAL PASS only. GitHub Actions and merge to `main` were not run.
