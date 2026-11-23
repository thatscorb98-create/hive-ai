# hive-2.9.0.apk — analysis

Static analysis only; the app was never executed.

## Build identity

| | |
|---|---|
| Label | Hive |
| Package | `com.hive.agents` |
| versionName / versionCode | 2.9.0 / 16 |
| minSdk / targetSdk / compileSdk | 28 / 35 / 35 |
| Toolchain | Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, JVM 17 |
| Signing | v2 only, self-signed `CN=Hive, OU=Hive, O=Hive, C=US`, cert `10af94b3…d8a428` |
| debuggable | false |
| allowBackup | **true** (app data can leave the device via backup) |

## Stack

Kotlin + Jetpack Compose Material 3, Room (+ KTX), WorkManager, Lifecycle/ViewModel Compose, OkHttp, `androidx.browser` custom tabs. No React Native, Flutter or WebView shell.

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

## Components

- Activities: `MainActivity`, `BrowserActivity`
- Services: `service.AgentRunService` (foreground), WorkManager system services
- Receivers: WorkManager/profiling only
- Providers: `androidx.startup.InitializationProvider`

## Code map

```
agent/     TeamEngine, ToolRegistry, ApprovalBroker, ApprovalRequest,
           AgentBrowser, BrowserHost, Plan, PlannedTask, PendingChat
business/  AutomationEngine, AutomationScheduler, Autopilot, InboxSweep,
           SalesDesk, StudioFiles, SuggestionEngine, SuggestionScheduler,
           BusinessNotifications
net/       NimClient, EmailApi, GitHubApi, StripeApi, StorageApi, SocialApi,
           ImageGen, ConnectProvider, ConnectVerify, AgentInbox
service/   AgentRunService, AutomationWorker, AutopilotWorker,
           ReplyWatchWorker, SuggestionWorker, ReplyWatchScheduler
data/      HiveStore, KeyVault, StoredKeys, Settings, and models: Agent, Mission,
           Task, Message, Automation, Product, Prospect, Deliverable,
           MemoryEntry, SentMail, RoleAssignment, TeamSuggestion
ui/        screens/{Missions, Mission, Team, Inbox, Sales, Studio, Business,
           Browsers, HiveViz, Settings}, ConnectSheet, KeySetup, SetupSheet,
           Voice, theme
```

## Third-party endpoints referenced

- `https://integrate.api.nvidia.com/v1` (+ `/models`) — LLM
- `https://api.agentmail.to/v0` — agent email
- `https://api.github.com` — repo/account tooling
- `https://api.stripe.com` (+ `/v1/account`, `/v1/balance`) — payments
- `https://supabase.com/docs/guides/storage`, `https://xxxx.supabase.co` — file storage placeholder
- `https://zernio.com/api/v1` — social accounts
- `https://html.duckduckgo.com/html/?q=` — web search

## Model ids found in the binary

`nvidia/nemotron-3.5-lightning-30b-a3b`, `nvidia/nemotron-3-super-120b-a12b`,
`nvidia/nemotron-3-ultra-550b-a55b`, `deepseek-ai/deepseek-v4.1-flash`.

## Security findings

- No API keys, tokens or JWT-shaped secrets embedded in the dex files. Keys are runtime-supplied and held in `KeyVault`.
- VCS metadata was stripped (`NO_SUPPORTED_VCS_FOUND`), so no repo URL is recoverable from the APK.
- `allowBackup=true` means vault data could be included in Android backups; consider `allowBackup=false` plus an explicit encrypted export.
- Debug symbols and source file names are stripped by R8.
