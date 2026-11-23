package com.hive.agents.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AgentMailTest {

    @Test
    fun parsesTheDocumentedV0Fields() {
        val raw = """
            {"messages":[{
              "message_id":"msg_123",
              "timestamp":"2026-11-23T10:00:00Z",
              "from":"lead@example.com",
              "subject":"Hello",
              "preview":"A short preview",
              "text":"The body"
            }]}
        """.trimIndent()

        val mail = AgentMailApi.parseMessages(raw)
        assertEquals(1, mail.size)
        assertEquals("msg_123", mail[0].id)
        assertEquals("lead@example.com", mail[0].from)
        assertEquals("Hello", mail[0].subject)
        assertEquals(Instant.parse("2026-11-23T10:00:00Z").toEpochMilli(), mail[0].receivedAt)
    }

    @Test
    fun fallsBackToLegacyIdAndCreatedAt() {
        val raw = """[{"id":"legacy_1","created_at":1700000000000,"sender":"old@example.com","subject":"Old"}]"""
        val mail = AgentMailApi.parseMessages(raw)
        assertEquals("legacy_1", mail.single().id)
        assertEquals(1700000000000L, mail.single().receivedAt)
        assertEquals("old@example.com", mail.single().from)
    }

    @Test
    fun malformedBodyDoesNotCrash() {
        assertTrue(AgentMailApi.parseMessages("not json").isEmpty())
    }
}
