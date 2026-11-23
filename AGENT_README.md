# Agent handoff — read this first

**Last updated:** v2.13.4+24, 2026-11-23
**Build:** debug APK 28,269,721 bytes, SHA-256 `62d36089f5d4cba567c3f66594f12da853e03245475fe8a2207119e35e6fdf11` (clean build, `--no-build-cache`)
**Runtime status:** compiles cleanly; **nothing has been run on a device or emulator**. No screen or tool has been exercised.

This file is the short version for whichever coding agent picks the project up next. `AGENTS.md` has the hard rules, `CHANGELOG.md` has history, `docs/product-spec.md` has behaviour that must not drift. Update this file on **every** version (see the last section).

## What changed in 2.13.4

- **Prompt-injection containment.** Web, search and page content is wrapped as untrusted with a per-call nonce; the system prompt forbids following embedded instructions. `remember`/`hire_agent` right after untrusted reads now ask first. Only the lead gets the full toolset (`ToolPolicy`); other roles get the minimum for their job. Email approval defaults on, sends are capped at 5 per agent turn, and the owner keeps a do-not-email list. `fetch_page`/`open_website` refuse private, loopback, link-local and non-http(s) destinations.
- **Sales are truthful.** `record_sale` asks first and is the only path that marks `paid`; revenue and the sales count cover paid WON deals only. `deliver_work` refuses unpaid jobs. Advancing a prospect to WON no longer implies payment.
- **One shift at a time, and stopping is safe.** `ShiftCoordinator` serializes the loop, manual orders and the WorkManager worker (the worker skips instead of queueing). Cancellation is no longer swallowed: a stopped mission becomes resumable and its queued tasks are dropped. Turning Autopilot off also cancels the scheduled worker.
- **State writes leave the Main thread.** `HiveStore` writes through a single background writer with a write-after-read guard, a surfaced error flow, and a flush on app stop. Caps now also cover missions (200, active preserved), sent mail (300) and suggestions (100). Vault read/write failures surface instead of silently returning empty keys.
- **Fixed while testing.** Writing the AgentMail tests exposed a dead fallback: `JSONObject(raw)` threw on a top-level array, so the legacy envelope was unreachable and the inbox silently appeared empty. The parser now handles both shapes and HTTP failures throw instead of returning an empty inbox.
- **26 unit tests, still no device run.** `ToolPolicy`, `EmailPolicy`, URL guards, shift serialization, async store behaviour with caps/backup/failure reporting, settings defaults, paid-only accounting and mail parsing are covered. Nothing has been exercised on a device or emulator, and no live API keys were available.
- Version 2.13.4 / code 24.

## What changed in 2.13.3

- **The shift no longer collapses into one task for one bot.** `planTasks` used to return a single `fallbackTask` when the lead's JSON did not parse, and that task always went to the first non-lead bot (Scout). That is the bug behind "only Chief and Scout work." Planning now falls back to `rosterPlan`, which gives the stage-relevant specialists a concrete job, fills remaining slots from whoever has waited longest, and always includes the boss's coordination job. A usable model plan is topped up the same way via `mergePlan`, capped at `MAX_TASKS = 5` per shift.
- **Unknown roles round-robin.** `parseTasks` matched only `role`, then dumped everything on the first specialist. It now matches role or bot name and otherwise picks the specialist with the oldest last task.
- **The handoff is visible.** `announcePlan` posts a `Shift plan, N bots working` system message listing each assignee and task, and DMs each bot its job before work begins.
- **Waves with real handoffs.** `executeMission` groups tasks by wave: 0 = boss plan, research, offer, sample; 1 = sales and product; 2 = proofreading. Tasks inside a wave still run in parallel; each wave receives the finished output of earlier waves through `completedWork()` inside its prompt.
- **An order to the boss is a mission.** `HiveViewModel.messageAgent` now calls `TeamEngine.takeOrder` for the primary bot: it creates a mission from the message, plans and dispatches the team, and replies with the per-bot result list. Specialists still use `converse`.
- **Optional Google search.** `search_web` tries the Google Custom Search JSON API when `googleSearchKey` and `googleSearchCx` are both in the vault, then DuckDuckGo, then Bing RSS. Settings has both fields. Google has no free scraping endpoint, so it cannot be the default.
- **The state file is capped.** A busy shift writes about twenty messages and every change rewrites the whole JSON. `HiveStore.update` keeps the last 600 messages, 400 tasks, 800 memory entries and 200 inbox items; pipeline, products, deliverables and paid work are never trimmed.
- Version 2.13.3 / code 23.

## What changed in 2.13.2

- **Empty reasoning responses are retried, not treated as bot answers.** NVIDIA's reasoning models count hidden reasoning against `max_tokens`; the old planner budget (500) and task budget (1,200) could be exhausted before any answer, producing HTTP 200 with `content: null`. The planner now gets 3,000, tool calls get 8,192, and an empty response retries at twice the budget and then on another live model. The error includes `finish_reason`.
- **Tool calling uses the real protocol.** `NimClient.Msg` now carries assistant `toolCalls` and tool `toolCallId`. `TeamEngine.converse`/`dispatch` echo the assistant's calls and send results as `role: "tool"` messages. The old code sent "Calling tools." as assistant text and every result as a user message; that is why the model repeated calls and eventually gave up.
- **No more dead-end tool budget.** Both loops run eight rounds; if they exhaust it, `wrapUp()` makes one no-tools call asking for a short report. "Stopped after six tool rounds" cannot be the final answer any more.
- **Planning errors are visible.** If planning fails, the lead posts the reason and says it is using a fallback task.
- **Search survives a DuckDuckGo challenge.** DDG can return HTTP 202 with no results. `search_web` falls through to Bing RSS (titles, links, snippets) with browser headers. The tool description asks for a city/region for local leads.
- **Nemotron tool calls force non-empty content.** `chat_template_kwargs: {"force_nonempty_content": true}` is sent to Nemotron models when tools are offered, as NVIDIA recommends. It is not sent to DeepSeek.
- Version 2.13.2 / code 22.

## What changed in 2.13.1

- **Autopilot starts on the first screen.** `HiveApp` now calls `onAppForeground()` from a `LaunchedEffect(Unit)` as well as the lifecycle `ON_RESUME` observer. On a cold launch the activity can already be RESUMED when Compose first composes, so the observer alone never sees its initial event and the company would wait for a manual shift. The company now starts working as soon as the app opens.
- **Crew onboarding is serialized.** `TeamEngine.ensureCoreTeam()` uses a mutex and re-checks the roster inside it, so the launch fallback and a failed shift cannot onboard the same six bots twice.
- Version 2.13.1 / code 21. No other behaviour changed.

## What changed in 2.13.0

- **The 410 is fixed.** 2.12 shipped `deepseek-ai/deepseek-v4.1-flash`, which NVIDIA has retired. `Models.DEEPSEEK` is now `deepseek-ai/deepseek-v4-flash`. `TeamEngine.bootstrap` migrates saved settings and per-bot overrides, so an existing install does not stay pinned to the dead id.
- **No more fake success.** `NimClient` now throws `NimException` on HTTP errors, parse failures and empty replies instead of returning `"model call failed: ..."` as if it were the bot's answer. A task whose model call fails is marked `FAILED`; the shift is `BLOCKED`, not "Mission complete".
- **Live model fallback.** Before the first call, `NimClient` reads `/v1/models`. If the requested id is missing it picks a live model from the same family (then a known-good model). A 404/410 retries once on another live model. The catalog is cached for ten minutes and cleared on a retired-id error.
- **Crew fallback.** If the lead has still not hired anyone after a shift, `TeamEngine.ensureCoreTeam()` locally onboards Scout (research), Closer (sales), Spark (marketing), Maker (product), Demo (showcase) and Proof (proofreading). It also runs on the next app open when `lastShiftAt > 0` and the company is still alone. No API call is needed for this fallback.
- **Exactly five tabs.** `ui/HiveApp.kt` is now Home, Chat, Browsers, Email, Settings. Business, Studio and Agents are no longer bottom tabs. Team management is reachable from Home's crew strip and Chat's `+ crew`. The 3D view is opened from Home/Chat and highlights Home, never Browsers.
- **Chat is a company chat, not a card feed.** `ui/screens/ChatScreen.kt` has a fixed left rail of bot blobs (plus Company), per-bot threads, iMessage-dark bubbles, timestamps, a working bar, and fenced-code blocks with Copy. Handoffs and DMs are real messages visible in both participants' threads.
- **Work is visible as conversation.** `TeamEngine.executeTask` posts the lead's assignment as `HANDOFF`, the assignee's "on it" as `HANDOFF`, and the result back to the lead. `message_teammate` adds `DM`s. The overseer can peer into a working conversation instead of reading a flat log.
- **Station view, not an orbit.** `ui/screens/HiveVizScreen.kt` is a StarNet-inspired cutaway: seven department rooms, isometric lids, hallways as handoff lanes, bots at workstations, glowing message packets, tap-to-isolate and a crew/work list. StarNet's idea is that the station is a projection of live runtime state; the art here is original Hive artwork, not a StarNet asset.
- **Samples survive the Business-tab removal.** Home now has a RECENT WORK strip showing the latest deliverables and whether they are customer samples. Products/services are created by the crew on demand and measured on Home.
- **Housekeeping.** `AgentAvatar.kt` is shared by Home and Chat; the old global header is gone; Settings has no back row; Inbox and Browsers no longer show a back link when they are tabs; the version is 2.13.0 / code 20.

## Build and verify

```bash
export JAVA_HOME=/path/to/jdk17
echo "sdk.dir=/path/to/android-sdk" > local.properties
gradle clean :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`, `app/build/outputs/apk/debug/app-debug.apk`, versionCode 23 / versionName 2.13.3. A source-only copy of this tree (no build outputs) produced the APK above. The build is debug-signed, so it cannot install over the old 2.9.0/2.10.0 builds; the original release keystore is still missing. This is a known blocker, not a regression.

## Architecture map

| Area | File |
|---|---|
| State and models, model-id migration | `data/Models.kt`, `data/HiveStore.kt` |
| Encrypted keys and backup | `data/KeyVault.kt` |
| NVIDIA client, live catalog, empty-reasoning retry, tool-call wire format | `net/NimClient.kt` |
| Agent loop, prompts, hiring, mission dispatch, crew fallback | `agent/TeamEngine.kt` |
| Model routing | `agent/ModelRouter.kt` |
| Tool definitions; registry dispatch and approval gates | `agent/ToolSpecs.kt`, `agent/ToolRegistry.kt`, `agent/ApprovalBroker.kt` |
| Web research with Bing RSS fallback; approval copy | `agent/ToolWeb.kt`, `agent/ToolApproval.kt` |
| Proofreading gate | `agent/Proofreader.kt` |
| Continuous loop, pipeline stage choice, shift mutex | `ui/HiveViewModel.kt` |
| Five-tab navigation, lifecycle, browser handoff | `ui/HiveApp.kt` |
| Shared bot blob | `ui/AgentAvatar.kt` |
| Dashboard | `ui/screens/HomeScreen.kt` |
| Company chat and code rendering | `ui/screens/ChatScreen.kt` |
| Station cutaway, live flow, work distribution | `ui/screens/HiveVizScreen.kt` |
| Email tab | `ui/screens/InboxScreen.kt` |
| Browser tab | `ui/screens/BrowsersScreen.kt` |
| Team management | `ui/screens/TeamScreen.kt` |
| Keys, model switch, EIN, memory | `ui/screens/SettingsScreen.kt` |
| Background shift | `service/AutopilotWorker.kt` |

## Behaviour that must not regress

1. **Model ids are resolved against the live catalog.** Never remove the `/v1/models` check or hardcode a retired id. `deepseek-ai/deepseek-v4.1-flash` is gone; use `deepseek-ai/deepseek-v4-flash`. A failed model call must throw, never become bot content.
2. **Parallel task dispatch stays parallel inside a wave.** `executeMission` runs each wave with `coroutineScope` + `async(Dispatchers.IO)`; waves run in order only so later bots can receive earlier output. Do not turn the inside of a wave into a sequential loop.
3. **A shift never becomes one task for one bot.** Keep `rosterPlan`/`mergePlan` and `MAX_TASKS`. The boss's plan is a bonus, not the only source of work: if the model returns prose, empty content or a partial plan, the standing role plan fills in.
4. **Paid, undelivered customers come first.** `record_sale` means payment received and now asks first; `deliver_work` refuses unpaid jobs and is the only path that closes a delivered job. Advancing a prospect to WON does not mark it paid.
5. **The bottom bar is exactly Home, Chat, Browsers, Email, Settings.** Business and Studio are not tabs. Products and services are made by the crew on demand; Home shows the work.
6. **The company starts with one lead, but must never stay stuck at one bot.** The lead hires through `hire_agent`; `ensureCoreTeam()` is the local safety net when the model fails or refuses to hire.
7. **Every outbound email, product description, sample and COPY deliverable passes `Proofreader`.** Never weaken it to make a build pass.
8. **Payment links always require human approval.** Email approval is on by default and remains the owner's switch; the proofreader still runs when it is off.
9. **Keys, EIN and state keep their `.bak` copies.** No secret ever goes into the repo or an APK.
10. **The station/chat must show real state.** No invented bots, messages, tasks or sales. The UI is a projection of `HiveState`; `NimClient` errors are shown as errors.
11. **Tool results stay real tool results.** Echo the assistant's `tool_calls` and answer each with `role: "tool"` plus its `tool_call_id`. Never replace a call with "Calling tools." or send the result as a user message; the model loses the connection and repeats the call.
12. **Reasoning models need room for reasoning.** Keep the planner at 3,000 tokens and tool calls at 8,192 or more. An empty response must retry with a larger budget and then another live model, never become a bot reply.
13. **Search must not treat a challenge page as empty results.** DuckDuckGo can answer 202 with no result markup; fall through to Bing RSS and return titles, links and snippets.

## Known gaps / next work

- No runtime verification. 26 unit tests cover policy, parsing, store and coordinator logic, but no screen, worker, vault, browser or model call has been exercised on a device or emulator.
- No NVIDIA key was available while building 2.13.4: nothing has been sent to the live API.
- Search now relies on Google Custom Search (optional keys), public DuckDuckGo and Bing pages. Either can rate-limit or change its markup; a paid search API is the durable fix if lead research becomes critical.
- The continuous loop spends NVIDIA credits while the app process lives. There is no per-day cap yet.
- Background WorkManager shifts still start from the standing goal; the foreground loop uses `nextCompanyMission()`. Unify them if the owner wants the exact funnel order in the background too.
- Parallel tasks assigned to the same bot can interleave their messages; the lead should assign distinct roles.
- One browser window is live at a time and Android shares one cookie jar across bots.
- Stripe payment is recorded by `record_sale`; there is no webhook listener.
- Release signing/keystore recovery is still the top blocker for in-place updates.

## Every update must

1. Bump `versionCode`/`versionName` in `app/build.gradle.kts`.
2. Rewrite **What changed in this version** above and refresh the build hash.
3. Add a `CHANGELOG.md` section.
4. Update the README status table and any feature that changed.
5. Update the relevant `docs/feature-checklist.md` rows.
6. Build clean from a source-only copy, commit, tag `vX.Y.Z+code`, and publish release notes with the APK link and hash.
