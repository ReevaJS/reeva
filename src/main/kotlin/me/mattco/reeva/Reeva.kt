package me.mattco.reeva

import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.tasks.EvaluationTask
import me.mattco.reeva.runtime.JSValue

object Reeva {
    // Used to ensure names of various things are unique
    @Volatile
    @JvmStatic
    internal var objectCount = 0

    @JvmStatic
    fun setup() {
        Realm.setupSymbols()
    }

    @JvmStatic
    fun getAgent() = Agent.activeAgent

    @JvmStatic
    fun makeRealm() = Realm()

    @JvmStatic
    @JvmOverloads
    fun evaluate(script: String, realm: Realm = makeRealm()): Result {
        return getAgent().runTask(EvaluationTask(script, realm))
    }

    @JvmStatic
    fun teardown() {
        Agent.activeAgentList.forEach(Agent::teardown)
    }

    fun <T> with(realm: Realm = makeRealm(), block: () -> T): T {
        val context = ExecutionContext(realm, null)
        Agent.pushContext(context)
        val result = block()
        Agent.popContext()
        return result
    }

    data class Result(val value: JSValue, val isError: Boolean)
}
