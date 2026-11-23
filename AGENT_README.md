# Agent handoff — read this first

**Last updated:** v2.12.0+19, 2026-11-23
**Build:** debug APK 28,089,497 bytes, SHA-256 `049b3cda148264ed1585fee193704421e3a787238da19752f92ac95e4e469db6`
**Runtime status:** compiles cleanly; **nothing has been run on a device or emulator**. No screen or tool has been exercised.

This file is the short version for whichever coding agent picks the project up next. `AGENTS.md` has the hard rules, `CHANGELOG.md` has history, `docs/product-spec.md` has behaviour that must not drift. Update this file on **every** version (see the last section).

## What changed in 2.12.0

- **Hive deck**: `ui/screens/HiveVizScreen.kt` is now a pseudo-3D command deck. Tilted orbit, depth-scaled nodes, glowing message packets travelling between sender and recipient, live/idle badge, message flow, and a per-bot work distribution list with each bot's model. Tap a bot card to trace only its traffic.
- **Parallel crews**: `TeamEngine.runMission` dispatches every planned task at once with `coroutineScope { async(Dispatchers.IO) }` instead of a `for` loop. Approval dialogs are serialized by a `Mutex` in `ToolRegistry`; the API calls after approval still run in parallel.
- **Model per role**: `agent/ModelRouter.kt`. Lead/Founder and Product/Studio use `Models.SUPER`; Research, Sales, Marketing and Showcase use `Models.LIGHTNING`; Proofreader uses `Models.DEEPSEEK`. A bot can override with `Agent.model`, and Setup has a `routeModels` switch.
- **Continuous company loop**: `HiveViewModel.startLoop()` runs a shift, waits 45 seconds, runs the next one while autopilot is on and the process is alive. `nextCompanyMission()` picks the next funnel stage: paid delivery → missing contact research → create an offer → make samples → outreach → follow-up → new leads. All-failed shifts back off for five minutes.
- **Paid → delivered**: `Prospect.paid` and `Prospect.delivered`. `record_sale` means payment received and sets `paid = true`; `deliver_work` saves the finished work and sets `delivered = true`. The loop puts any paid, undelivered customer first. Manual advance to WON also sets `paid`.
- **Samples**: `Deliverable.isSample`, the `create_sample` tool, a Showcase role, and a SAMPLES section in Business. Samples pass the proofreader.
- **Pipeline awareness**: every system prompt now includes the live lead list with stage, email, paid/delivered and value, plus ready samples and sales totals.
- **New facts and prompts**: continuous research → qualify → contact → close → deliver; hire a showcase bot; hand off to another bot with `message_teammate`.

## Build and verify

```bash
export JAVA_HOME=/path/to/jdk17
echo "sdk.dir=/path/to/android-sdk" > local.properties
gradle clean :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk`. The APK is debug-signed, so it cannot install over the old 2.9.0/2.10.0 builds; the original release keystore is still missing. This is a known blocker, not a regression.

## Architecture map

| Area | File |
|---|---|
| State and models | `data/Models.kt`, `data/HiveStore.kt` |
| Encrypted keys and backup | `data/KeyVault.kt` |
| Agent loop, prompts, hiring, mission dispatch | `agent/TeamEngine.kt` |
| Model routing | `agent/ModelRouter.kt` |
| Tools and approval gates | `agent/ToolRegistry.kt`, `agent/ApprovalBroker.kt` |
| Proofreading gate | `agent/Proofreader.kt` |
| Continuous loop and pipeline stage choice | `ui/HiveViewModel.kt` |
| Navigation, tabs, lifecycle | `ui/HiveApp.kt` |
| Hive deck | `ui/screens/HiveVizScreen.kt` |
| Business/samples/pipeline | `ui/screens/SalesScreen.kt` |
| Setup, model switch, keys, EIN | `ui/screens/SettingsScreen.kt` |
| Background shift | `service/AutopilotWorker.kt` |

## Behaviour that must not regress

1. Payment links always require human approval. Never pre-generate one.
2. Every outbound email, product description and sample/deliverable passes `Proofreader`; never weaken it to make a test pass.
3. Parallel task dispatch stays parallel. Do not turn `runMission` back into a sequential loop to simplify debugging.
4. Paid, undelivered customers come before new lead hunting.
5. The Business tab stays read-only; bots add products and leads.
6. Keys, EIN and state keep their `.bak` copies.
7. `hire_agent` stays available to every bot; the company still starts with one lead.

## Known gaps / next work

- No runtime verification. The loop, parallel calls, deck, samples and delivery are compiled only.
- The continuous loop spends NVIDIA credits while the app process lives. There is no per-day cap yet.
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
6. Build clean, commit, tag `vX.Y.Z+code`, and publish release notes with the APK link and hash.
