# Changelog

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
