# Agent handoff — read this first

**Last updated:** v2.13.2+22, 2026-11-23
**Build:** debug APK 28,204,185 bytes, SHA-256 `6255f1ed5bdf1ef510304f323419afe15ec77e87926c90b01a3bbfa04b912f91`
**Runtime status:** compiles cleanly; **nothing has been run on a device or emulator**. No screen or tool has been exercised.

This file is the short version for whichever coding agent picks the project up next. `AGENTS.md` has the hard rules, `CHANGELOG.md` has history, `docs/product-spec.md` has behaviour that must not drift. Update this file on **every** version (see the last section).

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

Expected: `BUILD SUCCESSFUL`, `app/build/outputs/apk/debug/app-debug.apk`, versionCode 22 / versionName 2.13.2. A source-only copy of this tree (no build outputs) produced the APK above. The build is debug-signed, so it cannot install over the old 2.9.0/2.10.0 builds; the original release keystore is still missing. This is a known blocker, not a regression.

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
2. **Parallel task dispatch stays parallel.** `TeamEngine.runMission` uses `coroutineScope` + `async(Dispatchers.IO)`. Do not turn it back into a sequential loop.
3. **Paid, undelivered customers come first.** `record_sale` means payment received; `deliver_work` is the only path that closes a delivered job.
4. **The bottom bar is exactly Home, Chat, Browsers, Email, Settings.** Business and Studio are not tabs. Products and services are made by the crew on demand; Home shows the work.
5. **The company starts with one lead, but must never stay stuck at one bot.** The lead hires through `hire_agent`; `ensureCoreTeam()` is the local safety net when the model fails or refuses to hire.
6. **Every outbound email, product description, sample and COPY deliverable passes `Proofreader`.** Never weaken it to make a build pass.
7. **Payment links always require human approval.** Email approval is the owner's switch; the proofreader still runs when it is off.
8. **Keys, EIN and state keep their `.bak` copies.** No secret ever goes into the repo or an APK.
9. **The station/chat must show real state.** No invented bots, messages, tasks or sales. The UI is a projection of `HiveState`; `NimClient` errors are shown as errors.
10. **Tool results stay real tool results.** Echo the assistant's `tool_calls` and answer each with `role: "tool"` plus its `tool_call_id`. Never replace a call with "Calling tools." or send the result as a user message; the model loses the connection and repeats the call.
11. **Reasoning models need room for reasoning.** Keep the planner at 3,000 tokens and tool calls at 8,192 or more. An empty response must retry with a larger budget and then another live model, never become a bot reply.
12. **Search must not treat a challenge page as empty results.** DuckDuckGo can answer 202 with no result markup; fall through to Bing RSS and return titles, links and snippets.

## Known gaps / next work

- No runtime verification. The station, chat rail, code blocks, crew fallback, model fallback and continuous loop are compiled only.
- No NVIDIA key was available while building 2.13.2: the empty-reasoning retry, real tool-call protocol and wrap-up call have not been sent to the live API.
- Search now relies on public DuckDuckGo and Bing pages. Either can rate-limit or change its markup; a paid search API is the durable fix if lead research becomes critical.
- The continuous loop spends NVIDIA credits while the app process lives. There is no per-day cap yet.
- Background WorkManager shifts still start from the standing goal; the foreground loop uses `nextCompanyMission()`. Unify them if the owner wants the exact funnel order in the background too.
- Parallel tasks assigned to the same bot can interleave their messages; the lead should assign distinct roles.
- One browser window is live at a time and Android shares one cookie jar across bots.
- Stripe payment is recorded by `record_sale`/manual advance; there is no webhook listener.
- Release signing/keystore recovery is still the top blocker for in-place updates.

## Every update must

1. Bump `versionCode`/`versionName` in `app/build.gradle.kts`.
2. Rewrite **What changed in this version** above and refresh the build hash.
3. Add a `CHANGELOG.md` section.
4. Update the README status table and any feature that changed.
5. Update the relevant `docs/feature-checklist.md` rows.
6. Build clean from a source-only copy, commit, tag `vX.Y.Z+code`, and publish release notes with the APK link and hash.
