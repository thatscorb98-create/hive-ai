package com.hive.agents.agent

import android.content.Context
import com.hive.agents.data.HiveStore
import com.hive.agents.data.KeyVault
import com.hive.agents.net.AgentMailApi
import com.hive.agents.net.NimClient
import com.hive.agents.net.StripeApi

/**
 * One object graph for the whole process.
 *
 * The UI and the background autopilot worker must share the same store, vault and approval
 * broker, otherwise two copies of the state would fight each other and an approval card raised
 * by background work would never reach the screen.
 */
object HiveGraph {

    @Volatile
    private var instance: Graph? = null

    fun get(context: Context): Graph =
        instance ?: synchronized(this) {
            instance ?: Graph(context.applicationContext).also { instance = it }
        }

    class Graph(context: Context) {
        val store = HiveStore(context)
        val vault = KeyVault(context)
        val memory = MemoryService(store)
        val approvals = ApprovalBroker()
        val mail = AgentMailApi(vault)
        val stripe = StripeApi(vault)
        val nim = NimClient(vault)
        val proofreader = Proofreader(nim, store)
        val suggestions = SuggestionBox(store)
        val shifts = ShiftCoordinator()
        val tools = ToolRegistry(store, vault, memory, approvals, mail, stripe, proofreader)
        val engine = TeamEngine(store, nim, tools, memory, suggestions, shifts)
    }
}
