package com.reevajs.reeva.core.errors

import com.reevajs.reeva.core.StackTraceFrame
import com.reevajs.reeva.core.lifecycle.SourceInfo
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.parsing.lexer.TokenLocation
import com.reevajs.reeva.runtime.JSValue

abstract class ErrorReporter {
    fun reportParseError(sourceInfo: SourceInfo, error: ParsingError) {
        reportParseError(sourceInfo, error.cause, error.start, error.end)
    }

    fun reportRuntimeError(sourceInfo: SourceInfo, cause: ThrowException) {
        reportRuntimeError(sourceInfo, cause.value, cause.stackFrames)
    }

    abstract fun reportParseError(sourceInfo: SourceInfo, cause: String, start: TokenLocation, end: TokenLocation)

    abstract fun reportRuntimeError(sourceInfo: SourceInfo, cause: JSValue, stackFrames: List<StackTraceFrame>)

    abstract fun reportInternalError(sourceInfo: SourceInfo, cause: Throwable)
}
