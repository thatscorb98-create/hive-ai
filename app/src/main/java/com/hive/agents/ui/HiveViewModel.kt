package com.hive.agents.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hive.agents.agent.HiveGraph
import com.hive.agents.browser.BrowserHub
import com.hive.agents.data.Deliverable
import com.hive.agents.data.DeliverableKind
import com.hive.agents.data.HiveState
import com.hive.agents.data.InboxMail
import com.hive.agents.data.MemoryKind
import com.hive.agents.data.MessageKind
import com.hive.agents.data.Mission
import com.hive.agents.data.MissionStatus
import com.hive.agents.data.Models
import com.hive.agents.data.Product
import com.hive.agents.data.Prospect
import com.hive.agents.data.ProspectStage
import com.hive.agents.data.Service
import com.hive.agents.data.Settings
import com.hive.agents.data.VaultKeys
import com.hive.agents.net.NimClient
import com.hive.agents.service.AutopilotWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class HiveViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = HiveGraph.get(app)
    private val store = graph.store
    private val vault = graph.vault
    private val memory = graph.memory
    private val approvals = graph.approvals
    private val mail = graph.mail
    private val stripe = graph.stripe
    private val nim = graph.nim
    private val suggestions = graph.suggestions
    private val engine = graph.engine

    val state: StateFlow<HiveState> = store.state
    val pendingApproval = approvals.pending
    val pendingBrowser = BrowserHub.pendingOpen

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
        scheduleAutopilot()
    }

    // ---------- setup ----------

    fun saveKeys(newKeys: VaultKeys) {
        vault.save(newKeys)
        _keys.value = vault.load()
        _notice.value = "Keys saved to the encrypted vault."
    }

    fun updateSettings(settings: Settings) {
        store.update { it.copy(settings = settings) }
    }

    fun setAutopilot(enabled: Boolean) {
        store.update { it.copy(settings = it.settings.copy(autopilot = enabled)) }
        if (enabled) scheduleAutopilot()
    }

    fun dismissNotice() {
        _notice.value = ""
    }

    private fun scheduleAutopilot() {
        val request = PeriodicWorkRequestBuilder<AutopilotWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        runCatching {
            WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
                AutopilotWorker.NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    /** Human-triggered shift. Same code path the worker uses. */
    fun runShift() {
        if (_busy.value) return
        val stateNow = store.snapshot()
        val next = stateNow.missions.firstOrNull {
            it.status == MissionStatus.PLANNED || it.status == MissionStatus.RUNNING
        } ?: Mission(
            title = stateNow.settings.goal.ifBlank { "Earn for the business" },
            brief = "Pick the most promising way to bring in revenue this shift and execute it."
        ).also { mission ->
            store.update { current -> current.copy(missions = current.missions + mission) }
        }
        _busy.value = true
        viewModelScope.launch {
            runCatching { engine.runMission(next.id) }
                .onFailure { _notice.value = it.message ?: "Shift failed" }
            _busy.value = false
        }
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
                        NimClient.Msg(
                            "system",
                            "You are ${agent.name}, the ${agent.role} at ${store.snapshot().settings.businessName}. " +
                                "Answer briefly and say what you will do next."
                        ),
                        NimClient.Msg("user", text)
                    ),
                    model = store.snapshot().settings.model
                )
                engine.say(agent, reply.content.ifBlank { "..." })
            }.onFailure { engine.say(agent, "I hit an error: ${it.message}", kind = MessageKind.SYSTEM) }
            _busy.value = false
        }
    }

    /** Command bar: talk to the orchestrator. */
    fun command(text: String) {
        val chief = store.snapshot().orchestrator ?: return
        messageAgent(chief.id, text)
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

    // ---------- browser ----------

    fun openBrowser(agentId: String, url: String = "") {
        val agent = store.snapshot().agent(agentId) ?: return
        BrowserHub.requestOpen(agent.id, agent.name, url)
    }

    fun clearBrowserRequest() {
        BrowserHub.clearRequest()
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

    // ---------- products ----------

    fun addProduct(name: String, description: String, price: String, kind: DeliverableKind) {
        if (name.isBlank()) return
        val cents = price.toDoubleOrNull()?.times(100)?.toLong() ?: 0L
        store.update { state ->
            state.copy(
                products = state.products + Product(
                    name = name.trim(),
                    description = description.trim(),
                    priceCents = cents,
                    kind = kind,
                    createdBy = state.orchestrator?.id.orEmpty()
                )
            )
        }
    }

    fun createCheckout(productId: String) {
        val product = store.snapshot().products.firstOrNull { it.id == productId } ?: return
        if (product.priceCents <= 0) {
            _notice.value = "Set a price before creating a checkout."
            return
        }
        _busy.value = true
        viewModelScope.launch {
            runCatching {
                stripe.createPaymentLink(
                    amountCents = product.priceCents,
                    currency = store.snapshot().settings.currency,
                    description = "${store.snapshot().settings.businessName} - ${product.name}"
                )
            }.onSuccess { link ->
                _notice.value = link
                store.update { state ->
                    state.copy(products = state.products.map {
                        if (it.id == product.id) it.copy(checkoutUrl = link, status = "on sale") else it
                    })
                }
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
