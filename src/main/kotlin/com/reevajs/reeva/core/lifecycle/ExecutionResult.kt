package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.parsing.lexer.TokenLocation
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.toPrintableString
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter

sealed class ExecutionResult(val executable: Executable) {
    open val isError = true

    class Success(executable: Executable, val value: JSValue) : ExecutionResult(executable) {
        override val isError = false
    }

    class ParseError(
        executable: Executable,
        val reason: String,
        val start: TokenLocation,
        val end: TokenLocation,
    ) : ExecutionResult(executable)

    class RuntimeError(executable: Executable, val value: JSValue) : ExecutionResult(executable) {
        override fun toString(): String {
            return value.toPrintableString()
        }
    }

    class InternalError(executable: Executable, val cause: Throwable) : ExecutionResult(executable) {
        override fun toString(): String {
            val writer = StringWriter()
            cause.printStackTrace(PrintWriter(writer))
            return writer.toString()
        }
    }
}

