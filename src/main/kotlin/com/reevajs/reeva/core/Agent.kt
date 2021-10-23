package com.reevajs.reeva.core

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.ast.RootNode
import com.reevajs.reeva.core.lifecycle.Executable
import com.reevajs.reeva.core.lifecycle.ExecutionResult
import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.interpreter.transformer.IRPrinter
import com.reevajs.reeva.interpreter.transformer.IRValidator
import com.reevajs.reeva.interpreter.transformer.Transformer
import com.reevajs.reeva.interpreter.transformer.TransformerResult
import com.reevajs.reeva.parsing.Parser
import com.reevajs.reeva.parsing.ParsingResult
import com.reevajs.reeva.runtime.functions.JSFunction
import java.io.File
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
    val activeFunction: JSFunction
        get() = callStack.last()

    val microtaskQueue = MicrotaskQueue(this)

    init {
        Reeva.allAgents.add(this)
    }

    fun parse(executable: Executable): ParsingResult {
        val parser = Parser(executable)

        val result = when {
            executable.file == null -> parser.parseScript()
            executable.file.extension == "js" -> parser.parseScript()
            executable.file.extension == "mjs" -> parser.parseModule()
            else -> throw IllegalArgumentException("Unknown file extension: ${executable.file.extension}")
        }

        if (result is ParsingResult.Success) {
            executable.rootNode = result.node as RootNode
        }
        return result
    }

    fun transform(executable: Executable): TransformerResult {
        val result = Transformer(executable).transform()
        if (result is TransformerResult.Success) {
            executable.functionInfo = result.ir
            // Let the script get garbage collected
            executable.rootNode = null
        }
        return result
    }

    fun run(source: String, realm: Realm): ExecutionResult {
        return run(Executable(realm, null, source))
    }

    fun run(file: File, realm: Realm): ExecutionResult {
        return run(Executable(realm, file, file.readText()))
    }

    fun run(executable: Executable): ExecutionResult {
        when (val result = parse(executable)) {
            is ParsingResult.InternalError -> return ExecutionResult.InternalError(executable, result.cause)
            is ParsingResult.ParseError ->
                return ExecutionResult.ParseError(executable, result.reason, result.start, result.end)
        }

        if (printAST) {
            executable.rootNode!!.debugPrint()
            println("\n")
        }

        when (val result = transform(executable)) {
            is TransformerResult.InternalError -> return ExecutionResult.InternalError(executable, result.cause)
            is TransformerResult.UnsupportedError ->
                return ExecutionResult.InternalError(executable, NotImplementedError(result.message))
        }

        if (printIR) {
            IRPrinter(executable).print()
            println("\n")
        }

        IRValidator(executable.functionInfo!!.ir).validate()

        return try {
            val function = Interpreter.wrap(executable, executable.realm.globalEnv)
            ExecutionResult.Success(executable, function.call(executable.realm.globalObject, emptyList()))
        } catch (e: ThrowException) {
            ExecutionResult.RuntimeError(executable, e.value)
        } catch (e: Throwable) {
            ExecutionResult.InternalError(executable, e)
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
