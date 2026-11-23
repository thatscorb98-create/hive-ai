package com.hive.agents.agent

import com.hive.agents.data.Agent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPolicyTest {

    private fun agent(name: String, role: String, primary: Boolean = false) =
        Agent(name = name, role = role, primary = primary)

    @Test
    fun researchCannotEmailPayOrHire() {
        val allowed = ToolPolicy.allowed(agent("Scout", "Research"))
        assertTrue("search_web" in allowed)
        assertTrue("fetch_page" in allowed)
        assertTrue("add_leads" in allowed)
        assertFalse("send_email" in allowed)
        assertFalse("create_payment_link" in allowed)
        assertFalse("record_sale" in allowed)
        assertFalse("hire_agent" in allowed)
    }

    @Test
    fun salesCanEmailAndRecordSalesButCannotDeliver() {
        val allowed = ToolPolicy.allowed(agent("Closer", "Sales"))
        assertTrue("send_email" in allowed)
        assertTrue("record_sale" in allowed)
        assertTrue("create_payment_link" in allowed)
        assertFalse("deliver_work" in allowed)
        assertFalse("hire_agent" in allowed)
    }

    @Test
    fun proofreaderOnlyProofreads() {
        val allowed = ToolPolicy.allowed(agent("Proof", "Proofreader"))
        assertTrue("proofread" in allowed)
        assertFalse("create_sample" in allowed)
        assertFalse("send_email" in allowed)
        assertFalse("search_web" in allowed)
    }

    @Test
    fun unknownRoleGetsTheSafeGenericSet() {
        val allowed = ToolPolicy.allowed(agent("Temp", "Specialist"))
        assertTrue("search_web" in allowed)
        assertFalse("send_email" in allowed)
        assertFalse("create_payment_link" in allowed)
        assertFalse("hire_agent" in allowed)
    }

    @Test
    fun leadKeepsTheWholeToolset() {
        val lead = agent("Chief", "Founder", primary = true)
        assertEquals(ToolSpecs.all.map { it.name }.toSet(), ToolPolicy.allowed(lead))
    }
}
