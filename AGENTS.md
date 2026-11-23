# AGENTS.md — rules for any coding agent working on Hive AI

You are working on an Android app that autonomous agents use to run a real business. Mistakes cost money, send real email, and can move real payments. Read this before changing anything.

## Ground truth

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
- The relevant checklist line is updated in the same commit.
- Commit message: `feat|fix|chore(scope): summary`, body explains why.

## Do not

- Do not change the package name, signing config, or minSdk to make something easier.
- Do not disable approval checks or shorten them to get a test to pass.
- Do not commit the APK, keystore, or vault contents.
- Do not promise features that are not in the checklist.
