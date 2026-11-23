# Grok Bot research notes

Sources (Nov 2026):

- https://x.ai/bot — product page
- https://docs.x.ai/grok-bot/overview
- https://docs.x.ai/grok-bot/bots
- https://docs.x.ai/grok-bot/computer-and-apps
- https://docs.x.ai/grok-bot/approvals-security-and-privacy
- https://docs.x.ai/grok-bot/faq
- https://x.ai/bot/guides/grok-bot-101

## What Grok Bot actually is

An AI teammate with a persistent **cloud computer** (browser, terminal, filesystem). Bots work while your laptop is closed, keep role-specific memory, and coordinate with other bots. Chat, voice, Slack and X are the interfaces; macOS/Windows/Linux plus iOS 18+/Android 9+ are the clients.

## Design decisions worth copying

1. **One shared computer, one screen per bot.** Cheap to build, gives parallel work with a single login state. Each screen is a work surface, not a security boundary — and they say so explicitly.
2. **Memory is per bot, with a few facts shared across all bots** (name, timezone). Deleting a bot deletes its memory.
3. **Approvals are the safety model**, not prompt wording. Auto Review checks each action; ask-first rules always stop; built-in checks cannot be overridden by user rules.
4. **Background approvals expire (~30 min)**, in-chat ones wait indefinitely. Prevents stale actions firing hours later.
5. **Secrets never reach the model.** Vault and Secure Forms fill values into the page; the model sees a placeholder. This is the single most important pattern for a business app that holds cards and API keys.
6. **Take over the computer** for SSO, passkeys, CAPTCHA, 2FA, card verification. Automation stops rather than pretending.
7. **Skills vs routines**: a skill is how, a routine is when. Test as a one-off first, then schedule.
8. **Templates for sharing** — a bot's configuration travels; its memory, logins and history do not.
9. **Model choice is automatic and hidden.** Hive deliberately diverges: users pick an NVIDIA model so cost is predictable on the free tier.

## Where Hive AI must diverge

| Grok Bot | Hive AI |
|---|---|
| No model picker, pooled subscription | Explicit NVIDIA model, free tier |
| Bots share one cloud computer | Each agent gets its own browser profile on the device |
| Slack/X as surface | Android app as surface |
| Generic assistant | Runs a business: leads, email, deliverables, Stripe |
| No money movement | On-demand Stripe links behind approval |
