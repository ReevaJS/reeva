package me.mattco.reeva.core

import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.ScriptNode
import me.mattco.reeva.interpreter.ExecutionResult
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.interpreter.transformer.Transformer
import me.mattco.reeva.interpreter.transformer.opcodes.IrPrinter
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.parser.ParsingResult
import me.mattco.reeva.runtime.functions.JSFunction
import java.nio.ByteOrder

class Agent {
    @Volatile
    private var objectId = 0
    @Volatile
    private var shapeId = 0

    var printAST = false
    var printIR = false

    var hostHooks = HostHooks()

    val byteOrder: ByteOrder = ByteOrder.nativeOrder()
    val isLittleEndian: Boolean
        get() = byteOrder == ByteOrder.LITTLE_ENDIAN
    val isBigEndian: Boolean
        get() = byteOrder == ByteOrder.BIG_ENDIAN

    private val pendingMicrotasks = ArrayDeque<() -> Unit>()
    internal val callStack = ArrayDeque<JSFunction>()

    init {
        Reeva.allAgents.add(this)
    }

    fun run(script: String, realm: Realm): ExecutionResult {
        val ast = when (val astResult = Parser(script).parseScript()) {
            is ParsingResult.InternalError -> return ExecutionResult.InternalError(astResult.cause)
            is ParsingResult.ParseError -> return ExecutionResult.ParseError(
                astResult.reason,
                astResult.start,
                astResult.end
            )
            is ParsingResult.Success -> astResult.node as ScriptNode
        }

        if (printAST) {
            ast.debugPrint()
            println("\n")
        }

        val ir = try {
            Transformer().transform(ast)
        } catch (e: Throwable) {
            return ExecutionResult.InternalError(e)
        }

        if (printIR) {
            IrPrinter(ir).print()
            println("\n")
        }

        return try {
            val function = Interpreter.wrap(ir, realm, realm.globalEnv)
            ExecutionResult.Success(function.call(realm.globalObject, emptyList()))
        } catch (e: ThrowException) {
            ExecutionResult.RuntimeError(realm, e.value)
        } catch (e: Throwable) {
            ExecutionResult.InternalError(e)
        }
    }

    internal fun <T> inCallScope(function: JSFunction, block: () -> T): T {
        callStack.add(function)
        return try {
            block()
        } finally {
            callStack.removeLast()
            if (callStack.isEmpty())
                processMicrotasks()
        }
    }

    fun addMicrotask(task: () -> Unit) {
        pendingMicrotasks.addFirst(task)
    }

    fun processMicrotasks() {
        while (pendingMicrotasks.isNotEmpty() && Reeva.running)
            pendingMicrotasks.removeLast()()
    }

    internal fun nextObjectId() = objectId++
    internal fun nextShapeId() = shapeId++
}
