# Changelog

## v2.13.0+20 — 2026-11-23

Fixes the retired-model failure that made shifts look complete while doing nothing, gives the company a real chat and a station view, and cuts the app to the five tabs the owner asked for.

**Fixed**
- **HTTP 410 Gone**: 2.12 used `deepseek-ai/deepseek-v4.1-flash`, which NVIDIA has retired. The current id is `deepseek-ai/deepseek-v4-flash`. Saved settings and per-bot overrides are migrated on launch.
- **Fake mission success**: `NimClient` used to return `"model call failed: ..."` as message content, so a failed task was marked DONE. It now throws `NimException` on HTTP errors, unreadable replies and empty responses. The task becomes FAILED and the mission becomes BLOCKED.
- **Retired ids mid-session**: the client reads `/v1/models`, caches it, picks a live model from the same family when needed, and retries a 404/410 once on another live model.
- **Stuck solo company**: if the lead still has not hired anyone after a shift (or on the next app open after a previous shift), `ensureCoreTeam()` locally onboards Scout, Closer, Spark, Maker, Demo and Proof.
- **Run shift silent no-op**: shifts now serialise on a mutex; a tap while the loop is running waits instead of disappearing. A failed shift shows a notice and the bot reports in Chat.
- **3D highlighted the wrong tab**: the station view now highlights Home; Browsers is selected only for the browser list.

**Added**
- **Grok Bot-style company chat**: `ChatScreen` has a left rail of bot blobs and a Company thread, per-bot threads, bot-to-bot handoffs and DMs, iMessage-dark bubbles, timestamps, a live working bar, and fenced-code blocks with Copy.
- **StarNet-inspired station**: `HiveVizScreen` is now a 2.5D cutaway with seven department rooms, isometric lids, hallways as handoff lanes, bots at workstations, moving message packets, tap-to-isolate and a crew/work list.
- **Visible handoffs**: task assignment from the lead, the assignee's acknowledgement and the result are all posted as real HANDOFF messages.
- **Recent work on Home**: latest deliverables and customer samples are visible after the Business tab is removed.
- Shared `AgentAvatar`; Home crew strip; five-tab shell.

**Changed**
- Bottom navigation is exactly **Home, Chat, Browsers, Email, Settings**. Business, Studio and Agents are gone as tabs. Team is reachable from Home and Chat; 3D is reachable from Home and Chat.
- No global model header; Home, Chat and Settings show model context where it belongs.
- Settings is a tab, not a back-stack screen; Email and Browsers are tabs without redundant back links.
- Products and services are made by the bots on demand; there is no manual Business form.
- Version 2.13.0 / versionCode 20.

**Verified**
- `gradle clean :app:assembleDebug` succeeds from a source-only copy of this tree: debug APK **28,187,801 bytes**, SHA-256 `ba4618f3210317194aa3f21d6156489d7ecf68076ff1180622f4db06b1f94ad6`.
- APK metadata: `com.hive.agents`, versionCode 20, versionName 2.13.0, minSdk 28, targetSdk 35.
- Not verified on a device or emulator: **no screen has been run**. The station, chat, handoffs, model fallback, crew seeding and loop are compiled, not exercised.

**Known limits**
- The foreground loop spends API credits while the app process is alive; turn Autopilot off to stop it. There is no daily cap yet.
- Parallel tasks assigned to the same bot can interleave messages.
- Stripe payments are recorded by the bot or the human; there is no webhook listener.
- One browser window and one cookie jar for the whole app.
- Background WorkManager shifts still use the standing goal rather than the foreground funnel order.

## v2.12.0+19 — 2026-11-23

The company runs a continuous sales loop, the crew works in parallel on different models, and the hive deck shows the traffic.

**Added**
- **Hive deck** (`HiveVizScreen`): a tilted pseudo-3D orbit with depth-scaled bots, glowing packets for every recent message, a live/idle badge, a message flow list, and a per-bot work distribution panel with the model each bot is on. Tap a bot to trace only its traffic.
- `agent/ModelRouter.kt`: lead and product use Super, research/sales/marketing/showcase use Lightning, proofreader uses DeepSeek. `Agent.model` overrides and Setup has a route-models switch.
- **Continuous company loop** in `HiveViewModel`: while autopilot is on and the process is alive, the team runs a shift every 45 seconds. `nextCompanyMission` walks the funnel: paid delivery, missing contact research, create an offer, make samples, outreach, follow-up, new leads. All-failed shifts wait five minutes.
- `create_sample` tool, `Deliverable.isSample`, a Showcase role, and a SAMPLES section in Business, so buyers can see the work before paying.
- `Prospect.paid`/`delivered` and the `deliver_work` tool. `record_sale` records payment; `deliver_work` saves the finished work and closes the job. Manual advance to WON marks paid.
- Live pipeline snapshot in every system prompt: lead company, stage, email, paid/delivered, value, samples and sales totals.
- `AGENT_README.md`: the living handoff a coding agent must read and update each version.

**Changed**
- `TeamEngine.runMission` now dispatches all planned tasks at the same time with `coroutineScope`/`async`; the old sequential for-loop is gone. Approval dialogs are serialized with a `Mutex`; the calls after approval still run in parallel.
- The team is told to hand work to another bot with `message_teammate`, and to deliver paid work before hunting new leads.
- The Showcase bot is part of the hiring instructions and the shared business facts.
- Setup copy now describes the loop and the per-role model switch.

**Verified**
- `gradle clean :app:assembleDebug` succeeds from a fresh public clone at commit `34c34711`: debug APK **28,089,497 bytes**, SHA-256 `5f9b9bde70c2dd5279acb2f4b41444f20fc9d18716ccd7e8c5f31b4f192451e0`. The APK was downloaded again after upload and the hash matched.
- Not verified on a device or emulator: **no screen has been run**. The deck, loop, parallel calls, samples and delivery are compiled, not exercised.

**Known limits**
- The foreground loop spends API credits while the app process is alive; turn Autopilot off to stop it. There is no daily cap yet.
- Parallel tasks assigned to the same bot can interleave messages.
- Stripe payments are recorded by the bot or the human; there is no webhook listener.
- One browser window and one cookie jar for the whole app.

## v2.11.0+18 — 2026-11-23

The company now starts with one lead bot who builds the team, and it runs itself.

**Added**
- One starting bot: `Chief`, the Founder. It knows it runs Local Reach Workshop, that the business sells digital goods and AI-run services, and that its job is to build a marketing team, a research team, a sales team, a product team and a proofreader.
- `hire_agent` tool, so the lead or any bot can create the employees the work needs. Human hiring is still in Agents.
- Delete a bot from Agents. Its memory and automations go with it; its past messages stay in the chat. Deleting the last bot starts a fresh lead so the company keeps running.
- `agent/Proofreader`: every outbound email, product description and COPY deliverable is rewritten before it leaves the building. It keeps facts, prices and links, cuts AI filler, and never invents a claim.
- `add_leads`, `record_sale`, `proofread` and `fetch_page` tools. Sales bots can build a list of real businesses, research them, add them in one call, contact them and record the closings.
- Browsers is now a bottom-nav tab. It lists every bot, shows which window is live, and opens or brings forward any bot's browser.
- Home and Business measure progress by **sales made** and money collected, not activity.
- Setup now has the EIN, storage credentials, a proofreading switch and an email-approval switch, and says exactly when keys survive an update.
- `hive-secrets.bak` and `hive-state.bak`: a second copy of the vault and the state, used if the main file is ever half-written.

**Changed**
- The Business tab is read-only. Products, leads and services are added by the bots; the human can still advance a lead or create a payment link when a buyer is ready.
- Chat is a work order: `TeamEngine.converse` gives the bot its tools during the reply, so "add a product" or "make me a lead list" actually changes the state.
- Autopilot is on by default. A shift starts when the app comes to the front and the 15-minute WorkManager schedule keeps running in the background.
- `send_email` proofreads, marks the matching lead as pitched, and honours the email-approval switch. Payment links still always ask.
- `create_product` descriptions and COPY deliverables pass the proofreader.
- The browser window is edge-to-edge safe, with compact chrome, a scrollable control row and zoom, so it fits the visible screen instead of sliding under the system bars.
- One-time upgrade migration: the old auto-hired trio collapses to the lead, autopilot turns on, and email approval turns off unless the owner changes it.

**Verified**
- `gradle clean :app:assembleDebug` succeeds from a source-only copy of this tree (no copied build outputs): debug APK **28,007,577 bytes**, SHA-256 `c360d6064e7e7ce41818122ea5ccb938d2f53d726f5a53201b2e7f93dee8d43f`.
- Not verified on a device or emulator: **no screen has been run**. Hiring, proofreading, lead tools, autopilot and the browser layout are compiled, not exercised.

**Known limits**
- Android has one cookie jar per app, and one browser window is mounted at a time.
- WorkManager may delay the 15-minute shift under Doze.
- The proofreader removes AI tells and protects facts, but it cannot guarantee a claim is true.

## v2.10.0+17 — 2026-11-23

Browsers, autopilot and products. Bots can now drive a real browser window, sell what they make, and keep working while the app is closed.

**Added**
- `browser/AgentBrowserActivity`: one real WebView window per bot, with an address bar, back/forward/reload, and full human control for logins, CAPTCHA and 2FA.
- `browser/BrowserHub`: the bridge between the model and that window — open a page, read its visible text, click the link or button whose label matches, type into a named field.
- New tools: `message_teammate`, `open_website`, `read_page`, `click_text`, `type_into`, `create_product`, and `create_checkout` (approval-gated).
- `Product` in state and a DIGITAL PRODUCTS section in Business, with an on-demand Stripe checkout per product.
- `service/AutopilotWorker` on WorkManager: every 15 minutes it picks up the oldest planned mission, or starts one from the standing goal. Off by default; enable it in Setup.
- `agent/HiveGraph`: one shared object graph for the UI and the worker, so an approval raised by background work reaches the screen.
- New Home dashboard: booked revenue, pipeline, automation rate, agent network with live status lines, activity feed, missions and a command bar.
- Setup: standing goal, autopilot switch, business identity fields.

**Changed**
- `HiveViewModel` runs on the shared graph instead of building its own dependencies.
- Every bot's system prompt states the business (Local Reach Workshop), that it sells digital goods and AI-run services, and that a Stripe link exists only when a buyer is ready. The same facts are seeded into shared memory on first launch.
- Bottom navigation is Home / Agents / Chat / Business / Setup.

**Verified**
- `gradle :app:assembleDebug` succeeds from a clean clone of this repository at this tag (27.9 MB debug APK).
- Not verified on a device or emulator: **no screen has been run**. The browser tools, products and autopilot are compiled, not exercised.

**Known limits**
- Android has one cookie jar per app, so a browser login is visible to every bot even though each bot has its own window and history.
- Autopilot is a 15-minute periodic worker; Android may delay it under Doze.

## v2.9.0+16 — 2026-11-23

First source drop. Reconstructed from the shipped `hive-2.9.0.apk` (versionCode 16).

**Added**
- Gradle project: AGP 8.7.3, Kotlin 2.0.21, Compose Material 3, minSdk 28, targetSdk 35.
- Domain model for agents, missions, tasks, messages, deliverables, memory, automations, prospects, services, mail and suggestions.
- `HiveStore`: one state file, atomic temp-file rename, `StateFlow`.
- `KeyVault`: AES-GCM via Android Keystore, own `hive-secrets.json`, atomic writes.
- `NimClient` for the NVIDIA endpoint with tool calling; `AgentMailApi`; `StripeApi`.
- `ToolRegistry` with AUTO/ASK risk classes; `ApprovalBroker` cards with timeout-as-refusal.
- `TeamEngine`: role hiring, mission planning, dispatch loop, timelines, handoffs, DMs.
- `MemoryService` with a mistake ledger and pre-task recall.
- `SuggestionBox` that emits coding-agent prompts.
- Ten screens: Missions, Mission, Team, Chat, Inbox, Sales, Studio, Browsers, HiveViz, Settings.

**Verified**
- `gradle :app:assembleDebug` succeeds from a clean clone of this repository.

**Not in this build**
- Browser automation, scheduled workers, voice, MCP connectors, multi-device sync.
- Release signing (original keystore unavailable).

## Earlier
- `2.9.0` APK shipped before this repo existed; its analysis is in `docs/apk-2.9.0-analysis.md`.
