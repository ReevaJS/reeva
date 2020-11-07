package me.mattco.reeva

import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.modules.ModuleResolver
import me.mattco.reeva.core.tasks.EvaluationTask
import me.mattco.reeva.runtime.JSValue
import java.io.File

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
    @JvmOverloads
    fun makeRealm(moduleResolver: ModuleResolver? = null) = Realm(moduleResolver)

    @JvmStatic
    @JvmOverloads
    fun evaluate(script: String, realm: Realm = makeRealm()): Result {
        return getAgent().runTask(EvaluationTask(script, realm, false))
    }

    @JvmStatic
    @JvmOverloads
    fun evaluateModule(module: File, realm: Realm = makeRealm()): Result {
        if (realm.moduleResolver == null)
            realm.moduleResolver = ModuleResolver(ModuleResolver.DefaultPathResolver(module.parentFile, null))

        return getAgent().runTask(EvaluationTask(module.readText(), realm, true))
    }

    @JvmStatic
    @JvmOverloads
    fun evaluateModule(module: String, realm: Realm = makeRealm()): Result {
        if (realm.moduleResolver == null)
            throw IllegalStateException("Reeva cannot evaluate text as a module with a realm that does not have a ModuleResolver.")

        return getAgent().runTask(EvaluationTask(module, realm, true))
    }

    @JvmStatic
    fun evaluateModule(module: String, moduleResolver: ModuleResolver): Result {
        val realm = makeRealm(moduleResolver)
        return getAgent().runTask(EvaluationTask(module, realm, true))
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
