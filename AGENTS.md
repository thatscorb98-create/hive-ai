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

## This version's invariants (2.13.0)

- `TeamEngine.runMission` dispatches tasks **in parallel** (`coroutineScope` + `async`). Do not turn it back into a sequential loop; the owner asked for simultaneous API work.
- `ModelRouter` routes lead/product to Super, research/sales/marketing/showcase to Lightning, and the proofreader to DeepSeek. Keep a manual override and the Setup switch.
- **Model ids must be resolved against `/v1/models`.** `deepseek-ai/deepseek-v4.1-flash` is retired; the current id is `deepseek-ai/deepseek-v4-flash`. `NimClient` must throw on HTTP/parse/empty failures, never return an error string as bot content, and must keep the 404/410 fallback.
- The company loop belongs to `HiveViewModel.startLoop()`. It must stop when Autopilot is off and back off after a wholly failed shift. Paid, undelivered work is always the first mission.
- The company starts with one lead, but `TeamEngine.ensureCoreTeam()` is the local safety net when the lead does not hire. Do not remove it; a broken model must not leave the app with one bot forever.
- The bottom bar is exactly **Home, Chat, Browsers, Email, Settings**. Business and Studio are not tabs; products and services are made on demand.
- Chat is a company chat: left rail of bot blobs, Company plus per-bot threads, and visible `DM`/`HANDOFF` messages. Do not collapse it back into a single card feed.
- `deliver_work` is the only path that closes a delivered job; `record_sale` means payment received.

## Do not

- Do not change the package name, signing config, or minSdk to make something easier.
- Do not disable approval checks or shorten them to get a test to pass.
- Do not commit the APK, keystore, or vault contents.
- Do not promise features that are not in the checklist.
