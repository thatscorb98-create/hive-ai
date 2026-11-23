package com.hive.agents.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One shift at a time, for the whole process.
 *
 * The foreground loop, the WorkManager worker and an owner order all used to be able to call
 * [TeamEngine.runMission] at once. That produced duplicate tasks, duplicate outreach and two
 * bills for the same work. The mutex here is the authority; [active] is only for the UI.
 *
 * The worker must use [tryStart]/[finish] so it never queues a second shift behind a live one
 * and runs past its own execution window. The foreground paths use [run], which waits.
 */
class ShiftCoordinator {

    private val mutex = Mutex()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        _active.value = true
        try {
            block()
        } finally {
            _active.value = false
        }
    }

    /** Returns false when a shift is already running. Pair a true result with [finish]. */
    fun tryStart(): Boolean {
        if (!mutex.tryLock()) return false
        _active.value = true
        return true
    }

    /** Releases a lock taken by [tryStart]. Only call this after it returned true. */
    fun finish() {
        _active.value = false
        mutex.unlock()
    }
}