package com.reevajs.reeva.core

import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.core.errors.DefaultErrorReporter
import com.reevajs.reeva.core.lifecycle.*
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.realm.RealmExtension
import com.reevajs.reeva.parsing.lexer.SourceLocation
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.objects.JSObject
import java.io.Closeable
import java.nio.ByteOrder
import java.util.function.Function
import kotlin.concurrent.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
class Agent(
    var printIR: Boolean,
    var printAST: Boolean,
    var hostHooks: HostHooks,
) {
    private val executionContextStack = ArrayDeque<ExecutionContext>()

    val runningExecutionContext: ExecutionContext
        get() = executionContextStack.last()

    private var envRecordProvider: ExecutionContext? = null

    var activeEnvRecord: EnvRecord
        get() = envRecordProvider!!.envRecord!!
        set(value) {
            envRecordProvider!!.envRecord = value
        }

    @Volatile
    private var objectId = 0

    var errorReporter = DefaultErrorReporter(System.out)

    val byteOrder: ByteOrder = ByteOrder.nativeOrder()
    val isLittleEndian: Boolean
        get() = byteOrder == ByteOrder.LITTLE_ENDIAN
    val isBigEndian: Boolean
        get() = byteOrder == ByteOrder.BIG_ENDIAN

    val microtaskQueue = MicrotaskQueue()

    fun contextStack(): List<ExecutionContext> = executionContextStack.toList()

    fun pushExecutionContext(context: ExecutionContext) {
        executionContextStack.addLast(context)
        if (context.envRecord != null)
            envRecordProvider = context
    }

    fun popExecutionContext() {
        val removed = executionContextStack.removeLast()
        if (removed == envRecordProvider) {
            envRecordProvider = executionContextStack.asReversed().firstOrNull {
                it.envRecord != null
            }
        }
    }

    @ECMAImpl("9.4.1")
    fun getActiveExecutable(): Executable? {
        if (executionContextStack.isEmpty())
            return null

        return executionContextStack.lastOrNull { it.executable != null }?.executable
    }

    fun getActiveFunction(): JSFunction? {
        return executionContextStack.lastOrNull { it.enclosingFunction != null }?.enclosingFunction
    }

    fun getActiveRealm(): Realm {
        return executionContextStack.last().realm
    }

    @JvmOverloads
    fun makeRealm(
        globalObjProducer: Function<Realm, JSObject> = Function { hostHooks.initializeHostDefinedGlobalObject(it) },
    ): Realm {
        return hostHooks.initializeHostDefinedRealm(globalObjProducer)
    }

    fun <T> withRealm(realm: Realm, env: EnvRecord? = null, block: () -> T): T {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        pushExecutionContext(ExecutionContext(null, realm, env, null, null))
        return try {
            block()
        } finally {
            popExecutionContext()
        }
    }

    fun nextObjectId() = objectId++

    fun setActive(active: Boolean) {
        val currentlyActiveAgent = agents.get()
        if (active) {
            require(currentlyActiveAgent == null || currentlyActiveAgent == this) {
                "There is another agent active on this thread"
            }
            agents.set(this)
        } else if (currentlyActiveAgent == this) {
            agents.set(null)
        }
    }

    fun <T> withActiveScope(block: Agent.() -> T): T {
        setActive(true)
        return try {
            this.block()
        } finally {
            setActive(false)
        }
    }

    class Builder {
        var printIR = false
        var printAST = false
        var alive = true
        var hostHooks = HostHooks()
    }

    companion object {
        private val agents = ThreadLocal<Agent>()

        @JvmStatic
        val activeAgent: Agent
            get() = agents.get()

        @JvmStatic
        val hasActiveAgent: Boolean
            get() = agents.get() != null

        fun build(block: Builder.() -> Unit): Agent {
            val builder = Builder().apply(block)
            return Agent(builder.printIR, builder.printAST, builder.hostHooks)
        }
    }
}
