# Provenance and the signing blocker

## Where this source came from

`app/` is a clean-room reconstruction, not the original project. The shipped `hive-2.9.0.apk` (versionCode 16) was analysed statically — package, toolchain, permissions, components, third-party endpoints, model ids and the full `com.hive.agents.*` class map — and the implementation was written fresh against that map.

What that gives you: a compiling project with the same architecture, the same screens, the same data model and the same integration points, that you can iterate on today.

What it does not give you: the original code. The APK carries no debug symbols and no VCS metadata (`NO_SUPPORTED_VCS_FOUND`), so nothing beyond behaviour could be recovered from it.

## Blocker: release signing

A build only installs as an update over the shipped 2.9.0 app if it is signed with the same private key (`cert SHA-256 10af94b3…d8a428`). A fingerprint cannot sign anything.

- **With the keystore:** `adb install -r` keeps app data, so stored API keys survive an update.
- **Without it:** every new build gets a different signature, Android forces an uninstall, and the uninstall wipes the vault.

Until the keystore is recovered, development builds are debug-signed and must be installed as a separate app (or after uninstalling, which loses the vault). The mitigation already in the code is that keys live in `hive-secrets.json` behind an Android Keystore key, so exporting and re-importing the vault is the recovery path — a vault export/import screen is still on the checklist.

## What to do once the keystore is found

1. Put it outside version control (for example `~/.android/hive-release.jks`).
2. Add a `signingConfigs.release` block that reads the path and passwords from environment variables or `local.properties`.
3. Never commit the keystore or its password.
4. Re-tag the first release-signed build so the update path is auditable.
