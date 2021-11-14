package com.reevajs.reeva.core

import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.core.errors.DefaultErrorReporter
import com.reevajs.reeva.core.lifecycle.*
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.realm.RealmExtension
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.parsing.lexer.SourceLocation
import com.reevajs.reeva.runtime.JSGlobalObject
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.Result
import java.nio.ByteOrder
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Function
import kotlin.concurrent.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
class Agent {
    private val executionContextStack = ArrayDeque<ExecutionContext>()
    private var pendingSourceLocation: SourceLocation? = null

    val runningExecutionContext: ExecutionContext
        get() = executionContextStack.last()

    private val executionLock = ReentrantLock()

    @Volatile
    private var objectId = 0

    @Volatile
    private var shapeId = 0

    var printAST = false
    var printIR = false

    var errorReporter = DefaultErrorReporter(System.out)

    var hostHooks = HostHooks()

    val byteOrder: ByteOrder = ByteOrder.nativeOrder()
    val isLittleEndian: Boolean
        get() = byteOrder == ByteOrder.LITTLE_ENDIAN
    val isBigEndian: Boolean
        get() = byteOrder == ByteOrder.BIG_ENDIAN

    val microtaskQueue = MicrotaskQueue()

    fun setPendingSourceLocation(location: SourceLocation?) {
        pendingSourceLocation = location
    }

    fun contextStack(): List<ExecutionContext> = executionContextStack.toList()

    fun pushExecutionContext(context: ExecutionContext) {
        executionContextStack.addLast(context)
    }

    fun popExecutionContext() {
        executionContextStack.removeLast()
    }

    @ECMAImpl("9.4.1", name = "GetActiveScriptOrModule")
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
        extensions: Map<Any, RealmExtension> = emptyMap(),
        globalObjProducer: Function<Realm, JSObject> = Function { hostHooks.initializeHostDefinedGlobalObject(it) },
    ): Realm {
        val realm = hostHooks.initializeHostDefinedRealm(extensions, globalObjProducer)
        return realm
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

    fun nextShapeId() = shapeId++

    fun hasLock() = executionLock.isHeldByCurrentThread

    fun lock() {
        executionLock.lock()
    }

    fun unlock() {
        executionLock.unlock()
    }

    fun <T> withLock(action: () -> T): T {
        contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
        return executionLock.withLock(action)
    }

    companion object {
        private val agents = ThreadLocal<Agent>()

        @JvmStatic
        val activeAgent: Agent
            get() = agents.get()

        @JvmStatic
        fun setAgent(agent: Agent) {
            agents.set(agent)
        }
    }
}
