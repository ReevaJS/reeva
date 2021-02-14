package me.mattco.reeva.core

import me.mattco.reeva.Reeva
import me.mattco.reeva.interpreter.IRInterpreter
import me.mattco.reeva.ir.IRTransformer
import me.mattco.reeva.ir.OpcodePrinter
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.utils.expect
import java.nio.ByteOrder
import java.util.*
import kotlin.collections.ArrayDeque

class Agent {
    // Used to ensure names of various things are unique
    @Volatile
    private var uniqueId = 0

    val byteOrder = ByteOrder.nativeOrder()
    val isLittleEndian: Boolean
        get() = byteOrder == ByteOrder.LITTLE_ENDIAN
    val isBigEndian: Boolean
        get() = byteOrder == ByteOrder.BIG_ENDIAN

    private val activeRealms = Stack<Realm>()
    val activeRealm: Realm
        get() = activeRealms.peek()

    internal val pendingMicrotasks = ArrayDeque<() -> Unit>()

    init {
        Reeva.allAgents.add(this)
    }

    fun run(script: String, realm: Realm): Reeva.Result {
        val ast = Parser(script).parseScript()
        val info = IRTransformer().transform(ast)
        OpcodePrinter.printFunctionInfo(info)
        println("\n")

        val function = IRInterpreter.wrap(info, realm)
        activeRealms.add(realm)
        val result = try {
            function.call(JSArguments(emptyList(), realm.globalObject))
        } catch (e: ThrowException) {
            return Reeva.Result(e.value, true)
        }
        activeRealms.pop()
        expect(activeRealms.isEmpty())
        return Reeva.Result(result, false)
    }

    internal fun addMicrotask(task: () -> Unit) {
        pendingMicrotasks.addFirst(task)
    }

    internal fun pushRealm(realm: Realm) = apply {
        activeRealms.push(realm)
    }

    internal fun popRealm() = apply {
        activeRealms.pop()
    }

    internal fun processMicrotasks() {
        while (pendingMicrotasks.isNotEmpty() && Reeva.running)
            pendingMicrotasks.removeLast()()
    }

    fun <T> withRealm(realm: Realm, block: () -> T): T {
        pushRealm(realm)
        return block().also { popRealm() }
    }

    internal fun nextId() = uniqueId++
}
