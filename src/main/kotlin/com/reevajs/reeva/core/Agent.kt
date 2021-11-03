package com.reevajs.reeva.core

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.errors.DefaultErrorReporter
import com.reevajs.reeva.core.errors.ErrorReporter
import com.reevajs.reeva.core.errors.StackTraceFrame
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.core.lifecycle.*
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import java.io.File
import java.nio.ByteOrder

sealed class RunResult(val sourceInfo: SourceInfo) {
    class ParseError(sourceInfo: SourceInfo, val error: ParsingError) : RunResult(sourceInfo)

    class RuntimeError(sourceInfo: SourceInfo, val cause: ThrowException) : RunResult(sourceInfo)

    class InternalError(sourceInfo: SourceInfo, val cause: Throwable) : RunResult(sourceInfo)

    class Success(sourceInfo: SourceInfo, val result: JSValue) : RunResult(sourceInfo)

    fun unwrap(errorReporter: ErrorReporter = Reeva.activeAgent.errorReporter): JSValue? {
        when (this) {
            is Success -> return result
            is ParseError -> errorReporter.reportParseError(
                sourceInfo,
                error.cause,
                error.start,
                error.end,
            )
            is RuntimeError -> errorReporter.reportRuntimeError(
                sourceInfo,
                cause.value,
                cause.stackTrace,
            )
            is InternalError -> errorReporter.reportInternalError(sourceInfo, cause)
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

    val microtaskQueue = MicrotaskQueue(this)

    init {
        Reeva.allAgents.add(this)
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
            if (callStack.isEmpty())
                microtaskQueue.checkpoint()
        }
    }

    internal fun nextObjectId() = objectId++

    internal fun nextShapeId() = shapeId++
}
