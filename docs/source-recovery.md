# Getting the source into this repo

This repo currently holds documentation only. The 2.9.0 APK is compiled Kotlin; there is no recoverable source inside it (no debug info, no VCS metadata).

## The three paths

1. **Reopen the original Freebuff chat.** Its workspace still holds the real Gradle project. Export or zip it, then commit it here. This is the only path that returns the actual code, including the signing keystore if it was generated there.
2. **A repo or zip from you.** Fastest if the project lives anywhere else already.
3. **Decompile and rebuild.** `jadx` gives Java-ish output and recovered resources; a fresh Gradle project is then assembled around it. Behaviour can be matched, but names, structure and Compose UI details will differ. Treat this as a rewrite, not a restore.

## Blocker: the signing key

A build can only install as an update over 2.9.0 if it is signed with the **same private key** (`cert SHA-256 10af94b3…d8a428`). The fingerprint alone is not enough to sign anything.

Consequences:

- With the keystore: `adb install -r` keeps app data, so the stored API keys survive.
- Without it: every new build needs a different signature, which forces an uninstall — and an uninstall wipes the vault. Keys would have to be re-entered, or restored from an export.

So the checklist item "keys survive updates" is really "recover the keystore". Until then, plan a vault export/import path as the fallback.

## Once the source lands

- Keep `applicationId com.hive.agents` and the existing signing config.
- `git tag v2.9.0+16` at the first commit of the real source so the shipped build has a marker.
- Put the keystore outside the repo (or in an ignored directory) and never commit its password.
