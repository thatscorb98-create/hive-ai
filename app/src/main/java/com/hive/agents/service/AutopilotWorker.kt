package com.hive.agents.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hive.agents.agent.HiveGraph
import com.hive.agents.data.Mission
import com.hive.agents.data.MissionStatus

/**
 * Keeps the business moving while the app is in the background.
 *
 * Every 15 minutes it picks up the oldest unfinished mission, or starts a new one from the
 * standing goal, and lets the company lead run it. It refuses to do anything without a model key,
 * and it never bypasses approvals: write actions still stop and wait for the human.
 */
class AutopilotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = HiveGraph.get(applicationContext)
        if (!graph.vault.load().hasNim) return Result.success()

        val store = graph.store
        if (!store.snapshot().settings.autopilot) return Result.success()

        val state = store.snapshot()
        store.update { it.copy(settings = it.settings.copy(lastShiftAt = System.currentTimeMillis())) }
        val next = state.missions
            .filter { it.status == MissionStatus.PLANNED || it.status == MissionStatus.RUNNING }
            .minByOrNull { it.createdAt }
            ?: Mission(
                title = state.settings.goal.ifBlank { "Grow the business this week" },
                brief = "Autonomous shift. Find a way to earn for the business and report back."
            ).also { mission ->
                store.update { current -> current.copy(missions = current.missions + mission) }
            }

        return runCatching {
            graph.engine.runMission(next.id)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val NAME = "hive-autopilot"
    }
}
