package com.reevajs.reeva.core

import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.core.errors.DefaultErrorReporter
import com.reevajs.reeva.core.lifecycle.Executable
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.utils.expect
import java.nio.ByteOrder
import java.util.concurrent.locks.ReentrantLock

class Agent(
    var printIR: Boolean,
    var printAST: Boolean,
    var canBlock: Boolean,
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
        return executionContextStack.lastOrNull { it.function != null }?.function
    }

    fun getActiveRealm(): Realm {
        return executionContextStack.last().realm
    }

    fun makeRealmAndInitializeExecutionEnvironment() = hostHooks.initializeHostDefinedRealm()

    fun nextObjectId() = objectId++

    fun enter() {
        if (!hasActiveAgent)
            agents.set(this)

        if (canBlock) {
            threadLocks.get().lock()
        } else {
            expect(threadLocks.get().tryLock()) {
                "Non-blocking agent failed to acquire thread lock"
            }
        }
    }

    fun exit() {
        expect(hasActiveAgent) { "Attempt to exit agent without a corresponding enter" }

        val lock = threadLocks.get()
        lock.unlock()

        if (!lock.isHeldByCurrentThread)
            agents.set(null)
    }

    fun <T> withActiveScope(block: Agent.() -> T): T {
        enter()
        return try {
            this.block()
        } finally {
            exit()
        }
    }

    class Builder {
        var printIR = false
        var printAST = false
        var canBlock = true
        var hostHooks = HostHooks()
    }

    companion object {
        private val agents = ThreadLocal<Agent?>()
        private val threadLocks = object : ThreadLocal<ReentrantLock>() {
            override fun initialValue() = ReentrantLock()
        }

        @JvmStatic
        val activeAgent: Agent
            get() = agents.get()!!

        @JvmStatic
        val hasActiveAgent: Boolean
            get() = agents.get() != null

        fun build(block: Builder.() -> Unit): Agent {
            val builder = Builder().apply(block)
            return Agent(builder.printIR, builder.printAST, builder.canBlock, builder.hostHooks)
        }
    }
}
