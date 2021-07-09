package me.mattco.reeva.interpreter

import me.mattco.reeva.core.Realm
import me.mattco.reeva.parsing.lexer.TokenLocation
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.toJSString
import me.mattco.reeva.runtime.toPrintableString
import java.io.PrintWriter
import java.io.StringWriter

sealed class ExecutionResult {
    open val isError = true

    abstract override fun toString(): String

    class Success(val result: JSValue) : ExecutionResult() {
        override val isError = false

        override fun toString() = result.toPrintableString()
    }

    class ParseError(val reason: String, val start: TokenLocation, val end: TokenLocation) : ExecutionResult() {
        override fun toString(): String {
            return "\u001b[31mParse error ($start-$end): $reason\u001B[0m\n"
        }
    }

    class RuntimeError(val realm: Realm, val cause: JSValue) : ExecutionResult() {
        override fun toString(): String {
            return "\u001b[31m${cause.toJSString(realm)}\u001B[0m"
        }
    }

    class InternalError(val cause: Throwable) : ExecutionResult() {
        override fun toString() = buildString {
            append("\u001b[31mInternal Reeva error\n")
            val sw = StringWriter()
            cause.printStackTrace(PrintWriter(sw))
            append(sw.toString())
        }
    }
}
