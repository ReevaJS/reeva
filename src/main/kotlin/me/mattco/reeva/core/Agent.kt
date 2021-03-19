package me.mattco.reeva.core

import me.mattco.reeva.Reeva
import me.mattco.reeva.pipeline.Pipeline
import me.mattco.reeva.pipeline.PipelineError
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.utils.Result
import me.mattco.reeva.utils.expect
import java.nio.ByteOrder
import java.util.*
import kotlin.collections.ArrayDeque

class Agent {
    // Used to ensure names of various things are unique
    @Volatile
    private var uniqueId = 0

    var printAST = false
    var printIR = false

    val byteOrder: ByteOrder = ByteOrder.nativeOrder()
    val isLittleEndian: Boolean
        get() = byteOrder == ByteOrder.LITTLE_ENDIAN
    val isBigEndian: Boolean
        get() = byteOrder == ByteOrder.BIG_ENDIAN

    private val activeRealms = Stack<Realm>()
    val activeRealm: Realm
        get() = activeRealms.peek()

    private val pendingMicrotasks = ArrayDeque<() -> Unit>()

    init {
        Reeva.allAgents.add(this)
    }

    fun run(script: String, realm: Realm, asModule: Boolean = false): Result<PipelineError, JSValue> {
        return withRealm(realm) {
            Pipeline.interpret(this, script, asModule)
        }
    }

    internal fun addMicrotask(task: () -> Unit) {
        pendingMicrotasks.addFirst(task)
    }

    fun pushRealm(realm: Realm) = apply {
        activeRealms.push(realm)
    }

    fun popRealm() = apply {
        activeRealms.pop()
    }

    fun <T> withRealm(realm: Realm, block: () -> T): T {
        val initialRealmCount = activeRealms.size
        pushRealm(realm)
        return try {
            block()
        } finally {
            popRealm()
            expect(activeRealms.size == initialRealmCount)
        }
    }

    internal fun processMicrotasks() {
        while (pendingMicrotasks.isNotEmpty() && Reeva.running)
            pendingMicrotasks.removeLast()()
    }

    internal fun nextId() = uniqueId++
}
