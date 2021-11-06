package com.reevajs.reeva.core

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.errors.DefaultErrorReporter
import com.reevajs.reeva.core.errors.ErrorReporter
import com.reevajs.reeva.core.errors.StackTraceFrame
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.core.lifecycle.*
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.realm.RealmExtension
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.JSGlobalObject
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import java.io.File
import java.nio.ByteOrder
import java.util.function.BiFunction
import java.util.function.Function

sealed class RunResult(val sourceInfo: SourceInfo) {
    class ParseError(sourceInfo: SourceInfo, val error: ParsingError) : RunResult(sourceInfo)

    class RuntimeError(sourceInfo: SourceInfo, val cause: ThrowException) : RunResult(sourceInfo)

    class InternalError(sourceInfo: SourceInfo, val cause: Throwable) : RunResult(sourceInfo)

    class Success(sourceInfo: SourceInfo, val result: JSValue) : RunResult(sourceInfo)

    fun unwrap(errorReporter: ErrorReporter = Agent.activeAgent.errorReporter): JSValue? {
        when (this) {
            is Success -> return result
            is ParseError -> errorReporter.reportParseError(this)
            is RuntimeError -> errorReporter.reportRuntimeError(this)
            is InternalError -> errorReporter.reportInternalError(this)
        }

        return null
    }
}

class Agent {
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

    internal val callStack = ArrayDeque<StackTraceFrame>()
    val activeFunction: JSFunction
        get() = callStack.last().enclosingFunction

    val eventLoop = EventLoop()

    init {
        allAgents.add(this)
    }

    fun makeRealm(extensions: Map<Any, RealmExtension> = emptyMap()) =
        hostHooks.initializeHostDefinedRealm(extensions)

    fun makeRealm(
        extensions: Map<Any, RealmExtension> = emptyMap(),
        globalObjProducer: Function<Realm, JSGlobalObject>,
    ): Realm {
        val realm = Realm(extensions)
        realm.initObjects()
        realm.setGlobalObject(globalObjProducer.apply(realm), hostHooks.initializeHostDefinedGlobalThisValue(realm))
        return realm
    }

    fun run(realm: Realm, file: File): RunResult {
        return run(realm, FileSourceInfo(file))
    }

    fun run(realm: Realm, source: String, isModule: Boolean): RunResult {
        return run(realm, LiteralSourceInfo("<anonymous>", source, isModule))
    }

    fun run(realm: Realm, sourceInfo: SourceInfo): RunResult {
        val result = if (sourceInfo.isModule) {
            SourceTextModuleRecord.parseModule(realm, sourceInfo)
        } else ScriptRecord.parseScript(realm, sourceInfo)

        return if (result.hasError) {
            RunResult.ParseError(sourceInfo, result.error())
        } else result.value().execute()
    }

    internal fun <T> inCallScope(function: JSFunction, block: () -> T): T {
        callStack.add(StackTraceFrame(function, function.debugName, isNative = function is JSNativeFunction))
        return try {
            block()
        } finally {
            callStack.removeLast()
        }
    }

    internal fun nextObjectId() = objectId++

    internal fun nextShapeId() = shapeId++

    companion object {
        private val agents = object : ThreadLocal<Agent>() {
            override fun initialValue() = Agent()
        }

        internal val allAgents = mutableListOf<Agent>()

        @JvmStatic
        val activeAgent: Agent
            get() = agents.get()

        @JvmStatic
        val activeRealm: Realm
            inline get() = activeAgent.activeFunction.realm

        @JvmStatic
        fun setAgent(agent: Agent) {
            agents.set(agent)
        }
    }
}
