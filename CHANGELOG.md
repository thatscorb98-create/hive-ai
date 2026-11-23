# Changelog

## v2.13.4+24 — 2026-11-23

Production-readiness hardening: prompt-injection containment, truthful sales accounting, serialized cancellable shifts, and background persistence — with 26 new unit tests.

**Fixed — the agent could be steered by page content**
- **Untrusted tool output.** `search_web`, `fetch_page` and `read_page` results are wrapped in a per-call nonce marker the model is instructed to treat as data, never instructions. `remember` and `hire_agent` right after untrusted reads now require human approval.
- **Over-broad tools.** `ToolPolicy` gives each role the minimum toolset for its job; only the lead keeps everything. Non-lead bots can no longer email, pay, deliver or hire.
- **Email safety.** Approval defaults on for new and migrated installs; sends are capped at 5 per agent turn with an owner do-not-email list in Setup. `fetch_page` and `open_website` refuse private, loopback, link-local and non-http(s) destinations.

**Fixed — money could be invented**
- **`record_sale` asks first** and is the only path that marks a deal paid. Revenue and the sales count cover paid WON deals only; manual advancement to WON no longer implies payment.
- **`deliver_work` refuses unpaid jobs** and is still the only path that closes a delivered job.

**Fixed — overlapping shifts and swallowed stops**
- **One shift at a time.** `ShiftCoordinator` serializes the in-app loop, manual orders and the WorkManager worker; the worker skips instead of queueing behind a live shift.
- **Cancellation propagates.** `runCatching` no longer swallows it: a stopped mission becomes resumable, its queued tasks are dropped, and turning Autopilot off also cancels the scheduled worker.

**Fixed — Main-thread writes and silent failures**
- **Background persistence.** `HiveStore` writes through a single background writer with a write-after-read guard, a surfaced error flow, and a flush when the app stops. Caps now also cover missions (200, active preserved), sent mail (300) and suggestions (100).
- **Vault honesty.** Keystore read/write failures surface in the UI instead of silently returning empty keys, and saving refuses to overwrite an unreadable vault with blanks.
- **AgentMail parsing.** `message_id`/`timestamp` are read, HTTP failures throw instead of returning an empty inbox, and a dead legacy-array fallback (found by the new tests) is fixed.
- **Idempotent Stripe writes.** Every payment-link and price creation carries an `Idempotency-Key`.
- **Browser identity.** Opening another bot's page switches the activity to that bot and clears the previous page instead of acting on a stale window.

**Verified**
- `gradle clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-build-cache` succeeds: debug APK **28,269,721 bytes**, SHA-256 `62d36089f5d4cba567c3f66594f12da853e03245475fe8a2207119e35e6fdf11`.
- 26 unit tests pass (policy, email rules, URL guards, shift serialization, store caps/backup/failure reporting, settings defaults, paid-only accounting and mail parsing); lint reports 0 errors.
- APK metadata: `com.hive.agents`, versionCode 24, versionName 2.13.4, minSdk 28, targetSdk 35.
- Not verified on a device or emulator, and no NVIDIA/AgentMail/Stripe keys were available: nothing has been exercised against a live service.

**Known limits**
- Debug-signed: cannot install over the old 2.9.0/2.10.0 builds; the original release keystore is still missing.
- Injection defences reduce but cannot eliminate model risk; the email cap is per turn, and the proofreader may still rewrite text after approval.
- No daily API budget yet; the loop spends credits while the app process lives.

## v2.13.3+23 — 2026-11-23

Makes the crew actually work together: every shift hands a job to several bots, in waves, with the earlier wave's output passed to the next one. Also adds optional Google search.

**Fixed — only the boss and Scout ever worked**
- **A failed plan collapsed the shift to one task.** When the lead's JSON plan did not parse, `planTasks` returned a single `fallbackTask`, and that task went to the first non-lead bot (Scout). One bot worked, everyone else sat idle, and the mission could never get past its first step. Planning now falls back to `rosterPlan`, which gives the stage-relevant specialists a concrete job, fills the rest from whoever has waited longest, and always includes the boss's coordination job. A usable model plan is topped up the same way when it leaves people out.
- **Unknown roles always landed on Scout.** `parseTasks` assigned any role it could not match to the first specialist. It now matches role *or* bot name and otherwise picks the specialist with the oldest last task.
- **No visible delegation.** The lead now posts a `Shift plan, N bots working` system message listing every assignee and task, and sends each bot a `DM` with its job before work starts. The owner can see the boss handing out the work.
- **Bots worked in isolation.** Shifts run in three waves: context (plan, research, offer, sample), then selling and building, then proofreading. Bots inside a wave still dispatch in parallel; each wave receives the finished output of the previous one (`completedWork`) inside its prompt, so a bot builds on a teammate's result instead of starting from nothing.

**Fixed — the boss stopped after the first command**
- **A chat order to the boss is now a mission.** `HiveViewModel.messageAgent` sent an order to the boss through `converse`, which could only answer and call a few tools itself. Messages to the primary bot now go through `TeamEngine.takeOrder`: it creates a mission from the order, plans it, dispatches the whole team in waves, and replies with the real per-bot result list. Talking to a specialist is still a direct conversation.

**Added**
- **Google search, optionally.** `search_web` tries the Google Custom Search JSON API first when a Google API key and search engine id (`cx`) are in the vault, then DuckDuckGo, then Bing RSS. Google has no free scraping endpoint, which is why this is a key pair and not a default.
- `VaultKeys.googleSearchKey`, `VaultKeys.googleSearchCx`, `VaultKeys.hasGoogle`.
- **The state file cannot grow without limit.** A busy shift writes around twenty messages and the whole state is rewritten on every change, so an uncapped file would eventually make every write slow. `HiveStore.update` now keeps the last 600 messages, 400 tasks, 800 memory entries and 200 inbox items. Pipeline, products, deliverables and paid work are never trimmed.
- Version 2.13.3 / versionCode 23.

**Verified**
- `gradle clean :app:assembleDebug --no-build-cache` succeeds from a fresh clone of `main`: debug APK **28,236,953 bytes**, SHA-256 `b458b76f8d627b3f1bd1b3110039fc1cb2b97201b5062ac83fe0ce11`. An incremental local build produced a different size, so the number above is the clean-clone build.
- APK metadata: `com.hive.agents`, versionCode 23, versionName 2.13.3, minSdk 28, targetSdk 35.
- Not verified on a device or emulator, and no NVIDIA API key was available: the new planning fallback, waves, handoff prompts, `takeOrder` path and Google branch are compiled, not exercised against the live model.

## v2.13.2+22 — 2026-11-23

Fixes the empty reasoning responses that blocked Scout, the tool loop that left Chief saying "Stopped after six tool rounds," and the search call that returned "No results" when DuckDuckGo challenged the request.

**Fixed**
- **Empty responses from reasoning models**: hidden reasoning counts against `max_tokens`. The planner had 500 tokens and task calls 1,200, so a model could spend the whole completion thinking and return HTTP 200 with no content and no tool call. The planner now gets 3,000; tool calls default to 8,192. An empty reply retries once at twice the budget, then once on another live model. The error now carries `finish_reason` so a future failure is diagnosable.
- **Tool results were sent as user messages**: the assistant's `tool_calls` were replaced with "Calling tools." and every result came back as a user message. `NimClient` now echoes the real assistant `tool_calls` and sends each result as `role: "tool"` with its `tool_call_id`. Models can see which result belongs to which call and stop repeating calls.
- **"Stopped after six tool rounds"**: the conversation loop now has eight rounds and, if the budget is exhausted, makes one no-tools wrap-up call asking for a short report. `dispatch` does the same. A dead-end round count is no longer the bot's answer.
- **Silent planning failure**: when the planning call fails, the lead posts a system message with the reason and says it is using one fallback task, instead of silently assigning a generic task to the first specialist.
- **Search returned "No results" on a DuckDuckGo challenge**: DDG's HTML endpoint can answer HTTP 202 with no result markup. `search_web` now detects that and falls back to Bing RSS, returning titles, links and snippets. Requests send a browser User-Agent and Accept-Language. The tool description asks for a city or region when looking for local businesses.

**Changed**
- `NimClient` sends `chat_template_kwargs: {"force_nonempty_content": true}` to Nemotron models when tools are offered. NVIDIA recommends this so a reasoning model cannot return an empty assistant message during tool calling. It is not sent to non-Nemotron models.
- Tool definitions moved to `agent/ToolSpecs.kt`, web search to `agent/ToolWeb.kt`, and approval copy to `agent/ToolApproval.kt`. Behavior is unchanged; the registry is small enough to review.
- Version 2.13.2 / versionCode 22.

**Verified**
- `gradle clean :app:assembleDebug` succeeds from a fresh public clone at commit `4ed3c3a`: debug APK **28,204,185 bytes**, SHA-256 `6255f1ed5bdf1ef510304f323419afe15ec77e87926c90b01a3bbfa412f91`.
- APK metadata: `com.hive.agents`, versionCode 22, versionName 2.13.2, minSdk 28, targetSdk 35.
- Downloaded again from Catbox after upload: hash matched, zip structure clean.
- DuckDuckGo returned HTTP 202 with no results from the build VM; Bing RSS returned HTTP 200 with 10 items. The fallback parser is compiled, not run inside the app.
- Not verified on a device or emulator, and no NVIDIA API key was available. The empty-response retry, tool-call protocol, wrap-up path and search parser have **not** been exercised against the live model.

## v2.13.1+21 — 2026-11-23

Starts the company on the first screen instead of waiting for a lifecycle event that may already have passed.

**Fixed**
- **Cold-launch autopilot**: Compose can first compose after the activity is RESUMED, so the `ON_RESUME` observer added in `DisposableEffect` never received an initial event. `HiveApp` now also calls `onAppForeground()` from `LaunchedEffect(Unit)`; the loop guard makes the resume path idempotent. The company starts working as soon as the app opens.
- **Duplicate crew onboarding**: `TeamEngine.ensureCoreTeam()` is now guarded by a mutex and re-checks the roster under the lock, so the launch fallback and a failed shift cannot add the same six bots twice.

**Verified**
- `gradle clean :app:assembleDebug` succeeds: debug APK **28,187,801 bytes**, SHA-256 `a993587733c08881343812405968679e687f1b64057ab135874ad8be758b1c33`.
- APK metadata: versionCode 21, versionName 2.13.1, minSdk 28, targetSdk 35.
- Downloaded again from Catbox after upload: hash matched, zip structure clean.
- Not verified on a device or emulator: **no screen has been run**. The autostart path is compiled, not exercised.

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
- `gradle clean :app:assembleDebug` succeeds from a source-only copy of this tree: debug APK **28,187,801 bytes**, SHA-256 `ba4618f3210317194aa2b8f6156489d7ecf68076ff1180622f4db06b1f94ad6`.
- APK metadata: versionCode 20, versionName 2.13.0, minSdk 28, targetSdk 35.
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
- `Prospect.paid`/`delivered` and the `deliver_work` tool. `record_sale` records payment; `deliver_work` saves the finished work and closes the job. Manual advance to WON no longer marks paid; only `record_sale` does.
- Live pipeline snapshot in every system prompt: lead company, stage, email, paid/delivered, value, samples and sales totals.
- `AGENT_README.md`: the living handoff a coding agent must read and update each version.

**Changed**
- `TeamEngine.runMission` now dispatches all planned tasks at the same time with `coroutineScope`/`async`; the old sequential for-loop is gone. Approval dialogs are serialized with a `Mutex`; the calls after approval still run in parallel.
- The team is told to hand work to another bot with `message_teammate`, and to deliver paid work before hunting new leads.
- The Showcase bot is part of the hiring instructions and the shared business facts.
- Setup copy now describes the loop and the per-role model switch.

**Verified**
- `gradle clean :app:assembleDebug` succeeds from a fresh public clone at commit `34c34711`: debug APK **28,089,497 bytes**, SHA-256 `5f9b9bde70c2dd5279acb2f414484f20fc9e18716ccd7e8c5f31b4145920`. The APK was downloaded again after upload and the hash matched.
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
- `gradle clean :app:assembleDebug` succeeds from a source-only copy of this tree (no copied build outputs): debug APK **28,007,577 bytes**, SHA-256 `c360d6064e7e7ce41818122ea5ccb938d2f93d726f5a53201b2e7f93dee8d43f`.
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
