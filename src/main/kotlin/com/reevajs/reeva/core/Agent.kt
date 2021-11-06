package com.reevajs.reeva.core

import com.reevajs.reeva.core.errors.DefaultErrorReporter
import com.reevajs.reeva.core.errors.StackTraceFrame
import com.reevajs.reeva.core.lifecycle.*
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.realm.RealmExtension
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.JSGlobalObject
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.utils.Result
import java.nio.ByteOrder
import java.util.function.Function

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

    val microtaskQueue = MicrotaskQueue()

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

    fun compile(realm: Realm, sourceInfo: SourceInfo): Result<ParsingError, Executable> {
        return if (sourceInfo.isModule) {
            compileModule(realm, sourceInfo).cast()
        } else compileScript(realm, sourceInfo).cast()
    }

    fun compileScript(realm: Realm, sourceInfo: SourceInfo): Result<ParsingError, ScriptRecord> {
        return ScriptRecord.parseScript(realm, sourceInfo)
    }

    fun compileModule(realm: Realm, sourceInfo: SourceInfo): Result<ParsingError, ModuleRecord> {
        return SourceTextModuleRecord.parseModule(realm, sourceInfo)
    }

    internal fun <T> inCallScope(function: JSFunction, block: () -> T): T {
        callStack.add(StackTraceFrame(function, function.debugName, isNative = function is JSNativeFunction))
        return try {
            block()
        } finally {
            callStack.removeLast()
            if (callStack.isEmpty())
                microtaskQueue.checkpoint()
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
