package com.reevajs.reeva.core.errors

import com.reevajs.reeva.core.lifecycle.SourceInfo
import com.reevajs.reeva.parsing.lexer.TokenLocation
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.functions.JSFunction

data class StackTraceFrame(
    val enclosingFunction: JSFunction,
    val location: TokenLocation?, // null indicates native context
)

interface ErrorReporter {
    fun reportParseError(sourceInfo: SourceInfo, cause: String, start: TokenLocation, end: TokenLocation)

    fun reportRuntimeError(sourceInfo: SourceInfo, cause: JSValue, stackTrace: List<StackTraceFrame>)
}


