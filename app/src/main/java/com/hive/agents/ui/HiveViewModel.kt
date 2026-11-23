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
import com.hive.agents.data.ProspectStage
import com.hive.agents.data.Settings
import com.hive.agents.data.VaultKeys
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

    private var lastAutoKick = 0L

    init {
        engine.bootstrap()
        scheduleAutopilot()
    }

    /**
     * Called every time the app comes to the front. If autopilot is on and the last shift was
     * more than ten minutes ago, the team starts working without being asked.
     */
    fun onAppForeground() {
        val current = store.snapshot()
        if (!current.settings.autopilot) return
        if (!vault.load().hasNim) return
        val now = System.currentTimeMillis()
        if (now - current.settings.lastShiftAt < 10 * 60_000L) return
        if (now - lastAutoKick < 60_000L) return
        lastAutoKick = now
        runShift()
    }

    // ---------- setup ----------

    fun saveKeys(newKeys: VaultKeys) {
        vault.save(newKeys)
        val reloaded = vault.load()
        _keys.value = reloaded
        _notice.value = if (reloaded == newKeys) {
            "Keys saved to the encrypted vault, with a backup copy."
        } else {
            "The vault could not be read back after saving. Try again before closing the app."
        }
    }

    fun updateSettings(settings: Settings) {
        store.update { it.copy(settings = settings) }
    }

    fun setAutopilot(enabled: Boolean) {
        store.update {
            it.copy(settings = it.settings.copy(autopilot = enabled, autopilotDefaulted = true))
        }
        if (enabled) {
            scheduleAutopilot()
            onAppForeground()
        }
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
        engine.bootstrap()
        val stateNow = store.snapshot()
        store.update { it.copy(settings = it.settings.copy(lastShiftAt = System.currentTimeMillis())) }
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

    /** Delete a bot. Its past messages stay as history; a new lead starts if the team empties. */
    fun deleteAgent(id: String) {
        val stateNow = store.snapshot()
        val target = stateNow.agent(id) ?: return
        val remaining = stateNow.agents.filterNot { it.id == id }

        if (remaining.isEmpty()) {
            store.update {
                it.copy(
                    agents = emptyList(),
                    memory = it.memory.filterNot { entry -> entry.agentId == id },
                    automations = it.automations.filterNot { automation -> automation.agentId == id }
                )
            }
            engine.bootstrap()
            _notice.value = "${target.name} deleted. A new lead started so the company keeps running."
            return
        }

        val promoted = if (remaining.none { it.primary }) {
            val lead = remaining.first()
            remaining.map { if (it.id == lead.id) it.copy(primary = true) else it }
        } else {
            remaining
        }
        store.update { state ->
            state.copy(
                agents = promoted,
                memory = state.memory.filterNot { entry -> entry.agentId == id },
                automations = state.automations.filterNot { automation -> automation.agentId == id }
            )
        }
        _notice.value = "${target.name} deleted, along with its memory."
    }

    /**
     * A human message is a real work order: the bot can call tools (hire, add leads, create a
     * product, proofread) while it answers, not just talk about it.
     */
    fun messageAgent(agentId: String, text: String) {
        if (text.isBlank()) return
        val agent = store.snapshot().agent(agentId) ?: store.snapshot().orchestrator ?: return
        engine.say(agent, text, fromUser = true)
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                val reply = engine.converse(agent, text)
                engine.say(agent, reply.ifBlank { "..." })
            }.onFailure { engine.say(agent, "I hit an error: ${it.message}", kind = MessageKind.SYSTEM) }
            _busy.value = false
        }
    }

    /** Command bar: talk to the company lead. */
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

    fun moveProspect(id: String, stage: ProspectStage) {
        store.update { state ->
            state.copy(prospects = state.prospects.map { if (it.id == id) it.copy(stage = stage) else it })
        }
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
