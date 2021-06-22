package me.mattco.reeva.core

import me.mattco.reeva.Reeva
import me.mattco.reeva.interpreter.IRInterpreter
import me.mattco.reeva.ir.IRTransformer
import me.mattco.reeva.ir.opcodes.IrPrinter
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.utils.Result
import me.mattco.reeva.utils.ResultT
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

    fun run(script: String, realm: Realm): ResultT<JSValue> {
        return try {
            val ast = Parser(script).parseScript()
            if (printAST) {
                ast.debugPrint()
                println("\n")
            }

            val ir = IRTransformer().transform(ast)
            if (printIR) {
                IrPrinter.printFunctionInfo(ir)
                println("\n")
            }

            val function = IRInterpreter.wrap(ir, realm)
            return Result.success(function.call(realm.globalObject, emptyList()))
        } catch (e: Throwable) {
            Result.error(e)
        }
    }

    internal fun addMicrotask(task: () -> Unit) {
        pendingMicrotasks.addFirst(task)
    }

    fun <T> withRealm(realm: Realm, block: () -> T): T {
        val initialRealmCount = activeRealms.size
        activeRealms.push(realm)
        return try {
            block()
        } finally {
            activeRealms.pop()
            expect(activeRealms.size == initialRealmCount)
        }
    }

    internal fun processMicrotasks() {
        while (pendingMicrotasks.isNotEmpty() && Reeva.running)
            pendingMicrotasks.removeLast()()
    }

    internal fun nextId() = uniqueId++
}
