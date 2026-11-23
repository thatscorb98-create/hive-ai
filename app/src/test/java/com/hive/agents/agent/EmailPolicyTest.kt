package com.hive.agents.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailPolicyTest {

    @Test
    fun rejectsNonAddresses() {
        assertFalse(EmailPolicy.looksLikeAddress(""))
        assertFalse(EmailPolicy.looksLikeAddress("hello"))
        assertFalse(EmailPolicy.looksLikeAddress("a@b"))
        assertFalse(EmailPolicy.looksLikeAddress("a b@example.com"))
        assertFalse(EmailPolicy.looksLikeAddress("a@@example.com"))
        assertFalse(EmailPolicy.looksLikeAddress("a@example."))
    }

    @Test
    fun acceptsOrdinaryAddresses() {
        assertTrue(EmailPolicy.looksLikeAddress("owner@example.com"))
        assertTrue(EmailPolicy.looksLikeAddress("first.last+tag@sub.example.co.uk"))
    }

    @Test
    fun blocklistIsCaseInsensitiveAndSeparatorTolerant() {
        val list = "owner@example.com, BOSS@EXAMPLE.COM\nsales@example.com; old@example.com"
        assertTrue(EmailPolicy.isBlocked("boss@example.com", list))
        assertTrue(EmailPolicy.isBlocked(" SALES@example.com ", list))
        assertFalse(EmailPolicy.isBlocked("new@example.com", list))
        assertFalse(EmailPolicy.isBlocked("", list))
    }
}
