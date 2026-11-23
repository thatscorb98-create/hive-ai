package com.hive.agents.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HiveStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val opened = mutableListOf<HiveStore>()

    private fun open(dir: File): HiveStore = HiveStore(dir).also { opened += it }

    @After
    fun tearDown() {
        opened.forEach {
            it.flush()
            it.close()
        }
    }

    @Test
    fun stateSurvivesAReopen() {
        val dir = temp.newFolder("state")
        val store = open(dir)
        store.update { it.copy(messages = listOf(Message(agentId = "a", body = "hello"))) }
        store.flush()

        val reopened = open(dir)
        assertEquals("hello", reopened.snapshot().messages.single().body)
    }

    @Test
    fun messagesAreCappedAndKeepTheNewest() {
        val store = open(temp.newFolder("state"))
        store.update { state ->
            state.copy(messages = (1..700).map { Message(agentId = "a", body = "m$it") })
        }
        store.flush()

        assertEquals(600, store.snapshot().messages.size)
        assertEquals("m700", store.snapshot().messages.last().body)
    }

    @Test
    fun missionsAreCappedWithoutDroppingARunningOne() {
        val store = open(temp.newFolder("state"))
        val finished = (1..205).map { Mission(title = "done$it", status = MissionStatus.DONE) }
        val running = Mission(title = "live", status = MissionStatus.RUNNING)
        store.update { it.copy(missions = finished + running) }
        store.flush()

        val missions = store.snapshot().missions
        assertEquals(200, missions.size)
        assertTrue(missions.any { it.id == running.id })
    }

    @Test
    fun highChurnHistoryIsCapped() {
        val store = open(temp.newFolder("state"))
        store.update { state ->
            state.copy(
                sentMail = (1..350).map {
                    SentMail(to = "a@example.com", subject = "s$it", body = "b")
                },
                suggestions = (1..110).map {
                    Suggestion(title = "t$it", problem = "p")
                }
            )
        }
        store.flush()

        assertEquals(300, store.snapshot().sentMail.size)
        assertEquals(100, store.snapshot().suggestions.size)
    }

    @Test
    fun backupIsUsedWhenTheMainFileIsGone() {
        val dir = temp.newFolder("state")
        val store = open(dir)
        store.update { it.copy(settings = it.settings.copy(businessName = "Backup Co")) }
        store.flush()

        assertTrue(File(dir, "hive-state.json").delete())
        val reopened = open(dir)
        assertEquals("Backup Co", reopened.snapshot().settings.businessName)
    }

    @Test
    fun aFailedWriteIsReportedNotSwallowed() {
        val missing = File(temp.root, "does-not-exist")
        val store = open(missing)
        store.update { it.copy(settings = it.settings.copy(businessName = "Lost Co")) }
        store.flush()

        assertNotNull(store.error.value)
    }
}