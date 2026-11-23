# Hive AI

Autonomous AI agents that run a service business end to end: they find leads, email them, do the work (websites, graphics, copy, digital goods) and close with a Stripe link only when a buyer is ready.

Android app — Kotlin + Jetpack Compose (Material 3), Room, WorkManager, OkHttp. Models run on NVIDIA's hosted API.

## Status

| | |
|---|---|
| Shipped build | `hive-2.9.0.apk`, versionCode 16, minSdk 28, targetSdk 35 |
| Package | `com.hive.agents` |
| Source in repo | **not yet** — see `docs/source-recovery.md` |
| Next milestone | recover source + signing key, then feature work from the checklist |

## Repo layout

```
AGENTS.md                      rules every coding agent must follow
README.md
.gitignore
docs/
  feature-checklist.md         Grok Bot parity checklist with per-feature status
  product-spec.md              what Hive AI is meant to do, data model, open questions
  grok-bot-notes.md            research notes + sources
  apk-2.9.0-analysis.md        what is inside the shipped APK
  source-recovery.md           how the source gets into this repo, and the blockers
```

## Non-negotiables

1. **Stripe** links are created on demand, at the moment a buyer is ready — never pre-generated, never in bulk.
2. **Money, sending, publishing and deletion always require human approval** in the app.
3. **API keys live in the app vault**, never in source, never in this repo, never in a build.
4. **Every build is signed with the same release key** so it installs as an update and keeps user data.

## Versioning

`vMAJOR.MINOR.PATCH+<versionCode>`. Every shipped version gets a commit, a tag and a GitHub release note describing what changed and what to test.
