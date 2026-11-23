package com.hive.agents.data

/**
 * Bridge between the network client and the store.
 *
 * [com.hive.agents.net.NimClient] has no reference to [HiveStore], and the app builds a client in
 * more than one place, so calls are reported here and the store installs the recorder once at
 * construction. Reporting never throws into a model call.
 */
object ModelUsage {
    @Volatile
    private var recorder: ((inputTokens: Int, outputTokens: Int) -> Unit)? = null

    fun install(recorder: (inputTokens: Int, outputTokens: Int) -> Unit) {
        this.recorder = recorder
    }

    fun record(inputTokens: Int, outputTokens: Int) {
        runCatching { recorder?.invoke(inputTokens, outputTokens) }
    }
}
