package me.mattco.reeva

import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSGlobalObject
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Reeva {
    private val agents = ThreadLocal<Agent>()
    internal val allAgents = mutableListOf<Agent>()

    @JvmStatic
    val activeAgent: Agent
        get() = agents.get()

    internal var running = false
        private set

    val threadPool: ExecutorService = Executors.newFixedThreadPool(10)

    val PRINT_PARSE_NODES = System.getProperty("reeva.debugParseNodes")?.toBoolean() ?: false
    var EMIT_CLASS_FILES = true
        internal set

    @JvmStatic
    fun teardown() {
        running = false
        threadPool.shutdownNow()
    }

    @JvmStatic
    fun makeRealm() = Realm().apply {
        ensureGloballyInitialized()
        setGlobalObject(JSGlobalObject.create(this))

    }

    @JvmStatic
    fun makeRealm(globalObjectProvider: (Realm) -> JSObject) = Realm().apply {
        ensureGloballyInitialized()
        setGlobalObject(globalObjectProvider(this))
    }

    @JvmStatic
    fun setAgent(agent: Agent) {
        agents.set(agent)
    }

    @JvmStatic
    fun setup() {
        Realm.setupSymbols()
        running = true
    }

    data class Result(val value: JSValue, val isError: Boolean)
}
