# Hive AI

Autonomous AI agents that run a service business end to end: they find leads, email them, do the work (websites, graphics, copy, digital goods) and close with a Stripe link only when a buyer is ready.

The company starts with **one lead bot**. It knows it runs **Local Reach Workshop**, and it hires the marketing, research, sales, product, showcase and proofreading people it needs. If the lead model fails or refuses to hire, a local onboarding pass gives the company its core crew instead of leaving it stranded at one bot.

While the app is open the company runs a continuous loop: research real local leads, work out what each one needs, make samples a buyer can see, reach out, follow up, close, take payment, then deliver the work. The crew works in parallel, with the lead and product bot on a strong model, research and sales on a fast model, and the proofreader on a cheap one. Model ids are checked against NVIDIA's live catalog, because retired ids were making shifts fail silently.

The app is five tabs: **Home, Chat, Browsers, Email, Settings**. Chat is a Grok Bot-style company thread with a left rail of bot blobs, visible bot-to-bot handoffs and DMs, iMessage-dark bubbles and code blocks. The 3D view is a StarNet-inspired station cutaway: rooms are departments, hallways are handoffs, and every moving dot is a real message.

Android app — Kotlin + Jetpack Compose (Material 3), OkHttp, kotlinx.serialization. Models run on NVIDIA's hosted API.

## Status

| | |
|---|---|
| Version | 2.13.3, build 23 — tagged `v2.13.3+23` |
| Package | `com.hive.agents` |
| Source | Kotlin + Compose, right here |
| Build | `gradle :app:assembleDebug` — verified from a clean clone with `--no-build-cache` |
| Nature | **clean-room reconstruction** of the shipped 2.9.0 APK, plus features added since |

This repo was rebuilt from the released APK: the shipped binary was analysed for its package, stack, services and screen map, and the implementation was written fresh against that map. The architecture matches (lead bot, tool registry, approvals, vault, per-agent browser windows), but names, structure and UI details are not byte-identical to whatever produced the APK.

## Build it

Requires JDK 17 and Android SDK `platforms;android-35` + `build-tools;35.0.0`, Gradle 8.9.

```bash
echo "sdk.dir=/absolute/path/to/android-sdk" > local.properties
gradle :app:assembleDebug
```

Android Studio can open the project root directly and will create the Gradle wrapper on first sync. Nothing is vendored; the first build downloads from `google()` and `mavenCentral()`.

## For coding agents

Read **`AGENT_README.md`** first. It is the living handoff: what changed in this version, the build hash, the architecture map, invariants that must not regress, and the next known gaps. It is updated on every release, and so should you update it.

## What works in this version

| Area | State |
|---|---|
| Starting team | One bot, `Chief`, knows it runs Local Reach Workshop and builds the rest of the company itself; `ensureCoreTeam` is the local safety net if a shift leaves it alone |
| Hiring | `hire_agent` tool: the lead (or any bot) creates marketing, research, sales, product, showcase and proofreading bots; the human can hire or delete, and deletion removes that bot's memory |
| Missions | Plan → task split across several bots → **parallel waves with handoffs** → deliverables; a failed plan falls back to the standing role plan so the whole crew still works |
| Company chat | Left rail of bot blobs plus Company, per-bot threads, visible DMs/handoffs, iMessage-dark bubbles, timestamps and copyable code blocks |
| Tabs | Exactly Home, Chat, Browsers, Email, Settings. Business, Studio and Agents are not tabs |
| Models | Per-role routing, a live `/v1/models` check, retired-id migration, generous reasoning budgets and an empty-response retry; a failed call becomes a failed task, never fake bot copy |
| Web research | `search_web` uses Google Custom Search when a key and `cx` are in the vault, then DuckDuckGo, then Bing RSS |
| Chat with the boss | A message to `Chief` becomes a real mission for the whole team (`takeOrder`), and the reply is the per-bot result list |
| Tool registry | `remember`, `hire_agent`, `add_leads`, `record_sale`, `deliver_work`, `create_sample`, `proofread`, `fetch_page`, `send_email`, `create_payment_link`, `save_deliverable`, `search_web`, `add_prospect`, `message_teammate`, `open_website`, `read_page`, `click_text`, `type_into`, `create_product`, `create_checkout` |
| Chat as work order | A human message gives the bot a real turn with tools, so "add a product" or "make a lead list" actually happens; assistant `tool_calls` are echoed back and results are `role: "tool"` messages, so the model can see what it already ran |
| Lead generation | Sales bots build lead lists with `search_web` + `fetch_page`, add them with `add_leads`, contact them, and record closings with `record_sale`; search falls through DuckDuckGo challenges to Bing RSS |
| Proofreading | Every outbound email, product description and COPY deliverable goes through `agent/Proofreader` first: keeps facts and prices, cuts AI filler, never invents a claim |
| Approvals | Payment links always ask. Email approval is a Setup switch; when off, outreach still passes the proofreader before sending. Unanswered background approval is a refusal |
| Memory | Per-agent memory plus a mistake ledger recalled before similar work; shared business facts seeded on first launch |
| Email | Per-agent inbox + sent viewer (AgentMail) |
| Browsers | A **Browsers tab** lists every bot; one live window per bot, edge-to-edge safe so the chrome and page fit the screen, with zoom, address bar and human takeover |
| Autopilot | **On by default, starting on the first screen.** While the app is open the team loops through the sales funnel every 45 seconds; WorkManager adds a 15-minute shift in the background. A wholly failed shift backs off for five minutes and reports in Chat |
| Progress | Measured by **sales made**: Home shows sales count and collected revenue, and the bots are told to judge themselves the same way |
| Fulfilment | `record_sale` records payment, `deliver_work` saves the finished work and closes the job; paid, undelivered work is the loop's first priority |
| Samples | The Showcase bot makes customer-facing samples with `create_sample`; they are proofread and shown in Home's RECENT WORK strip |
| Products | Digital goods and AI-run services are made by the crew on demand; there is no manual Business tab or add form |
| Deliverables | Finished work lives in state and appears in Home's RECENT WORK strip; mission timelines are still in state |
| Hive Station | 2.5D station cutaway: seven department rooms, isometric lids, hallways as handoff lanes, bots at workstations, live message packets, tap-to-isolate, live flow and per-bot work distribution |
| Vault | Keys AES-GCM encrypted with Android Keystore, atomic `hive-secrets.json`, plus a backup copy; state has a backup too |

## Known gaps

- Android shares one cookie jar per app: each bot has its own browser window and history, but a login is visible to every bot. The Browsers screen says so before you sign in.
- One browser window is live at a time. Opening another bot's window brings that bot forward; the previous bot's page is not kept mounted.
- The proofreader removes AI tells and protects facts, but it is not a fact-checker. A bot can still write something wrong; keeping claims true remains the agent's job.
- Nothing has been run on a device or emulator. The APK compiles from a clean clone; screens and tools are untested at runtime. No NVIDIA key was available, so the planning fallback, waves and handoff prompts are compiled, not exercised.
- Search depends on Google Custom Search keys (optional), public DuckDuckGo and Bing pages. Add a paid search API if search becomes a production dependency.
- Socials are not integrated. Reading or posting on Reddit, Instagram and similar needs per-service API credentials or OAuth; the per-bot browser can only read what the human signed into.
- Release signing is blocked: the original keystore is not available, so a build cannot install over the 2.9.0 APK as an update. See `docs/source-recovery.md`.
- WorkManager may delay the 15-minute shift under Doze, and its background shift still starts from the standing goal rather than the foreground funnel order.
- The foreground loop spends API credits while the app process is alive. Turn Autopilot off to stop it; there is no daily cap yet.
- Tasks run at the same time. If two are assigned to the same bot, their messages can interleave.
- The station, chat rail, code blocks, crew fallback and model fallback are compiled, not viewed on a device.

## Non-negotiables

1. **Stripe** links are created on demand, when a buyer is ready — never pre-generated, never batched.
2. **Payment links always require human approval.** Email approval is the owner's switch, and every send is proofread. No invented contacts.
3. **API keys live in the app vault**, never in source, never in this repo, never in a build.
4. **Every build is signed with the same release key** so it installs as an update and keeps user data.

## Repo layout

```
AGENTS.md                      rules every coding agent must follow
AGENT_README.md                living handoff; read first, update every version
app/src/main/java/com/hive/agents/
  agent/                       TeamEngine, ModelRouter, ToolRegistry, Proofreader, Approvals,
                               MemoryService, SuggestionBox, HiveGraph
  browser/                     BrowserHub, AgentBrowserActivity (one window per bot)
  data/                        models, atomic state store, encrypted KeyVault
  net/                         NimClient, AgentMailApi, StripeApi, shared OkHttp client
  service/                     AutopilotWorker (WorkManager shift every 15 minutes)
  ui/                          activity, navigation, view model, components, theme
  ui/screens/                  Home, Team, Chat, Inbox, Browsers, HiveViz, Settings
                               (Missions/Sales/Studio files remain but have no tab)
docs/
  feature-checklist.md         Grok Bot parity checklist with per-feature status
  product-spec.md              behaviour that must not drift, plus open questions
  grok-bot-notes.md            research notes and sources
  apk-2.9.0-analysis.md        analysis of the shipped APK
  source-recovery.md           provenance, and the signing-key blocker
CHANGELOG.md
```

## Versioning

`vMAJOR.MINOR.PATCH+<versionCode>`. Every shipped version gets a commit, a tag, and a release note describing what changed and what to test — plus an updated `AGENT_README.md`.
