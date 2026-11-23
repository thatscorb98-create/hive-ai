package com.hive.agents.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolWebTest {

    @Test
    fun rejectsLoopbackAndPrivateAddresses() {
        assertFalse(ToolWeb.isSafePublicUrl("http://localhost/admin"))
        assertFalse(ToolWeb.isSafePublicUrl("http://127.0.0.1:8080/"))
        assertFalse(ToolWeb.isSafePublicUrl("http://10.0.0.1/"))
        assertFalse(ToolWeb.isSafePublicUrl("http://192.168.1.1/"))
        assertFalse(ToolWeb.isSafePublicUrl("http://169.254.169.254/latest/meta-data"))
        assertFalse(ToolWeb.isSafePublicUrl("http://[::1]/"))
    }

    @Test
    fun rejectsNonHttpSchemes() {
        assertFalse(ToolWeb.isSafePublicUrl("file:///etc/passwd"))
        assertFalse(ToolWeb.isSafePublicUrl("ftp://example.com/file"))
        assertFalse(ToolWeb.isSafePublicUrl("not a url"))
    }

    @Test
    fun acceptsAnOrdinaryPublicHost() {
        assertTrue(ToolWeb.isSafePublicUrl("https://1.1.1.1/path?q=1"))
    }
}