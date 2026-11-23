package com.hive.agents.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HiveStateTest {

    private fun prospect(stage: ProspectStage, paid: Boolean, cents: Long) = Prospect(
        company = "Test Co",
        stage = stage,
        paid = paid,
        valueCents = cents
    )

    @Test
    fun salesAndRevenueOnlyCountPaidWonDeals() {
        val state = HiveState(
            prospects = listOf(
                prospect(ProspectStage.WON, paid = true, cents = 10_000),
                prospect(ProspectStage.WON, paid = false, cents = 50_000),
                prospect(ProspectStage.PITCHED, paid = true, cents = 30_000),
                prospect(ProspectStage.LOST, paid = true, cents = 40_000)
            )
        )

        assertEquals(1, state.salesMade)
        assertEquals(10_000, state.revenueCents)
    }

    @Test
    fun emptyPipelineIsZero() {
        assertEquals(0, HiveState().salesMade)
        assertEquals(0, HiveState().revenueCents)
    }
}
