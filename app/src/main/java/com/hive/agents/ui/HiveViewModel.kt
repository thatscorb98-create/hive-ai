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
import com.hive.agents.data.TaskStatus
import com.hive.agents.data.VaultKeys
import com.hive.agents.service.AutopilotWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
    private val shifts = graph.shifts

    val state: StateFlow<HiveState> = store.state
    val pendingApproval = approvals.pending
    val pendingBrowser = BrowserHub.pendingOpen

    private val _keys = MutableStateFlow(vault.load())
    val keys: StateFlow<VaultKeys> = _keys.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _loopOn = MutableStateFlow(false)
    val loopOn: StateFlow<Boolean> = _loopOn.asStateFlow()

    private val _notice = MutableStateFlow("")
    val notice: StateFlow<String> = _notice.asStateFlow()

    private val _inbox = MutableStateFlow<List<InboxMail>>(emptyList())
    val inbox: StateFlow<List<InboxMail>> = _inbox.asStateFlow()

    private val _selectedMission = MutableStateFlow<String?>(null)
    val selectedMission: StateFlow<String?> = _selectedMission.asStateFlow()

    private var loopJob: Job? = null

    private companion object {
        const val LOOP_PAUSE_MS = 45_000L
        const val FAIL_PAUSE_MS = 5 * 60_000L
    }

    init {
        engine.bootstrap()
        if (store.snapshot().settings.autopilot) scheduleAutopilot()
        viewModelScope.launch {
            store.error.collect { message ->
                if (message != null) _notice.value = message
            }
        }
        vault.lastError?.let { _notice.value = it }
    }

    /**
     * Autopilot is a loop, not one shift. While the app process is alive the company keeps
     * moving through the sales pipeline: research, sample, outreach, follow-up, payment,
     * delivery. WorkManager adds a shift every 15 minutes when the app is not open.
     */
    fun onAppForeground() {
        val current = store.snapshot()
        if (!current.settings.autopilot) return
        if (!vault.load().hasNim) return

        // The 2.12 build could finish a shift without hiring because its model call failed.
        // On the next launch, give that company its core crew before the loop starts.
        if (current.agents.size <= 1 && current.settings.lastShiftAt > 0) {
            viewModelScope.launch { engine.ensureCoreTeam() }
        }
        startLoop()
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = viewModelScope.launch {
            _loopOn.value = true
            try {
                while (isActive) {
                    val current = store.snapshot()
                    if (!current.settings.autopilot || !vault.load().hasNim) break
                    val healthy = runShiftInternal()
                    delay(if (healthy) LOOP_PAUSE_MS else FAIL_PAUSE_MS)
                }
            } finally {
                _loopOn.value = false
            }
        }
    }

    // ---------- setup ----------

    fun saveKeys(newKeys: VaultKeys) {
        if (vault.lastError != null && newKeys.nimApiKey.isBlank()) {
            _notice.value = "The vault could not be read on this device. Re-enter the NVIDIA key before saving, so the existing keys are not replaced with blanks."
            return
        }
        val saved = runCatching { vault.save(newKeys) }
        if (saved.isFailure) {
            _notice.value = vault.lastError ?: "Keys were not saved: ${saved.exceptionOrNull()?.message}"
            return
        }
        val reloaded = vault.load()
        _keys.value = reloaded
        _notice.value = if (reloaded == newKeys) {
            "Keys saved to the encrypted vault, with a backup copy."
        } else {
            vault.lastError ?: "The vault could not be read back after saving. Try again before closing the app."
        }
        if (reloaded.hasNim && store.snapshot().settings.autopilot) startLoop()
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
        } else {
            loopJob?.cancel()
            loopJob = null
            _loopOn.value = false
            // Turning autopilot off must also stop the 15-minute worker, not just the in-app loop.
            runCatching {
                WorkManager.getInstance(getApplication()).cancelUniqueWork(AutopilotWorker.NAME)
            }
        }
    }

    fun dismissNotice() {
        _notice.value = ""
    }

    /** Flush queued persistence work when the app leaves the foreground. */
    fun flushState() {
        viewModelScope.launch(Dispatchers.IO) { store.flush() }
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

    /** Human-triggered shift. Same code path the worker and the loop use. */
    fun runShift() {
        if (!vault.load().hasNim) {
            _notice.value = "Add your NVIDIA API key in Settings before running a shift."
            return
        }
        viewModelScope.launch { runShiftInternal() }
    }

    /**
     * Shifts are serialized here. A manual tap while the loop is running now waits for the current
     * shift instead of silently doing nothing, and a failed shift is never reported as healthy.
     */
    private suspend fun runShiftInternal(): Boolean = shifts.run {
        engine.bootstrap()
        _busy.value = true
        try {
            val stateNow = store.snapshot()
            val existing = stateNow.missions.firstOrNull {
                it.status == MissionStatus.PLANNED || it.status == MissionStatus.RUNNING
            }
            val mission = existing ?: nextCompanyMission(stateNow)
            store.update {
                it.copy(
                    settings = it.settings.copy(lastShiftAt = System.currentTimeMillis()),
                    missions = if (existing == null) it.missions + mission else it.missions
                )
            }
            engine.runMission(mission.id)
            val tasks = store.snapshot().tasks.filter { it.missionId == mission.id }
            val healthy = tasks.isEmpty() || tasks.any { it.status == TaskStatus.DONE }
            if (!healthy && tasks.isNotEmpty()) {
                _notice.value = "Shift finished with failures. Open Chat to read what each bot reported."
            }
            healthy
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _notice.value = error.message ?: "Shift failed"
            false
        } finally {
            _busy.value = false
        }
    }

    /**
     * Picks the next useful stage of the company loop instead of inventing a new mission each
     * time. Paid work comes first; then missing research; then an offer; then samples; then
     * outreach; then follow-up; then a fresh lead hunt.
     */
    private fun nextCompanyMission(state: HiveState): Mission {
        val paidWaiting = state.prospects.count { it.paid && !it.delivered }
        val noEmail = state.prospects.count {
            it.email.isBlank() && it.stage != ProspectStage.LOST && it.stage != ProspectStage.WON
        }
        val ready = state.prospects.count {
            it.email.isNotBlank() && (it.stage == ProspectStage.NEW || it.stage == ProspectStage.QUALIFIED)
        }
        val pitched = state.prospects.count {
            it.stage == ProspectStage.PITCHED || it.stage == ProspectStage.NEGOTIATING
        }
        val samples = state.deliverables.count { it.isSample }

        val title: String
        val brief: String
        when {
            paidWaiting > 0 -> {
                title = "Deliver paid work"
                brief = "$paidWaiting customer(s) have paid. Do the work, save it, and mark each one delivered with deliver_work. A paying customer comes before a new lead."
            }
            noEmail > 0 -> {
                title = "Research the pipeline"
                brief = "$noEmail lead(s) are missing contact details. Research each real business with search_web and fetch_page, find the right person and a verified email, then update the pipeline with add_leads. Never guess an address."
            }
            state.products.isEmpty() -> {
                title = "Create something to sell"
                brief = "Package one concrete offer for Local Reach Workshop: a digital product or a done-for-you service with a clear price and outcome. Save it with create_product."
            }
            samples < 3 -> {
                title = "Make samples buyers can see"
                brief = "Make three samples of the work the company sells: a sample audit, a sample post set and a sample landing page. Save each with create_sample so a buyer can judge the quality before paying. If there is no showcase bot yet, hire one first or make the samples yourself."
            }
            ready > 0 -> {
                title = "Contact qualified leads"
                brief = "$ready qualified lead(s) have an email. Send a short, specific outreach email about what the company can do for them, then update the pipeline."
            }
            pitched > 0 -> {
                title = "Follow up and close"
                brief = "$pitched lead(s) have been pitched. Follow up with a useful next step, ask for the sale, and record payment with record_sale when it lands."
            }
            else -> {
                title = "Find and qualify real local leads"
                brief = "Search for real local businesses that need what Local Reach Workshop sells. Verify each one, add the best leads with add_leads, and note what the company can do for them."
            }
        }
        return Mission(id = UUID.randomUUID().toString(), title = title, brief = brief)
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
            try {
                shifts.run { engine.runMission(id) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _notice.value = error.message ?: "Mission failed"
            } finally {
                _busy.value = false
            }
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
            try {
                // An order to the boss is a mission for the team, not a one-bot chat reply.
                // Talking to a specialist still stays a direct conversation.
                val reply = if (agent.primary && vault.load().hasNim) {
                    engine.takeOrder(agent, text)
                } else {
                    engine.converse(agent, text)
                }
                engine.say(agent, reply.ifBlank { "..." })
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                engine.say(agent, "I hit an error: ${error.message}", kind = MessageKind.SYSTEM)
            } finally {
                _busy.value = false
            }
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

    fun moveProspect(id: String, stage: ProspectStage) {
        val now = System.currentTimeMillis()
        store.update { state ->
            state.copy(prospects = state.prospects.map {
                if (it.id == id) {
                    it.copy(
                        stage = stage,
                        updatedAt = now
                    )
                } else {
                    it
                }
            })
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
            val result = withContext(Dispatchers.IO) {
                runCatching { mail.inbox(agent.email) }
            }
            result.exceptionOrNull()?.let { _notice.value = "Inbox: ${it.message}" }
            val messages = result.getOrDefault(emptyList())
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
