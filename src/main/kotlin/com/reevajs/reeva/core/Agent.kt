package com.reevajs.reeva.core

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.errors.DefaultErrorReporter
import com.reevajs.reeva.core.errors.ErrorReporter
import com.reevajs.reeva.core.errors.StackTraceFrame
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.core.lifecycle.FileSourceType
import com.reevajs.reeva.core.lifecycle.LiteralSourceType
import com.reevajs.reeva.core.lifecycle.SourceInfo
import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.transformer.*
import com.reevajs.reeva.parsing.ParsedSource
import com.reevajs.reeva.parsing.Parser
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.utils.Result
import java.io.File
import java.nio.ByteOrder

sealed class RunResult(val sourceInfo: SourceInfo) {
    class ParseError(sourceInfo: SourceInfo, val error: ParsingError) : RunResult(sourceInfo)

    class RuntimeError(sourceInfo: SourceInfo, val cause: ThrowException) : RunResult(sourceInfo)

    class Success(sourceInfo: SourceInfo, val result: JSValue) : RunResult(sourceInfo)

    fun unwrap(errorReporter: ErrorReporter = Reeva.activeAgent.errorReporter): JSValue? {
        return when (this) {
            is Success -> this.result
            is ParseError -> {
                errorReporter.reportParseError(
                    this.sourceInfo,
                    this.error.cause,
                    this.error.start,
                    this.error.end,
                )
                null
            }
            is RuntimeError -> {
                errorReporter.reportRuntimeError(
                    this.sourceInfo,
                    this.cause.value,
                    this.cause.stackTrace,
                )
                null
            }
        }
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

    val microtaskQueue = MicrotaskQueue(this)

    init {
        Reeva.allAgents.add(this)
    }

    fun parse(sourceInfo: SourceInfo): Result<ParsingError, ParsedSource> {
        return Parser(sourceInfo).let {
            if (sourceInfo.type.isModule) it.parseModule() else it.parseScript()
        }.also {
            if (printAST && it.hasValue) {
                it.value().node.debugPrint()
                println('\n')
            }
        }
    }

    fun transform(parsedSource: ParsedSource): TransformedSource {
        return Transformer(parsedSource).transform().also {
            if (printIR) {
                IRPrinter(it).print()
                println('\n')
            }
            IRValidator(it.functionInfo.ir).validate()
        }
    }

    fun interpret(transformedSource: TransformedSource): Result<ThrowException, JSValue> {
        return try {
            Result.success(
                Interpreter.wrap(transformedSource).call(transformedSource.realm.globalObject, emptyList())
            )
        } catch (e: ThrowException) {
            Result.error(e)
        }
    }

    fun compile(transformedSource: TransformedSource): JSFunction {
        TODO()
    }

    fun run(realm: Realm, file: File): RunResult {
        return run(
            SourceInfo(
                realm,
                file.readText(),
                FileSourceType(file),
            )
        )
    }

    fun run(realm: Realm, source: String, isModule: Boolean): RunResult {
        return run(
            SourceInfo(
                realm,
                source,
                LiteralSourceType(isModule, "<anonymous>"),
            )
        )
    }

    fun run(sourceInfo: SourceInfo): RunResult {
        val parseResult = parse(sourceInfo)
        if (parseResult.hasError)
            return RunResult.ParseError(sourceInfo, parseResult.error())

        val transformedSource = transform(parseResult.value())

        val interpretResult = interpret(transformedSource)
        if (interpretResult.hasError)
            return RunResult.RuntimeError(sourceInfo, interpretResult.error())
        return RunResult.Success(sourceInfo, interpretResult.value())
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
}
