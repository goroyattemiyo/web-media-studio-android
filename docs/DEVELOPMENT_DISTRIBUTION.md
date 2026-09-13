# WMS Android — Development distribution

Last updated: 2026-09-14 JST

Scope: local/private development APKs only. No Play Store release is planned.

## Produce a repeatable artifact

From the repository root on Windows:

```powershell
.\scripts\local-verify.ps1
```

The script uses the Gradle 8.13 Wrapper and runs, in order:

```text
testDebugUnitTest -> lintDebug -> assembleDebug
```

After all tasks pass, it copies the APK into ignored `dist/` with the app version,
versionCode, and 12-character Git checkpoint in the filename. A dirty worktree is
explicitly suffixed `-dirty`. The script also writes a sibling `.sha256` checksum file.
A failed verification never publishes a new distribution copy; share only clean-checkpoint artifacts.

To build and verify an update installation on one connected device:

```powershell
.\scripts\local-verify.ps1 -Install -Serial 6167306007fe
```

The install path uses `adb install -r`, preserving app data, and then verifies the
installed package's versionName and versionCode. Omit `-Serial` only when exactly one
adb target is available.

## Signing strategy

Debug APKs are signed by the local Android debug keystore. They can update an installed
build only when the signing certificate is the same. A debug APK produced on another PC
may therefore fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

For distribution beyond the current single-development-machine workflow:

1. create a dedicated development keystore outside the repository;
2. store its base64 content and passwords only in an approved secret store;
3. inject signing values at build time without committing `*.jks`, `*.keystore`, or
   `keystore.properties`;
4. record and verify the signing-certificate SHA-256 before the first shared install;
5. back up the keystore separately because losing it prevents in-place updates.

No signing secret is currently required or stored in this repository. GitHub Actions
must not be run or changed to consume secrets without explicit approval.

## Install and update

- Prefer `adb install -r <artifact>` for an update that preserves app data.
- Confirm package `com.goroyattemiyo.wms`, versionName, and versionCode after install.
- Smoke-test retained Room rows, Appearance DataStore values, playback, and one screen
  that changed in the new build.
- Never distribute an APK whose checksum does not match its sibling `.sha256` file.

## Rollback

Keep the previous known-good versioned APK and checksum until the new build passes its
device smoke test.

1. Stop and diagnose the new build before changing installed data.
2. Check whether Room/DataStore schemas remain backward-compatible with the old APK.
3. If compatible and signed with the same key, Android may allow a development downgrade:

   ```powershell
   adb install -r -d <previous-known-good.apk>
   ```

4. Verify the installed version and retained Library/Playlist data immediately.
5. If Android rejects the downgrade or the data schema is not backward-compatible, do
   not uninstall automatically. Uninstalling removes app-private data and managed media;
   obtain explicit user approval and arrange any required export first.

## GitHub artifacts and Releases

The existing workflow remains an explicitly triggered final-CI path. Gate A8 local work
does not run it. A private GitHub Release APK remains optional and is not created unless
the user explicitly approves the external upload and release action.
