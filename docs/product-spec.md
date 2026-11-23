# Hive AI — product spec

## What it is

Hive AI is an Android app where a team of AI agents runs a service business. The company starts with **one lead bot**. That lead knows it runs **Local Reach Workshop**, hires the marketing, research, sales, product, showcase and proofreading people it needs, and keeps the company moving while the human watches the chat and approves anything that needs a human.

Business: **Local Reach Workshop** — a registered business with an EIN. Hive ships work under that name.

## Agents

- **Lead (primary)** — the human's single point of contact and the company's boss. It starts alone, decides what to sell and who to hire, and is expected to build the whole company: marketing, research, sales, product, showcase and a proofreader. It is judged on sales made.
- **Showcase** — makes customer-facing samples of what the company sells, so a buyer can judge the work before paying. Samples are proofread and appear in Home's RECENT WORK strip.
- **Specialists** — created by the lead (or any bot) with `hire_agent`, or by the human in the Team screen (reachable from Home or Chat). Each has a persistent role, tool set and memory. Roles are data, not code: hiring a new role does not require an app update.
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

- **Five tabs only**: Home, Chat, Browsers, Email, Settings. Business, Studio and Agents are not tabs.
- **Company chat**: a left rail of bot blobs plus Company; selecting a bot shows its direct messages and handoffs as well as its broadcasts. Bubbles are iMessage-dark, with timestamps, a working bar and copyable code blocks.
- **Agent-to-agent DMs** and **handoffs** are real messages visible in both participants' threads, so the human can peer into a working conversation.
- A human message is a **work order**, not just a chat reply: the bot keeps its tools during the turn. In the Company thread the message goes to the lead; selecting a bot addresses that bot.
- **Hive Station**: a 2.5D station cutaway inspired by StarNet's idea that the layout is the runtime. Rooms are departments, hallways are handoff lanes, bots sit at workstations, and moving packets are real messages. Tap a room to isolate it. It is a projection of tasks and messages, not decoration.

## Company loop

- Autopilot is a loop, not one shift. While the app process is alive and Autopilot is on, the team runs a shift, waits 45 seconds and runs the next one. WorkManager adds a 15-minute shift in the background.
- Each loop picks the next funnel stage instead of inventing work: deliver paid work → research missing contacts → create an offer → make samples → contact qualified leads → follow up and close → find new leads.
- A shift that fails every task backs off for five minutes instead of burning credits in a hot loop.
- The team works in parallel: all planned tasks are dispatched at once. Approval dialogs are serialized; the API calls after them are not.
- Each bot routes to a model by role: lead and product use Super, research/sales/marketing/showcase use Lightning, the proofreader uses DeepSeek. A bot-level override and a Setup switch are available.

## Memory and mistakes

- Each memory row: `kind` (preference / rule / fact / lesson / worklog), text, source, created, last used.
- **Mistake ledger**: when a task fails or the human corrects the bot, record `what happened → why → what to do instead`. Before retrying a similar task, retrieve the top matches and inject them.
- Shared facts (business name, EIN, brand voice, the team-building mandate) are available to every agent.
- Memory is editable in plain language: "what do you remember", "forget that", "that's out of date".
- Deleting a bot deletes its private memory.

## Sales and money

- Pipeline states: `new → qualified → pitched → negotiating → won/lost`, with `paid` and `delivered` flags on each lead.
- Sales bots research real businesses with `search_web` and `fetch_page`, add them in bulk with `add_leads`, contact them with `send_email`, and record payments with `record_sale`.
- After payment the work still has to be done: the bot saves the finished work and marks the job closed with `deliver_work`. A paid, undelivered customer is the loop's first priority.
- **Progress is measured by sales made** and money collected. Home shows those numbers first.
- **Stripe links are created only when a buyer is ready to pay** — at the moment of the ask, for that specific job and amount. No pre-generated link inventory, no reuse across customers.
- Every link creation shows an approval card with amount, currency, description and customer. Human approves; the link is then sent.
- There is no Business tab. Bots add products, services and leads on demand; the human can advance a lead or create a link for a ready buyer through the crew tools.
- Invoices/contracts carry the Local Reach Workshop identity.

## Proofreading

- Every outbound email, every product description, every customer sample and every COPY deliverable passes `agent/Proofreader` before it leaves the building.
- The proofreader keeps facts, prices, names and links exactly; cuts AI tells and filler; never invents a client, result, guarantee or deadline.
- It is a style-and-safety gate, not a fact-checker. Agents remain responsible for the truth of a claim.
- The lead is told to hire a proofreader bot as part of the team. The code gate applies even before that hire.

## Autopilot

- On by default, from the first composed screen. `HiveApp` starts the loop in `LaunchedEffect(Unit)` and again on every `ON_RESUME`; the loop guard makes that idempotent. While the app process is alive the company loops: run a shift, wait 45 seconds, run the next. WorkManager adds a 15-minute shift in the background.
- Each shift picks up an existing planned/running mission or creates the next funnel stage with `nextCompanyMission`.
- A shift where every task fails waits five minutes before the next attempt.
- Autopilot never bypasses a payment approval. Email approval is the owner's switch; when off, the proofreader still runs.
- Turning autopilot off cancels the foreground loop and the scheduled work; manual shifts remain available.

## Suggestion box

Agents notice friction (a tool that keeps failing, a missing screen, a repeated manual step) and write a **ready-to-paste prompt** for a coding agent.

## Models

- NVIDIA hosted API (`https://integrate.api.nvidia.com/v1`), free-tier first.
- Current defaults: `nvidia/nemotron-3.5-lightning-30b-a3b`, `nvidia/nemotron-3-super-120b-a12b`, `deepseek-ai/deepseek-v4-flash`.
- NVIDIA retires ids without notice. `NimClient` reads `/v1/models`, migrates saved ids (including the retired `deepseek-ai/deepseek-v4.1-flash`), falls back to a live model on 404/410, and throws `NimException` instead of returning error text as bot content.
- Default = the cheapest model that can do the job; escalate per task, not per user.
- `ModelRouter` picks per role; the owner can turn routing off or pin a model per bot.
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
