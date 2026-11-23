package com.hive.agents.agent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftCoordinatorTest {

    @Test
    fun tryStartIsExclusiveUntilFinish() {
        val coordinator = ShiftCoordinator()
        assertTrue(coordinator.tryStart())
        assertTrue(coordinator.active.value)
        assertFalse(coordinator.tryStart())
        coordinator.finish()
        assertFalse(coordinator.active.value)
        assertTrue(coordinator.tryStart())
        coordinator.finish()
    }

    @Test
    fun runSerializesTwoShifts() = runTest {
        val coordinator = ShiftCoordinator()
        val order = mutableListOf<Int>()
        coroutineScope {
            launch {
                coordinator.run {
                    order += 1
                    delay(100)
                    order += 2
                }
            }
            launch {
                coordinator.run {
                    order += 3
                }
            }
        }
        assertEquals(listOf(1, 2, 3), order)
        assertFalse(coordinator.active.value)
    }
}