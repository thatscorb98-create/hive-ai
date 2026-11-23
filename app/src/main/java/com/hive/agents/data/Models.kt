package com.hive.agents.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class MissionStatus { PLANNED, RUNNING, BLOCKED, DONE, FAILED }

@Serializable
enum class TaskStatus { QUEUED, RUNNING, BLOCKED, DONE, FAILED }

@Serializable
enum class MessageKind { CHAT, DM, HANDOFF, SYSTEM, NOTE }

@Serializable
enum class MemoryKind { PREFERENCE, RULE, FACT, LESSON, WORKLOG }

@Serializable
enum class DeliverableKind { WEBSITE, IMAGE, COPY, DOCUMENT, CODE, OTHER }

@Serializable
enum class ProspectStage { NEW, QUALIFIED, PITCHED, NEGOTIATING, WON, LOST }

/** A durable teammate. Hired by the orchestrator or by the human, and it keeps its memory. */
@Serializable
data class Agent(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String,
    val charter: String = "",
    val email: String = "",
    val colorSeed: Int = 0,
    val hiredAt: Long = System.currentTimeMillis(),
    val hiredBy: String = "orchestrator",
    val active: Boolean = true,
    /** Separate browser profile id, so each agent keeps its own cookies and session. */
    val browserProfile: String = "",
    val primary: Boolean = false,
    /** Optional model override. Blank means route by role. */
    val model: String = ""
) {
    val profileId: String get() = browserProfile.ifBlank { id }
}

@Serializable
data class Mission(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val brief: String = "",
    val status: MissionStatus = MissionStatus.PLANNED,
    val createdAt: Long = System.currentTimeMillis(),
    val ownerAgentId: String = "",
    val result: String = ""
)

@Serializable
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String,
    val title: String,
    val detail: String = "",
    val assigneeId: String = "",
    val status: TaskStatus = TaskStatus.QUEUED,
    val output: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String,
    val missionId: String = "",
    val toAgentId: String = "",
    val kind: MessageKind = MessageKind.CHAT,
    val body: String,
    val fromUser: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class Deliverable(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String = "",
    val agentId: String = "",
    val kind: DeliverableKind = DeliverableKind.OTHER,
    val title: String,
    val body: String = "",
    val uri: String = "",
    /** A demo made for buyers to see, not a paid job. */
    val isSample: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** One remembered fact, rule or mistake. [agentId] "*" means every agent can read it. */
@Serializable
data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String = "*",
    val kind: MemoryKind = MemoryKind.FACT,
    val text: String,
    val context: String = "",
    val source: String = "agent",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L,
    val uses: Int = 0
)

@Serializable
data class Automation(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String = "",
    val name: String,
    val prompt: String,
    val everyMinutes: Int = 1440,
    val enabled: Boolean = true,
    val lastRunAt: Long = 0L
)

@Serializable
data class Prospect(
    val id: String = UUID.randomUUID().toString(),
    val company: String,
    val contactName: String = "",
    val email: String = "",
    val stage: ProspectStage = ProspectStage.NEW,
    val notes: String = "",
    val valueCents: Long = 0L,
    val ownerAgentId: String = "",
    /** Payment received. The work still has to be delivered. */
    val paid: Boolean = false,
    val delivered: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class Service(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val priceCents: Long = 0L,
    val deliverableKind: DeliverableKind = DeliverableKind.OTHER,
    val active: Boolean = true
)

/** Something the business sells: a digital good or an AI-run service. */
@Serializable
data class Product(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val priceCents: Long = 0L,
    val kind: DeliverableKind = DeliverableKind.OTHER,
    val status: String = "draft",
    val checkoutUrl: String = "",
    val assetBody: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class SentMail(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String = "",
    val from: String = "",
    val to: String,
    val subject: String,
    val body: String,
    val sentAt: Long = System.currentTimeMillis()
)

@Serializable
data class InboxMail(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String = "",
    val from: String,
    val subject: String,
    val preview: String = "",
    val body: String = "",
    val receivedAt: Long = System.currentTimeMillis(),
    val unread: Boolean = true
)

/** A prompt the agents recommend the human paste into a coding agent. */
@Serializable
data class Suggestion(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val problem: String,
    val evidence: String = "",
    val proposedChange: String = "",
    val files: String = "",
    val acceptance: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val copied: Boolean = false
) {
    fun asPrompt(): String = buildString {
        appendLine("Problem: $problem")
        if (evidence.isNotBlank()) appendLine("Evidence: $evidence")
        if (proposedChange.isNotBlank()) appendLine("Proposed change: $proposedChange")
        if (files.isNotBlank()) appendLine("Files likely involved: $files")
        if (acceptance.isNotBlank()) appendLine("Acceptance: $acceptance")
    }
}

@Serializable
data class Settings(
    val businessName: String = "Local Reach Workshop",
    val businessEin: String = "",
    val fromName: String = "Local Reach Workshop",
    val model: String = Models.DEFAULT,
    val autoApproveLowRisk: Boolean = true,
    /** Email is gated by default; an injected page must not be able to send mail on its own. */
    val requireApprovalForEmail: Boolean = true,
    /** Comma, newline or semicolon separated addresses that must never be emailed. */
    val emailBlocklist: String = "",
    val requireApprovalForPayment: Boolean = true,
    val currency: String = "usd",
    val goal: String = "Sell digital goods and AI-run services for Local Reach Workshop",
    val autopilot: Boolean = true,
    /** The company runs a shift when the app opens and every 15 minutes after that. */
    val autopilotDefaulted: Boolean = false,
    /** One-time upgrade of the old auto-hired trio into a single lead who builds the team. */
    val teamCleaned: Boolean = false,
    val proofreadOutbound: Boolean = true,
    /** Lead on the strong model, specialists on Lightning, proofreader on DeepSeek. */
    val routeModels: Boolean = true,
    val lastShiftAt: Long = 0L,
    val buyingGuide: String = "Digital goods, AI-run services, websites, graphics, copy and automation for local businesses."
)

@Serializable
data class HiveState(
    val agents: List<Agent> = emptyList(),
    val missions: List<Mission> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val messages: List<Message> = emptyList(),
    val deliverables: List<Deliverable> = emptyList(),
    val memory: List<MemoryEntry> = emptyList(),
    val automations: List<Automation> = emptyList(),
    val prospects: List<Prospect> = emptyList(),
    val services: List<Service> = emptyList(),
    val products: List<Product> = emptyList(),
    val sentMail: List<SentMail> = emptyList(),
    val inbox: List<InboxMail> = emptyList(),
    val suggestions: List<Suggestion> = emptyList(),
    val settings: Settings = Settings()
) {
    val orchestrator: Agent? get() = agents.firstOrNull { it.primary } ?: agents.firstOrNull()
    fun agent(id: String): Agent? = agents.firstOrNull { it.id == id }

    /** Progress is measured by money actually made, not by how busy the bots look. */
    val salesMade: Int get() = prospects.count { it.stage == ProspectStage.WON && it.paid }
    val revenueCents: Long get() = prospects
        .filter { it.stage == ProspectStage.WON && it.paid }
        .sumOf { it.valueCents }
}

/**
 * Model ids available on the free NVIDIA endpoint. NVIDIA retires ids without notice, so the
 * client also checks /v1/models at runtime and falls back instead of trusting this list forever.
 */
object Models {
    const val LIGHTNING = "nvidia/nemotron-3.5-lightning-30b-a3b"
    const val SUPER = "nvidia/nemotron-3-super-120b-a12b"
    const val DEEPSEEK = "deepseek-ai/deepseek-v4-flash"

    const val DEFAULT = LIGHTNING

    val all = listOf(LIGHTNING, SUPER, DEEPSEEK)

    /** Saved settings from 2.12 and older may point at ids NVIDIA has removed. */
    fun migrate(id: String): String = when (id) {
        "deepseek-ai/deepseek-v4.1-flash",
        "deepseek-ai/deepseek-v4-flash-0731" -> DEEPSEEK
        "nvidia/nemotron-3-ultra-550b-a55b" -> SUPER
        else -> id
    }

    fun label(id: String): String = when (id) {
        LIGHTNING -> "Nemotron 3.5 Lightning"
        SUPER -> "Nemotron 3 Super"
        DEEPSEEK -> "DeepSeek v4 Flash"
        else -> id.substringAfterLast('/')
    }
}
