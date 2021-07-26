package com.reevajs.reeva.core

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.ast.ScriptNode
import com.reevajs.reeva.interpreter.ExecutionResult
import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.interpreter.transformer.Transformer
import com.reevajs.reeva.interpreter.transformer.opcodes.IrPrinter
import com.reevajs.reeva.parsing.Parser
import com.reevajs.reeva.parsing.ParsingResult
import com.reevajs.reeva.runtime.functions.JSFunction
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

    internal val callStack = ArrayDeque<JSFunction>()
    val microtaskQueue = MicrotaskQueue(this)

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
                microtaskQueue.checkpoint()
        }
    }

    internal fun nextObjectId() = objectId++
    internal fun nextShapeId() = shapeId++
}
