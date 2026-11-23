package com.hive.agents.agent

import com.hive.agents.data.Agent

/**
 * Least privilege for the crew.
 *
 * Every bot used to receive all twenty tools, so a research bot could send email, create a
 * payment link or hire more bots. A prompt-injected page therefore had the whole company's
 * capabilities behind it. Roles now get only the tools their job needs; the lead keeps the full
 * set because it coordinates the company.
 *
 * This is a policy, not a security boundary on its own: [ToolRegistry.call] re-checks it so a
 * model cannot ask for a tool it was never offered.
 */
internal object ToolPolicy {

    private val allToolNames = ToolSpecs.all.map { it.name }.toSet()

    private val research = setOf(
        "search_web", "fetch_page", "add_leads", "add_prospect",
        "remember", "message_teammate",
        "open_website", "read_page", "click_text", "type_into"
    )

    private val sales = setOf(
        "search_web", "fetch_page", "add_leads", "add_prospect",
        "send_email", "create_payment_link", "create_checkout", "record_sale",
        "proofread", "remember", "message_teammate",
        "open_website", "read_page", "click_text", "type_into"
    )

    private val marketing = setOf(
        "search_web", "fetch_page", "create_product", "save_deliverable",
        "create_sample", "proofread", "remember", "message_teammate"
    )

    private val product = setOf(
        "fetch_page", "save_deliverable", "create_sample", "create_product",
        "deliver_work", "proofread", "remember", "message_teammate"
    )

    private val showcase = setOf(
        "search_web", "fetch_page", "create_sample", "save_deliverable",
        "proofread", "remember", "message_teammate"
    )

    private val proofreading = setOf(
        "proofread", "save_deliverable", "remember", "message_teammate"
    )

    private val generic = setOf(
        "search_web", "fetch_page", "remember", "message_teammate",
        "save_deliverable", "proofread"
    )

    fun allowed(agent: Agent): Set<String> {
        if (agent.primary) return allToolNames
        val role = agent.role.lowercase()
        return when {
            role.contains("research") -> research
            role.contains("sales") || role.contains("closer") -> sales
            role.contains("market") || role.contains("content") -> marketing
            role.contains("product") || role.contains("maker") || role.contains("design") -> product
            role.contains("showcase") || role.contains("demo") -> showcase
            role.contains("proof") -> proofreading
            else -> generic
        }
    }
}