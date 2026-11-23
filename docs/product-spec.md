# Hive AI — product spec

## What it is

Hive AI is an Android app where a team of AI agents runs a service business. The company starts with **one lead bot**. That lead knows it runs **Local Reach Workshop**, hires the marketing, research, sales, product and proofreading people it needs, and keeps the company moving while the human watches the chat and approves anything that needs a human.

Business: **Local Reach Workshop** — a registered business with an EIN. Hive ships work under that name.

## Agents

- **Lead (primary)** — the human's single point of contact and the company's boss. It starts alone, decides what to sell and who to hire, and is expected to build the whole company: marketing, research, sales, product and a proofreader. It is judged on sales made.
- **Specialists** — created by the lead (or any bot) with `hire_agent`, or by the human in Agents. Each has a persistent role, tool set and memory. Roles are data, not code: hiring a new role does not require an app update.
- Hiring persists. A bot hired for a job survives restarts and keeps its memory.
- **Deleting a bot** removes the bot, its memory and its automations. Its past messages stay in the chat as history. Deleting the last bot starts a fresh lead so the company keeps running.
- One-time upgrade: the old auto-hired trio collapses to the lead, which then builds the team it actually needs.

## Per-agent capabilities

| Capability | Behaviour |
|---|---|
| Browser | One browser window per agent, its own URL and history, visible in the Browsers tab, human can take over the screen |
| Email | One address per agent (AgentMail). Read inbox, send, view sent folder |
| Memory | Preferences, standing rules, facts, dated work log, **mistake ledger** |
| Tools | Registered in `ToolRegistry` with an approval class |
| Hiring | `hire_agent` creates a teammate at any time |
| Deliverables | Website, graphic, copy, document, digital good — attached to the mission |

Android shares one cookie jar across WebViews, and one window is mounted at a time. Both limits are stated in the Browsers screen.

## Collaboration

- One **team chat** where all agents post and the human can address a specific bot.
- **Agent-to-agent DMs**: a directed channel so two bots can coordinate without spamming the human.
- Handoff = a task moves from one bot to another with context attached.
- A human message is a **work order**, not just a chat reply: the bot keeps its tools during the turn.
- **3D view**: a live graph of the team — nodes are bots, edges light up when they message or hand off, and the lead sits at the centre.

## Memory and mistakes

- Each memory row: `kind` (preference / rule / fact / lesson / worklog), text, source, created, last used.
- **Mistake ledger**: when a task fails or the human corrects the bot, record `what happened → why → what to do instead`. Before retrying a similar task, retrieve the top matches and inject them.
- Shared facts (business name, EIN, brand voice, the team-building mandate) are available to every agent.
- Memory is editable in plain language: "what do you remember", "forget that", "that's out of date".
- Deleting a bot deletes its private memory.

## Sales and money

- Pipeline states: `new → qualified → pitched → negotiating → won/lost`.
- Sales bots research real businesses with `search_web` and `fetch_page`, add them in bulk with `add_leads`, contact them with `send_email`, and record closings with `record_sale`.
- **Progress is measured by sales made** and money collected. Home and Business show those numbers first.
- **Stripe links are created only when a buyer is ready to pay** — at the moment of the ask, for that specific job and amount. No pre-generated link inventory, no reuse across customers.
- Every link creation shows an approval card with amount, currency, description and customer. Human approves; the link is then sent.
- The Business tab is read-only: bots add products and leads. The human can advance a lead or create a link for a ready buyer.
- Invoices/contracts carry the Local Reach Workshop identity.

## Proofreading

- Every outbound email, every product description and every COPY deliverable passes `agent/Proofreader` before it leaves the building.
- The proofreader keeps facts, prices, names and links exactly; cuts AI tells and filler; never invents a client, result, guarantee or deadline.
- It is a style-and-safety gate, not a fact-checker. Agents remain responsible for the truth of a claim.
- The lead is told to hire a proofreader bot as part of the team. The code gate applies even before that hire.

## Autopilot

- On by default. A shift starts when the app comes to the front, then every 15 minutes through WorkManager.
- A shift picks up the oldest planned/running mission or starts one from the standing goal.
- Autopilot never bypasses a payment approval. Email approval is the owner's switch; when off, the proofreader still runs.
- Turning autopilot off stops the scheduled work; manual shifts remain available.

## Suggestion box

Agents notice friction (a tool that keeps failing, a missing screen, a repeated manual step) and write a **ready-to-paste prompt** for a coding agent.

## Models

- NVIDIA hosted API (`https://integrate.api.nvidia.com/v1`), free-tier first.
- Model ids referenced in 2.9.0: `nvidia/nemotron-3.5-lightning-30b-a3b`, `nvidia/nemotron-3-super-120b-a12b`, `nvidia/nemotron-3-ultra-550b-a55b`, `deepseek-ai/deepseek-v4.1-flash`.
- Default = the cheapest model that can do the job; escalate per task, not per user.
- No paid fallback without an explicit setting.

## Keys and updates

- Keys are entered once and stored in the app vault (encrypted at rest), with a backup copy.
- Business identity, EIN and settings live in the app state file, also with a backup copy.
- **Updating the app must not require re-entering keys.** That means every build is signed with the same release keystore, and the app data is preserved by an in-place install.
- Uninstalling, or installing a build signed with a different key, clears app data. Vault export is still required so a reinstall is not a total loss.

## Open questions

1. Where is the release keystore, and do we have its password? Without it, no in-place update is possible.
2. NVIDIA API key — whose account, and does it have the free-tier models enabled?
3. AgentMail account: how many inboxes/domains are available?
4. Stripe: live or test, and does Local Reach Workshop have a verified account?
5. Do agents email from one shared domain (`agent@localreachworkshop.com`) or per-agent subaddresses?
6. Where does the 3D view get its state — local store or a server?
