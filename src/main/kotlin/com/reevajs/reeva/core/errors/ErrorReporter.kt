package com.reevajs.reeva.core.errors

import com.reevajs.reeva.core.RunResult
import com.reevajs.reeva.core.lifecycle.SourceInfo
import com.reevajs.reeva.parsing.lexer.TokenLocation
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.functions.JSFunction

// TODO: Add source information
data class StackTraceFrame(
    val enclosingFunction: JSFunction,
    val name: String,
    val isNative: Boolean,
)

abstract class ErrorReporter {
    fun reportParseError(result: RunResult.ParseError) {
        val error = result.error
        reportParseError(result.sourceInfo, error.cause, error.start, error.end)
    }

    fun reportRuntimeError(result: RunResult.RuntimeError) {
        val error = result.cause
        reportRuntimeError(result.sourceInfo, error.value, error.stackTrace)
    }

    fun reportInternalError(result: RunResult.InternalError) {
        reportInternalError(result.sourceInfo, result.cause)
    }

    abstract fun reportParseError(sourceInfo: SourceInfo, cause: String, start: TokenLocation, end: TokenLocation)

    abstract fun reportRuntimeError(sourceInfo: SourceInfo, cause: JSValue, stackTrace: List<StackTraceFrame>)

    abstract fun reportInternalError(sourceInfo: SourceInfo, cause: Throwable)
}


