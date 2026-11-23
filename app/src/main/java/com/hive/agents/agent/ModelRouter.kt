package com.hive.agents.agent

import com.hive.agents.data.Agent
import com.hive.agents.data.Models
import com.hive.agents.data.Settings

/**
 * Picks a model per bot so the team is not all queued behind one slow model.
 *
 * The lead and product roles get the stronger model because they decide what the company does.
 * Research, sales, marketing and showcase get Lightning: fast and cheap, and they run in
 * parallel. The proofreader gets DeepSeek because rewriting is a narrow, high-volume job. A bot
 * can still override this with its own [Agent.model], and the owner can turn routing off in Setup.
 */
object ModelRouter {

    fun forAgent(agent: Agent, settings: Settings): String {
        if (!settings.routeModels) return settings.model
        if (agent.model.isNotBlank()) return agent.model
        return forRole(agent.role, settings.model)
    }

    fun forRole(role: String, fallback: String): String {
        val r = role.lowercase()
        return when {
            r.contains("proof") -> Models.DEEPSEEK
            r.contains("research") || r.contains("sales") || r.contains("market") ||
                r.contains("showcase") || r.contains("lead gen") -> Models.LIGHTNING
            r.contains("founder") || r.contains("lead") || r.contains("product") ||
                r.contains("studio") || r.contains("design") -> Models.SUPER
            else -> fallback
        }
    }

    fun label(agent: Agent, settings: Settings): String = Models.label(forAgent(agent, settings))
}
