# Hive AI — product spec

## What it is

Hive AI is an Android app where a team of AI agents runs a service business. One orchestrator hires specialists, each specialist has its own browser, its own email address, its own memory, and they talk to each other in a shared chat while the human approves anything that costs money or leaves the building.

Business: **Local Reach Workshop** — a registered business with an EIN. Hive ships work under that name.

## Agents

- **Orchestrator (primary)** — the human's single point of contact. Reads the mission, decides which roles are needed, hires them, chases status, reports back.
- **Specialists** — created by the orchestrator or the human, each with a persistent role, tool set and memory. Roles are data (`RoleAssignment`), not code: hiring a new role must not require shipping an app update.
- Hiring persists. A bot hired for a job survives restarts and keeps its memory.
- Delete/hide semantics: hiding keeps history and routines; deleting erases memory.

## Per-agent capabilities

| Capability | Behaviour |
|---|---|
| Browser | One browser profile per agent, own cookies/session, visible in the UI, human can take over the screen |
| Email | One address per agent (AgentMail). Read inbox, send with approval, view sent folder |
| Memory | Preferences, standing rules, facts, dated work log, **mistake ledger** |
| Tools | Registered in `ToolRegistry` with an approval class |
| Deliverables | Website, graphic, copy, document, digital good — attached to the mission |

## Collaboration

- One **team chat** where all agents post and the human can address a specific bot.
- **Agent-to-agent DMs**: a directed channel so two bots can coordinate without spamming the human.
- Handoff = a task moves from one bot to another with context attached.
- **3D view**: a live graph of the team — nodes are bots, edges light up when they message or hand off, and the orchestrator sits at the centre. The point is to see who is blocked, who is working, who is idle.

## Memory and mistakes

- Each memory row: `kind` (preference / rule / fact / lesson / worklog), text, source, created, last used.
- **Mistake ledger**: when a task fails or the human corrects the bot, record `what happened → why → what to do instead`. Before retrying a similar task, retrieve the top matches and inject them.
- Shared facts (business name, EIN, timezone, brand voice) are available to every agent.
- Memory is editable in plain language: "what do you remember", "forget that", "that's out of date".

## Sales and money

- Pipeline states: `new → qualified → pitched → negotiating → won/lost`.
- Agents research and qualify leads, draft outreach, send with approval.
- **Stripe links are created only when a buyer is ready to pay** — at the moment of the ask, for that specific job and amount. No pre-generated link inventory, no reuse across customers.
- Every link creation shows an approval card with amount, currency, description and customer. Human approves; the link is then sent.
- Invoices/contracts carry the Local Reach Workshop business identity.

## Suggestion box

Agents notice friction (a tool that keeps failing, a missing screen, a repeated manual step) and write a **ready-to-paste prompt** for a coding agent:

```
Problem: <what went wrong, with the failing step>
Evidence: <log lines / message ids>
Proposed change: <specific fix in this repo>
Files likely involved: <paths>
Acceptance: <how to verify>
```

The human copies it into whatever coding agent they use. This repo is the shared state so any agent can pick it up.

## Models

- NVIDIA hosted API (`https://integrate.api.nvidia.com/v1`), free-tier first.
- Model ids referenced in 2.9.0: `nvidia/nemotron-3.5-lightning-30b-a3b`, `nvidia/nemotron-3-super-120b-a12b`, `nvidia/nemotron-3-ultra-550b-a55b`, `deepseek-ai/deepseek-v4.1-flash`.
- Default = the cheapest model that can do the job; escalate per task, not per user.
- No paid fallback without an explicit setting.

## Keys and updates

- Keys are entered once and stored in the app vault (encrypted at rest).
- **Updating the app must not require re-entering keys.** That means every build is signed with the same release keystore, and the app data is preserved by an in-place install.
- Vault export (encrypted) is required so a reinstall is not a total loss.

## Open questions

1. Where is the release keystore, and do we have its password? Without it, no in-place update is possible.
2. NVIDIA API key — whose account, and does it have the free-tier models enabled?
3. AgentMail account: how many inboxes/domains are available?
4. Stripe: live or test, and does Local Reach Workshop have a verified account?
5. Do agents email from one shared domain (`agent@localreachworkshop.com`) or per-agent subaddresses?
6. Where does the 3D view get its state — local Room or a server?
