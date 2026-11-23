# Grok Bot feature parity checklist

Research target: **Grok Bot** (xAI, out of beta Nov 2026) — `x.ai/bot`, docs at `docs.x.ai/grok-bot`.

> **Status meaning (updated for v2.10.0):** ✅ = implemented in `app/` and builds,
> 🟡 = partially implemented, ⬜ = not started. Earlier versions of this file described the
> shipped APK; the statuses below describe the code in this repository.

## 1. Bots and roster

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 1.1 | Create a bot with name, avatar shape/colour | `Agent` + name/role/charter | ✅ |
| 1.2 | Each bot has a durable job/role | `Agent.role` + `charter` | ✅ |
| 1.3 | Primary bot reports on the whole team | `HiveState.orchestrator` | ✅ |
| 1.4 | Orchestrator creates bots with a role, and they persist | `TeamEngine.ensureTeam` + `hire` | ✅ |
| 1.5 | Pin, sections, hide/unhide | — | ⬜ |
| 1.6 | Duplicate a bot without memory | — | ⬜ |
| 1.7 | Delete a bot and its memory | manual forget only | 🟡 |
| 1.8 | Share/import bot templates | — | ⬜ |

## 2. Memory

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 2.1 | Per-bot memory: preferences, rules, facts, work log | `MemoryService`, `MemoryKind` | ✅ |
| 2.2 | Saves unprompted and says so | agent can call `remember` | 🟡 |
| 2.3 | Facts shared by all bots | `agentId = "*"` | ✅ |
| 2.4 | What do you remember / correct / forget | forget in Setup | 🟡 |
| 2.5 | **Mistake ledger** with retrieval before retry | `MemoryService.lesson` + `guardrails` | ✅ |
| 2.6 | Import memory from another assistant | — | ⬜ |
| 2.7 | Survives chat reset, dies with the bot | stored separately from messages | ✅ |

## 3. Computer / browser

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 3.1 | Persistent computer: browser + terminal + files | `AgentBrowserActivity` gives each bot a real WebView window | 🟡 |
| 3.2 | Each bot its own screen, parallel work | one window per bot, opened from Browsers | ✅ |
| 3.3 | Sessions and logins persist | cookies persist app-wide; window history is per bot | 🟡 |
| 3.4 | Hand over for SSO/passkey/CAPTCHA/2FA | the human takes the same window, then the bot continues | ✅ |
| 3.5 | Read one-time codes from the bot's inbox | inbox exists, no agent wiring | 🟡 |
| 3.6 | Local execution policy | tools are fixed and risk-classed | 🟡 |

## 4. Connectors

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 4.1 | App catalog | NVIDIA, AgentMail, Stripe configured directly | 🟡 |
| 4.2 | Custom MCP servers | — | ⬜ |
| 4.3 | Tokens server-side, never to the model | vault on device, tools never hand tokens to the model | ✅ |
| 4.4 | Account-wide connectors | vault is account-wide | ✅ |
| 4.5 | Search/read X | DuckDuckGo HTML only | 🟡 |

## 5. Approvals

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 5.1 | Every action reviewed | `ToolRegistry` risk classes | ✅ |
| 5.2 | Card shows target, scope, values, warnings | `ApprovalDialog` + irreversible flag | ✅ |
| 5.3 | Allow once / always / deny | allow once + deny | 🟡 |
| 5.4 | Ask-first + allow rules | — | ⬜ |
| 5.5 | Background approvals expire ~30 min | `ApprovalBroker` timeout = refusal | ✅ |
| 5.6 | Built-in checks rules cannot override | fixed risk classes | 🟡 |
| 5.7 | Action log | mission timeline | 🟡 |

## 6. Vault

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 6.1 | Logins/cards fill without model seeing | vault holds keys, not browser logins | 🟡 |
| 6.2 | Secure form, masked, out of transcript | masked fields in Setup | ✅ |
| 6.3 | Per-use card approval | Stripe is approve-per-link | ✅ |
| 6.4 | Hardware security keys | — | ⬜ |

## 7. Chat and collaboration

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 7.1 | Chat is the interface | `ChatScreen` | ✅ |
| 7.2 | Group chat with several bots | one shared thread, all bots post | ✅ |
| 7.3 | Bots DM each other, hand off work | `MessageKind.DM` / `HANDOFF`, `message_teammate` tool | ✅ |
| 7.4 | Voice in / voice out | — | ⬜ |
| 7.5 | Sync across devices | local only | ⬜ |
| 7.6 | **3D team view** (Hive-specific) | `HiveVizScreen` animated graph | ✅ |

## 8. Skills and routines

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 8.1 | Learn by demonstration | — | ⬜ |
| 8.2 | Prompt skills, tested before scheduling | missions act as one-shot runs | 🟡 |
| 8.3 | Scheduled / event routines | `AutopilotWorker` every 15 min from the standing goal | ✅ |
| 8.4 | Pause a routine | autopilot switch in Setup | ✅ |

## 9. Deliverables

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 9.1 | Shared file workspace | deliverables and products in state | 🟡 |
| 9.2 | Deliverables attached to chat | shown in mission + studio | ✅ |
| 9.3 | Build sites, graphics, copy, digital goods | `create_product` + model-driven deliverables | 🟡 |

## 10. Business operations

| # | Requirement | Hive AI | Status |
|---|---|---|---|
| 10.1 | Per-agent email address for leads | `AgentMailApi` | ✅ |
| 10.2 | In-app per-agent inbox + sent viewer | `InboxScreen` | ✅ |
| 10.3 | Stripe link only when a buyer is ready | approval-gated, on demand | ✅ |
| 10.4 | Pipeline lead → qualify → pitch → close | `SalesScreen` + `Prospect` | ✅ |
| 10.5 | Business identity (Local Reach Workshop, EIN) on documents | settings fields, not yet on PDFs | 🟡 |
| 10.6 | Suggestion box that outputs a coding-agent prompt | `SuggestionBox` + copy button | ✅ |
| 10.7 | Products with a checkout per product | `create_product`, `create_checkout`, Business screen | ✅ |
| 10.8 | Bots know who they work for | system prompt + seeded shared memory | ✅ |

## 11. Platform and model

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 11.1 | Phone + desktop | Android 9+ | ✅ |
| 11.2 | Automatic model choice | explicit NVIDIA picker | ✅ |
| 11.3 | 38 languages | English | ⬜ |
| 11.4 | Usage metering | — | ⬜ |

## 12. Keys survive updates

| # | Requirement | Status |
|---|---|---|
| 12.1 | In-place update keeps keys | blocked: original keystore missing |
| 12.2 | No keys in the binary | ✅ |
| 12.3 | Vault export/backup | ⬜ |

## Build order

1. Keystore recovery — blocks every future in-place update.
2. Vault export/import (12.3).
3. Per-bot cookie isolation (3.3) — currently one cookie jar for the app.
4. MCP connectors (4.2) and voice (7.4).
5. Runtime verification on a device or emulator; nothing has been run yet.
