# Hive AI

Autonomous AI agents that run a service business end to end: they find leads, email them, do the work (websites, graphics, copy, digital goods) and close with a Stripe link only when a buyer is ready.

The company starts with **one lead bot**. It knows it runs **Local Reach Workshop**, and it hires the marketing, research, sales, product and proofreading people it needs. The owner can hire or delete anyone, and can give the lead a work order in plain language from the chat.

Android app — Kotlin + Jetpack Compose (Material 3), OkHttp, kotlinx.serialization. Models run on NVIDIA's hosted API.

## Status

| | |
|---|---|
| Version | 2.11.0, build 18 — tagged `v2.11.0+18` |
| Package | `com.hive.agents` |
| Source | Kotlin + Compose, right here |
| Build | `gradle :app:assembleDebug` — verified from a source-only copy |
| Nature | **clean-room reconstruction** of the shipped 2.9.0 APK, plus features added since |

This repo was rebuilt from the released APK: the shipped binary was analysed for its package, stack, services and screen map, and the implementation was written fresh against that map. The architecture matches (lead bot, tool registry, approvals, vault, per-agent browser windows), but names, structure and UI details are not byte-identical to whatever produced the APK.

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
| Starting team | One bot, `Chief`, knows it runs Local Reach Workshop and builds the rest of the company itself |
| Hiring | `hire_agent` tool: the lead (or any bot) creates marketing, research, sales, product and proofreading bots; the human can hire or delete, and deletion removes that bot's memory |
| Missions | Plan → task split → dispatch → timeline → deliverables |
| Tool registry | `remember`, `hire_agent`, `add_leads`, `record_sale`, `proofread`, `fetch_page`, `send_email`, `create_payment_link`, `save_deliverable`, `search_web`, `add_prospect`, `message_teammate`, `open_website`, `read_page`, `click_text`, `type_into`, `create_product`, `create_checkout` |
| Chat as work order | A human message gives the bot a real turn with tools, so "add a product" or "make a lead list" actually happens |
| Lead generation | Sales bots build lead lists with `search_web` + `fetch_page`, add them with `add_leads`, contact them, and record closings with `record_sale` |
| Proofreading | Every outbound email, product description and COPY deliverable goes through `agent/Proofreader` first: keeps facts and prices, cuts AI filler, never invents a claim |
| Approvals | Payment links always ask. Email approval is a Setup switch; when off, outreach still passes the proofreader before sending. Unanswered background approval is a refusal |
| Memory | Per-agent memory plus a mistake ledger recalled before similar work; shared business facts seeded on first launch |
| Email | Per-agent inbox + sent viewer (AgentMail) |
| Browsers | A **Browsers tab** lists every bot; one live window per bot, edge-to-edge safe so the chrome and page fit the screen, with zoom, address bar and human takeover |
| Autopilot | **On by default.** Runs a shift when the app opens, then every 15 minutes from the standing goal |
| Progress | Measured by **sales made**: Home and Business show sales count and collected revenue, and the bots are told to judge themselves the same way |
| Products | Digital goods and AI-run services with an on-demand Stripe checkout; the Business tab shows them without manual add forms |
| Sales | Pipeline with advance and on-demand Stripe link, services list, owner per lead |
| Studio | Deliverables and a suggestion box that writes coding-agent prompts |
| Hive view | Animated 3D-style graph of the team and its messages |
| Vault | Keys AES-GCM encrypted with Android Keystore, atomic `hive-secrets.json`, plus a backup copy; state has a backup too |

## Known gaps

- Android shares one cookie jar per app: each bot has its own browser window and history, but a login is visible to every bot. The Browsers screen says so before you sign in.
- One browser window is live at a time. Opening another bot's window brings that bot forward; the previous bot's page is not kept mounted.
- The proofreader removes AI tells and protects facts, but it is not a fact-checker. A bot can still write something wrong; keeping claims true remains the agent's job.
- Nothing has been run on a device or emulator. The APK compiles from a clean source copy; screens and tools are untested at runtime.
- Release signing is blocked: the original keystore is not available, so a build cannot install over the 2.9.0 APK as an update. See `docs/source-recovery.md`.
- WorkManager may delay the 15-minute shift under Doze.

## Non-negotiables

1. **Stripe** links are created on demand, when a buyer is ready — never pre-generated, never batched.
2. **Payment links always require human approval.** Email approval is the owner's switch, and every send is proofread. No invented contacts.
3. **API keys live in the app vault**, never in source, never in this repo, never in a build.
4. **Every build is signed with the same release key** so it installs as an update and keeps user data.

## Repo layout

```
AGENTS.md                      rules every coding agent must follow
app/src/main/java/com/hive/agents/
  agent/                       TeamEngine, ToolRegistry, Proofreader, Approvals,
                               MemoryService, SuggestionBox, HiveGraph
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
