# Changelog

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
