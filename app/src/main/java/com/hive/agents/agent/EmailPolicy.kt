package com.hive.agents.agent

/** Pure email checks, kept out of the registry so they can be tested without Android. */
internal object EmailPolicy {

    fun looksLikeAddress(address: String): Boolean {
        val clean = address.trim()
        if (clean.length < 3 || clean.any { it.isWhitespace() }) return false
        val at = clean.indexOf('@')
        if (at <= 0 || at == clean.lastIndex) return false
        if (clean.indexOf('@', at + 1) >= 0) return false
        val domain = clean.substring(at + 1)
        return domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.')
    }

    /** True when the address is on the owner's do-not-email list. */
    fun isBlocked(address: String, blocklist: String): Boolean {
        val target = address.trim().lowercase()
        if (target.isBlank()) return false
        return blocklist
            .split(',', '\n', ';')
            .map { it.trim().lowercase() }
            .any { it.isNotBlank() && it == target }
    }
}