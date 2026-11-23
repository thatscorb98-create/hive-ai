package com.hive.agents.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** Something an agent wants to do that needs a human yes. */
data class ApprovalRequest(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String,
    val agentName: String,
    val tool: String,
    val title: String,
    val detail: String,
    val irreversible: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Blocks the agent until the human answers. Background work times out; a card that is never
 * answered is treated as a refusal, never as consent.
 */
class ApprovalBroker {

    private val _pending = MutableStateFlow<ApprovalRequest?>(null)
    val pending: StateFlow<ApprovalRequest?> = _pending.asStateFlow()

    private var waiter: CompletableDeferred<Boolean>? = null

    suspend fun request(req: ApprovalRequest, timeoutMs: Long = 30 * 60 * 1000L): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        synchronized(this) {
            waiter?.complete(false)
            waiter = deferred
            _pending.value = req
        }
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
        synchronized(this) {
            if (waiter === deferred) {
                waiter = null
                _pending.value = null
            }
        }
        return result
    }

    fun resolve(allow: Boolean) {
        synchronized(this) {
            _pending.value = null
            waiter?.complete(allow)
            waiter = null
        }
    }
}
