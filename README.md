# Hive AI

Autonomous AI agents that run a service business end to end: they find leads, email them, do the work (websites, graphics, copy, digital goods) and close with a Stripe link only when a buyer is ready.

Android app — Kotlin + Jetpack Compose (Material 3), OkHttp, kotlinx.serialization. Models run on NVIDIA's hosted API.

## Status

| | |
|---|---|
| Version | 2.10.0, build 17 — tagged `v2.10.0+17` |
| Package | `com.hive.agents` |
| Source | Kotlin + Compose, right here |
| Build | `gradle :app:assembleDebug` — verified from a clean clone |
| Nature | **clean-room reconstruction** of the shipped 2.9.0 APK, plus features added since |

This repo was rebuilt from the released APK: the shipped binary was analysed for its package, stack, services and screen map, and the implementation was written fresh against that map. The architecture matches (orchestrator, tool registry, approvals, vault, per-agent profiles), but names, structure and UI details are not byte-identical to whatever produced the APK.

## Build it

Requires JDK 17 and Android SDK `platforms;android-35` + `build-tools;35.0.0`, Gradle 8.9.

```bash
echo "sdk.dir=/absolute/path/to/android-sdk" > local.properties
gradle :app:assembleDebug
```

Android Studio can open the project root directly and will create the Gradle wrapper on first sync. Nothing is vendored; the first build downloads from `google()` and `mavenCentral()`.

## What works in this version

| Area | State |
|---|---|
| Orchestrator + hiring | Plans roles, hires specialists, persists them; the human can hire too |
| Missions | Plan → task split → dispatch → timeline → deliverables |
| Tool registry | `remember`, `send_email`, `create_payment_link`, `save_deliverable`, `search_web`, `add_prospect`, `message_teammate`, `open_website`, `read_page`, `click_text`, `type_into`, `create_product`, `create_checkout` |
| Approvals | ASK tools block on a human card; unanswered background work is a refusal |
| Memory | Per-agent memory plus a mistake ledger recalled before similar work; shared business facts seeded on first launch |
| Email | Per-agent inbox + sent viewer (AgentMail) |
| Browsers | One WebView window per bot: the human can sign in or solve a challenge, the bot can drive the same window |
| Autopilot | 15-minute WorkManager shift from the standing goal; off by default |
| Products | Digital goods and AI-run services with an on-demand Stripe checkout |
| Sales | Pipeline, services, on-demand Stripe link |
| Studio | Deliverables and a suggestion box that writes coding-agent prompts |
| Hive view | Animated 3D-style graph of the team and its messages |
| Vault | Keys AES-GCM encrypted with Android Keystore, atomic `hive-secrets.json` |

## Known gaps

- Android shares one cookie jar per app: each bot has its own browser window and history, but a login is visible to every bot. The Browsers screen says so before you sign in.
- No voice, no MCP connectors, no multi-device sync yet.
- Nothing has been run on a device or emulator. The APK compiles from a clean clone; screens and tools are untested at runtime.
- Release signing is blocked: the original keystore is not available, so a build cannot install over the 2.9.0 APK as an update. See `docs/source-recovery.md`.

## Non-negotiables

1. **Stripe** links are created on demand, when a buyer is ready — never pre-generated, never batched.
2. **Money, sending, publishing and deletion require human approval.**
3. **API keys live in the app vault**, never in source, never in this repo, never in a build.
4. **Every build is signed with the same release key** so it installs as an update and keeps user data.

## Repo layout

```
AGENTS.md                      rules every coding agent must follow
app/src/main/java/com/hive/agents/
  agent/                       TeamEngine, ToolRegistry, Approvals, MemoryService,
                               SuggestionBox, HiveGraph
  browser/                     BrowserHub, AgentBrowserActivity (one window per bot)
  data/                        models, atomic state store, encrypted KeyVault
  net/                         NimClient, AgentMailApi, StripeApi, shared OkHttp client
  service/                     AutopilotWorker (WorkManager shift every 15 minutes)
  ui/                          activity, navigation, view model, components, theme
  ui/screens/                  Home, Missions, Mission, Team, Chat, Inbox, Sales, Studio,
                               Browsers, HiveViz, Settings
docs/
  feature-checklist.md         Grok Bot parity checklist with per-feature status
  product-spec.md              behaviour that must not drift, plus open questions
  grok-bot-notes.md            research notes and sources
  apk-2.9.0-analysis.md        analysis of the shipped APK
  source-recovery.md           provenance, and the signing-key blocker
CHANGELOG.md
```

## Versioning

`vMAJOR.MINOR.PATCH+<versionCode>`. Every shipped version gets a commit, a tag, and a release note describing what changed and what to test.
