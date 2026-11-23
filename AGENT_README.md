# Agent handoff — read this first

**Last updated:** v2.13.0+20, 2026-11-23
**Build:** debug APK 28,187,801 bytes, SHA-256 `ba4618f3210317194aa3f21d6156489d7ecf68076ff1180622f4db06b1f94ad6`
**Runtime status:** compiles cleanly; **nothing has been run on a device or emulator**. No screen or tool has been exercised.

This file is the short version for whichever coding agent picks the project up next. `AGENTS.md` has the hard rules, `CHANGELOG.md` has history, `docs/product-spec.md` has behaviour that must not drift. Update this file on **every** version (see the last section).

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

Expected: `BUILD SUCCESSFUL`, `app/build/outputs/apk/debug/app-debug.apk`, versionCode 20 / versionName 2.13.0. A source-only copy of this tree (no build outputs) produced the APK above. The build is debug-signed, so it cannot install over the old 2.9.0/2.10.0 builds; the original release keystore is still missing. This is a known blocker, not a regression.

## Architecture map

| Area | File |
|---|---|
| State and models, model-id migration | `data/Models.kt`, `data/HiveStore.kt` |
| Encrypted keys and backup | `data/KeyVault.kt` |
| NVIDIA client, live catalog and fallback | `net/NimClient.kt` |
| Agent loop, prompts, hiring, mission dispatch, crew fallback | `agent/TeamEngine.kt` |
| Model routing | `agent/ModelRouter.kt` |
| Tools and approval gates | `agent/ToolRegistry.kt`, `agent/ApprovalBroker.kt` |
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

## Known gaps / next work

- No runtime verification. The station, chat rail, code blocks, crew fallback, model fallback and continuous loop are compiled only.
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
