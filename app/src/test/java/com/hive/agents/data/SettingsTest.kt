package com.hive.agents.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun emailApprovalIsOnForAnOlderStateFile() {
        val decoded = json.decodeFromString<Settings>("{}")
        assertTrue(decoded.requireApprovalForEmail)
        assertEquals("", decoded.emailBlocklist)
    }

    @Test
    fun blocklistRoundTrips() {
        val settings = Settings(requireApprovalForEmail = false, emailBlocklist = "a@example.com")
        val decoded = json.decodeFromString<Settings>(json.encodeToString(settings))
        assertEquals("a@example.com", decoded.emailBlocklist)
        assertEquals(false, decoded.requireApprovalForEmail)
    }
}