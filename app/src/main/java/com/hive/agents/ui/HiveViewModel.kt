package com.hive.agents.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hive.agents.agent.ApprovalBroker
import com.hive.agents.agent.MemoryService
import com.hive.agents.agent.SuggestionBox
import com.hive.agents.agent.TeamEngine
import com.hive.agents.agent.ToolRegistry
import com.hive.agents.data.Deliverable
import com.hive.agents.data.DeliverableKind
import com.hive.agents.data.HiveState
import com.hive.agents.data.HiveStore
import com.hive.agents.data.InboxMail
import com.hive.agents.data.KeyVault
import com.hive.agents.data.MemoryKind
import com.hive.agents.data.MessageKind
import com.hive.agents.data.Mission
import com.hive.agents.data.Models
import com.hive.agents.data.Prospect
import com.hive.agents.data.ProspectStage
import com.hive.agents.data.Service
import com.hive.agents.data.Settings
import com.hive.agents.data.VaultKeys
import com.hive.agents.net.AgentMailApi
import com.hive.agents.net.NimClient
import com.hive.agents.net.StripeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class HiveViewModel(app: Application) : AndroidViewModel(app) {

    private val store = HiveStore(app)
    private val vault = KeyVault(app)
    private val memory = MemoryService(store)
    private val approvals = ApprovalBroker()
    private val mail = AgentMailApi(vault)
    private val stripe = StripeApi(vault)
    private val nim = NimClient(vault)
    private val suggestions = SuggestionBox(store)
    private val tools = ToolRegistry(store, vault, memory, approvals, mail, stripe)
    private val engine = TeamEngine(store, nim, tools, memory, suggestions)

    val state: StateFlow<HiveState> = store.state
    val pendingApproval = approvals.pending

    private val _keys = MutableStateFlow(vault.load())
    val keys: StateFlow<VaultKeys> = _keys.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _notice = MutableStateFlow("")
    val notice: StateFlow<String> = _notice.asStateFlow()

    private val _inbox = MutableStateFlow<List<InboxMail>>(emptyList())
    val inbox: StateFlow<List<InboxMail>> = _inbox.asStateFlow()

    private val _selectedMission = MutableStateFlow<String?>(null)
    val selectedMission: StateFlow<String?> = _selectedMission.asStateFlow()

    init {
        engine.bootstrap()
    }

    // ---------- setup ----------

    fun saveKeys(newKeys: VaultKeys) {
        vault.save(newKeys)
        _keys.value = vault.load()
        _notice.value = "Keys saved to the vault. They survive app updates."
    }

    fun updateSettings(settings: Settings) {
        store.update { it.copy(settings = settings) }
    }

    fun dismissNotice() {
        _notice.value = ""
    }

    // ---------- missions ----------

    fun createMission(title: String, brief: String) {
        if (title.isBlank()) return
        val mission = Mission(id = UUID.randomUUID().toString(), title = title.trim(), brief = brief.trim())
        store.update { it.copy(missions = it.missions + mission) }
        _selectedMission.value = mission.id
    }

    fun selectMission(id: String?) {
        _selectedMission.value = id
    }

    fun startMission(id: String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            runCatching { engine.runMission(id) }
                .onFailure { _notice.value = it.message ?: "Mission failed" }
            _busy.value = false
        }
    }

    fun deleteMission(id: String) {
        store.update { state ->
            state.copy(
                missions = state.missions.filterNot { it.id == id },
                tasks = state.tasks.filterNot { it.missionId == id }
            )
        }
        if (_selectedMission.value == id) _selectedMission.value = null
    }

    // ---------- team ----------

    fun hire(role: String, name: String, charter: String) {
        if (role.isBlank()) return
        viewModelScope.launch {
            engine.hire(role.trim(), name.ifBlank { role.trim() }, charter.trim(), by = "human")
        }
    }

    fun messageAgent(agentId: String, text: String) {
        if (text.isBlank()) return
        val agent = store.snapshot().agent(agentId) ?: store.snapshot().orchestrator ?: return
        engine.say(agent, text, fromUser = true)
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                val reply = nim.chat(
                    listOf(
                        NimClient.Msg("system", "You are ${agent.name}, the ${agent.role}. Answer briefly."),
                        NimClient.Msg("user", text)
                    ),
                    model = store.snapshot().settings.model
                )
                engine.say(agent, reply.content.ifBlank { "..." })
            }.onFailure { engine.say(agent, "I hit an error: ${it.message}", kind = MessageKind.SYSTEM) }
            _busy.value = false
        }
    }

    fun directMessage(fromId: String, toId: String, text: String) {
        val stateNow = store.snapshot()
        val from = stateNow.agent(fromId) ?: return
        val to = stateNow.agent(toId) ?: return
        if (text.isBlank()) return
        engine.dm(from, to, text)
    }

    // ---------- approvals ----------

    fun answerApproval(allow: Boolean) {
        approvals.resolve(allow)
    }

    // ---------- sales ----------

    fun addProspect(company: String, email: String, value: String) {
        if (company.isBlank()) return
        val cents = value.toDoubleOrNull()?.times(100)?.toLong() ?: 0L
        store.update { state ->
            state.copy(
                prospects = state.prospects + Prospect(
                    company = company.trim(),
                    email = email.trim(),
                    valueCents = cents,
                    ownerAgentId = state.orchestrator?.id.orEmpty()
                )
            )
        }
    }

    fun moveProspect(id: String, stage: ProspectStage) {
        store.update { state ->
            state.copy(prospects = state.prospects.map { if (it.id == id) it.copy(stage = stage) else it })
        }
    }

    fun addService(name: String, price: String) {
        if (name.isBlank()) return
        val cents = price.toDoubleOrNull()?.times(100)?.toLong() ?: 0L
        store.update { state -> state.copy(services = state.services + Service(name = name.trim(), priceCents = cents)) }
    }

    /**
     * The only path to a Stripe link: the human presses this for a specific prospect,
     * with the amount on screen, and the call goes out immediately.
     */
    fun createPaymentLink(prospectId: String) {
        val prospect = store.snapshot().prospects.firstOrNull { it.id == prospectId } ?: return
        if (prospect.valueCents <= 0) {
            _notice.value = "Set a deal value before creating a link."
            return
        }
        _busy.value = true
        viewModelScope.launch {
            runCatching {
                stripe.createPaymentLink(
                    amountCents = prospect.valueCents,
                    currency = store.snapshot().settings.currency,
                    description = "${store.snapshot().settings.businessName} - ${prospect.company}",
                    customerEmail = prospect.email
                )
            }.onSuccess { link ->
                _notice.value = link
                memory.remember(
                    store.snapshot().orchestrator?.id ?: "*",
                    MemoryKind.FACT,
                    "Payment link created for ${prospect.company}: $link",
                    source = "stripe"
                )
            }.onFailure { _notice.value = "Stripe: ${it.message}" }
            _busy.value = false
        }
    }

    // ---------- studio / deliverables ----------

    fun addDeliverable(title: String, body: String, kind: DeliverableKind = DeliverableKind.COPY) {
        if (title.isBlank()) return
        store.update { state ->
            state.copy(
                deliverables = state.deliverables + Deliverable(
                    title = title.trim(),
                    body = body,
                    kind = kind,
                    agentId = state.orchestrator?.id.orEmpty()
                )
            )
        }
    }

    // ---------- inbox ----------

    fun refreshInbox(agentId: String) {
        val agent = store.snapshot().agent(agentId) ?: return
        viewModelScope.launch {
            _busy.value = true
            val messages = withContext(Dispatchers.IO) {
                runCatching { mail.inbox(agent.email) }.getOrDefault(emptyList())
            }
            _inbox.value = messages
            if (messages.isNotEmpty()) {
                store.update { state ->
                    state.copy(inbox = (messages + state.inbox).distinctBy { it.id }.take(200))
                }
            }
            _busy.value = false
        }
    }

    // ---------- suggestions ----------

    fun addSuggestion(title: String, problem: String) {
        suggestions.add(title = title, problem = problem, acceptance = "Verified in a build.")
    }

    fun markSuggestionCopied(id: String) {
        suggestions.markCopied(id)
    }

    // ---------- memory ----------

    fun forgetMemory(id: String) {
        memory.forget(id)
    }

    fun rememberNote(text: String) {
        memory.remember("*", MemoryKind.FACT, text, source = "human")
    }

    fun modelOptions(): List<String> = Models.all
}
