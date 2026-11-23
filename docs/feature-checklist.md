# Grok Bot feature parity checklist

Research target: **Grok Bot** (xAI, out of beta Nov 2026) — `x.ai/bot`, docs at `docs.x.ai/grok-bot`.
Legend: **✅** verified present in the shipped 2.9.0 APK, **🟡 partial**, **⬜ planned**.

## 1. Bots and roster

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 1.1 | Create a bot with name, avatar shape/colour | Agent model + avatar | ✅ |
| 1.2 | Each bot has a durable job/role | `Agent`, `RoleAssignment` | ✅ |
| 1.3 | Primary bot reports on the whole team | `TeamEngine` | 🟡 |
| 1.4 | Orchestrator creates new bots with a role, and they persist | `SuggestionEngine` only | ⬜ |
| 1.5 | Pin, sections, hide/unhide | — | ⬜ |
| 1.6 | Duplicate a bot without memory | — | ⬜ |
| 1.7 | Delete a bot and its memory | — | ⬜ |
| 1.8 | Share/import bot templates | — | ⬜ |

## 2. Memory

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 2.1 | Per-bot memory: preferences, rules, facts, work log | `MemoryEntry` | ✅ |
| 2.2 | Saves unprompted and says so | — | 🟡 |
| 2.3 | Facts shared by all bots | — | ⬜ |
| 2.4 | What do you remember / correct / forget | — | ⬜ |
| 2.5 | **Mistake ledger** with retrieval before retry | — | ⬜ |
| 2.6 | Import memory from another assistant | — | ⬜ |
| 2.7 | Survives chat reset, dies with the bot | — | 🟡 |

## 3. Computer / browser

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 3.1 | Persistent computer: browser + terminal + files | `AgentBrowser`, `BrowserActivity` | 🟡 |
| 3.2 | Each bot its own screen, parallel work | agent-per-browser model | 🟡 |
| 3.3 | Sessions and logins persist | — | ⬜ |
| 3.4 | Hand over for SSO/passkey/CAPTCHA/2FA/card verify | — | ⬜ |
| 3.5 | Read one-time codes from the bot's inbox | `AgentInbox` | 🟡 |
| 3.6 | Local execution policy: ask/always/never | — | ⬜ |

## 4. Connectors

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 4.1 | App catalog | `ConnectSheet` | 🟡 |
| 4.2 | Custom MCP servers | — | ⬜ |
| 4.3 | Tokens server-side, never to the model | `KeyVault` on-device | 🟡 |
| 4.4 | Account-wide connectors | — | ⬜ |
| 4.5 | Search/read X | — | ⬜ |

## 5. Approvals

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 5.1 | Every action reviewed | `ApprovalBroker` | ✅ |
| 5.2 | Card shows target, scope, values, warnings | `ApprovalRequest` | 🟡 |
| 5.3 | Allow once / always / deny | — | 🟡 |
| 5.4 | Ask-first + allow rules, 20 each | — | ⬜ |
| 5.5 | Background approvals expire ~30 min | — | ⬜ |
| 5.6 | Built-in checks rules cannot override | — | ⬜ |
| 5.7 | Action log | — | ⬜ |

## 6. Vault

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 6.1 | Logins/cards fill without model seeing | `KeyVault` | 🟡 |
| 6.2 | Secure form, masked, out of transcript | `KeySetup` | 🟡 |
| 6.3 | Per-use card approval | — | ⬜ |
| 6.4 | Hardware security keys | — | ⬜ |

## 7. Chat and collaboration

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 7.1 | Chat is the interface | Compose chat | ✅ |
| 7.2 | Group chat with several bots | — | ⬜ |
| 7.3 | Bots DM each other, hand off work | `TeamEngine`, `Message` | 🟡 |
| 7.4 | Voice in / voice out | `VoiceKt` | 🟡 |
| 7.5 | Sync across devices | local only | ⬜ |
| 7.6 | **3D team view** (Hive-specific) | `HiveVizScreen` | 🟡 |

## 8. Skills and routines

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 8.1 | Learn by demonstration | — | ⬜ |
| 8.2 | Prompt skills, tested before scheduling | `Plan` | 🟡 |
| 8.3 | Scheduled / event routines | `Automation` + workers | ✅ |
| 8.4 | Pause a routine | — | 🟡 |

## 9. Deliverables

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 9.1 | Shared file workspace | `StudioFiles` | ✅ |
| 9.2 | Deliverables attached to chat | `Deliverable` | ✅ |
| 9.3 | Build sites, graphics, copy, digital goods | `ImageGen`, Studio | 🟡 |

## 10. Business operations (Hive-specific)

| # | Requirement | Hive AI | Status |
|---|---|---|---|
| 10.1 | Per-agent email address for leads | `EmailApi` (AgentMail) | 🟡 |
| 10.2 | In-app per-agent inbox + sent viewer | models only, no screen | ⬜ |
| 10.3 | Stripe link only when a buyer is ready | `StripeApi` | 🟡 |
| 10.4 | Pipeline lead → qualify → pitch → close | `SalesDesk` | 🟡 |
| 10.5 | Business identity (Local Reach Workshop, EIN) on documents | — | ⬜ |
| 10.6 | Suggestion box that outputs a coding-agent prompt | `SuggestionEngine` | 🟡 |

## 11. Platform and model

| # | Grok Bot behaviour | Hive AI | Status |
|---|---|---|---|
| 11.1 | Phone + desktop | Android 9+ | ✅ |
| 11.2 | Automatic model choice | NVIDIA picker (free tier) | ⬜ |
| 11.3 | 38 languages | English | ⬜ |
| 11.4 | Usage metering | — | ⬜ |

## 12. Keys survive updates

| # | Requirement | Status |
|---|---|---|
| 12.1 | In-place update keeps keys | needs original keystore (blocker) |
| 12.2 | No keys in the binary | ✅ |
| 12.3 | Vault export/backup | ⬜ |

## Build order

1. Keystore recovery — blocks every future update.
2. Source into the repo.
3. Per-agent inbox/sent screens + orchestrator auto-hire.
4. Agent-to-agent DMs in shared chat, fed by the 3D view.
5. Mistake ledger + retrieval.
6. Suggestion box → copyable prompt.
7. Stripe on-demand flow with approval gate.
