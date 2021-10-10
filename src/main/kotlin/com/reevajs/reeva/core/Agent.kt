package com.reevajs.reeva.core

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.ast.ScriptNode
import com.reevajs.reeva.core.lifecycle.Executable
import com.reevajs.reeva.core.lifecycle.ExecutionResult
import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.interpreter.transformer.Transformer
import com.reevajs.reeva.interpreter.transformer.TransformerResult
import com.reevajs.reeva.parsing.Parser
import com.reevajs.reeva.parsing.ParsingResult
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.primitives.JSTrue
import com.reevajs.reeva.utils.expect
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

    fun parse(executable: Executable): ParsingResult {
        val result = Parser(executable).parseScript()
        if (result is ParsingResult.Success) {
            expect(result.node is ScriptNode)
            executable.script = result.node
        }
        return result
    }

    fun transform(executable: Executable): TransformerResult {
        val result = Transformer(executable).transform()
        if (result is TransformerResult.Success)
            executable.ir = result.ir
        return result
    }

    fun run(source: String, realm: Realm): ExecutionResult {
        return run(Executable(null, source), realm)
    }

    fun run(executable: Executable, realm: Realm): ExecutionResult {
        when (val result = parse(executable)) {
            is ParsingResult.InternalError -> return ExecutionResult.InternalError(executable, result.cause)
            is ParsingResult.ParseError ->
                return ExecutionResult.ParseError(executable, result.reason, result.start, result.end)
        }

        if (printAST) {
            executable.script!!.debugPrint()
            println("\n")
        }

        when (val result = transform(executable)) {
            is TransformerResult.InternalError -> return ExecutionResult.InternalError(executable, result.cause)
            is TransformerResult.UnsupportedError ->
                return ExecutionResult.InternalError(executable, NotImplementedError(result.message))
        }

        if (printIR) {
            // TODO
            // IrPrinter(executable.ir!!).print()
            println("\n")
        }

        // TODO
        return ExecutionResult.Success(executable, JSTrue)
        // return try {
        //     val function = Interpreter.wrap(ir, realm, realm.globalEnv)
        //     ExecutionResult.Success(function.call(realm.globalObject, emptyList()))
        // } catch (e: ThrowException) {
        //     ExecutionResult.RuntimeError(realm, e.value)
        // } catch (e: Throwable) {
        //     ExecutionResult.InternalError(e)
        // }
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
