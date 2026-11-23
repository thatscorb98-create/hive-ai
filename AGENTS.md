# AGENTS.md — rules for any coding agent working on Hive AI

You are working on an Android app that autonomous agents use to run a real business. Mistakes cost money, send real email, and can move real payments. Read this before changing anything.

## Ground truth

- **`AGENT_README.md` is the handoff for the coding agent.** Read it first, then update it on every version with what changed and the new build hash.
- `docs/feature-checklist.md` is the product backlog. Update the status column when you ship something; do not mark a feature done without a build that exercises it.
- `docs/product-spec.md` holds the behaviour that must not drift.
- `docs/apk-2.9.0-analysis.md` describes the last shipped build.

## Hard rules

1. **No secrets in the repo.** No API keys, OAuth tokens, keystores, `.env`, `google-services.json`, or customer data. Keys are entered by the user into the app vault at runtime.
2. **Stripe only on demand.** Never generate a payment link ahead of a ready buyer, never batch them, never store a link as reusable for a different job. Every link creation always goes through the human approval flow.
3. **Payment and publishing gates stay on.** Payment links, publishing and deletions need a human yes. Email approval is the owner's explicit switch (`requireApprovalForEmail`); when it is off, autonomous outreach is allowed, and every send must pass `Proofreader` first. Never weaken the proofreader to make a test pass.
4. **Agent email is real email.** No invented contacts or addresses, never mail an address that was not collected for that purpose, always honour opt-out. Proofread before sending.
5. **Signing key is fixed.** Builds must be signed with the project release keystore so they install as an update. Never change `applicationId` (`com.hive.agents`), the signing config, or the version scheme without an explicit decision recorded in the tag message.
6. **Models**: NVIDIA hosted API (`https://integrate.api.nvidia.com/v1`). Default to the free-tier models already referenced in the app; do not hardcode a paid fallback.

## Engineering conventions

- Kotlin 2.0.x, Compose Material 3, `minSdk 28`, `targetSdk 35`, JVM target 17.
- Compose for all UI. New screens go under `ui/screens/` and get added to the nav graph.
- State lives in `HiveViewModel` over the single `HiveStore` JSON state file; do not add a second source of truth.
- Long work runs in WorkManager workers or the foreground `AgentRunService`, never on the main thread.
- Every tool an agent can call is registered in `ToolRegistry` with an approval class (auto / ask-first / always-ask).
- New network calls go through `net/`, OkHttp only, no new HTTP client.
- Strings in `strings.xml`, no hardcoded user-facing text.

## Definition of done

- `./gradlew clean assembleDebug` passes from a source-only copy.
- App installs over the previous build and the stored keys still work.
- `AGENT_README.md`, `CHANGELOG.md` and the README status table are updated in the same version.
- Commit message: `feat|fix|chore(scope): summary`, body explains why.

## This version's invariants (2.13.4)

- `TeamEngine.runMission` dispatches tasks **in parallel** (`coroutineScope` + `async`) inside each wave. Do not turn it back into a sequential loop; the owner asked for simultaneous API work.
- **A shift never collapses into one task for one bot.** `planTasks` falls back to `rosterPlan` (stage-relevant specialists + least-recently-used fill + the boss's coordination job) and `mergePlan` tops up partial model plans. `MAX_TASKS = 5` per shift. An unmatched JSON role must never be dumped on the first specialist.
- **The boss delegates visibly.** `announcePlan` posts the shift plan and DMs every assigned bot before work starts. A message from the owner to the primary bot goes through `takeOrder` and becomes a full team mission, not a one-bot reply.
- Waves are 0 = plan/research/offer/sample, 1 = sales/product, 2 = proofreading; each wave receives earlier output via `completedWork()` so bots hand off instead of working blind.
- `search_web` order is Google Custom Search (only when `googleSearchKey` + `googleSearchCx` are in the vault), then DuckDuckGo, then Bing RSS. Never hardcode a Google key.
- `ModelRouter` routes lead/product to Super, research/sales/marketing/showcase to Lightning, and the proofreader to DeepSeek. Keep a manual override and the Setup switch.
- **Model ids must be resolved against `/v1/models`.** `deepseek-ai/deepseek-v4.1-flash` is retired; the current id is `deepseek-ai/deepseek-v4-flash`. `NimClient` must throw on HTTP/parse/empty failures, never return an error string as bot content, and must keep the 404/410 fallback.
- The company loop belongs to `HiveViewModel.startLoop()`. It must stop when Autopilot is off and back off after a wholly failed shift. Paid, undelivered work is always the first mission.
- The company starts with one lead, but `TeamEngine.ensureCoreTeam()` is the local safety net when the lead does not hire. Do not remove it; a broken model must not leave the app with one bot forever.
- The bottom bar is exactly **Home, Chat, Browsers, Email, Settings**. Business and Studio are not tabs; products and services are made on demand.
- `HiveApp` starts autopilot from `LaunchedEffect(Unit)` as well as the lifecycle `ON_RESUME` observer. Keep both: the first composition can happen after the activity is already RESUMED.
- Chat is a company chat: left rail of bot blobs, Company plus per-bot threads, and visible `DM`/`HANDOFF` messages. Do not collapse it back into a single card feed.
- `deliver_work` is the only path that closes a delivered job; `record_sale` means payment received.
- **Tool calls stay real tool calls.** Echo the assistant's `tool_calls` and answer each one with `role: "tool"` plus its `tool_call_id`. Never replace the call with "Calling tools." or send the result as a user message; the model loses track of which call finished and repeats it.
- **Reasoning models need room.** Keep the planner at 3,000 tokens and tool calls at 8,192 or more. An empty 200 response must retry at twice the budget, then on another live model; it must never become bot content.
- **A search challenge is not "no results."** `search_web` tries DuckDuckGo, then Bing RSS, and returns titles, links and snippets. Keep the city/region hint for local lead research.
- **`HiveStore.update` caps history and writes off Main.** 600 messages, 400 tasks, 800 memory entries, 200 inbox items, 200 missions (active ones kept), 300 sent mails, 100 suggestions. Pipeline, products, deliverables and paid work are never trimmed. Writes go through a single background writer; `flush()` runs on app stop.
- **`record_sale` asks first and is the only path that marks `paid`.** `deliver_work` refuses unpaid jobs. Advancing a prospect to WON never marks paid; revenue counts paid WON deals only.
- **Email approval defaults on.** `send_email` is capped at 5 per agent turn, with an owner blocklist. Only the lead gets the full toolset (`ToolPolicy`); other roles get the minimum for their job.
- **Web and page content is untrusted.** `ToolRegistry` wraps it with a per-call nonce marker; `remember`/`hire_agent` right after untrusted reads require human approval. `fetch_page`/`open_website` refuse private, loopback, link-local and non-http(s) destinations.
- **One shift at a time.** `ShiftCoordinator` serializes the loop, manual orders and the WorkManager worker. Stopping a shift marks the mission resumable and drops its queued tasks; cancellation is never swallowed by `runCatching`.

## Do not

- Do not change the package name, signing config, or minSdk to make something easier.
- Do not disable approval checks or shorten them to get a test to pass.
- Do not commit the APK, keystore, or vault contents.
- Do not promise features that are not in the checklist.
