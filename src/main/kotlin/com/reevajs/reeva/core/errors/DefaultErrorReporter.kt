package com.reevajs.reeva.core.errors

import com.reevajs.reeva.core.lifecycle.SourceInfo
import com.reevajs.reeva.parsing.lexer.TokenLocation
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.toPrintableString
import com.reevajs.reeva.utils.key
import java.io.PrintStream
import kotlin.math.max

class DefaultErrorReporter(private val out: PrintStream) : ErrorReporter {
    override fun reportParseError(
        sourceInfo: SourceInfo,
        cause: String,
        start: TokenLocation,
        end: TokenLocation,
    ) {
        val lines = sourceInfo.sourceText.lines()
        val firstLine = (start.line - 2).coerceAtLeast(0)
        val lastLine = (start.line + 2).coerceAtMost(lines.lastIndex)

        val lineIndexWidth = max(firstLine.toString().length, lastLine.toString().length)

        for (i in firstLine..lastLine) {
            out.print("\u001b[2;37m%${lineIndexWidth}d:    \u001b[0m".format(i))
            out.println(lines[i])
            if (i == start.line) {
                out.print(" ".repeat(start.column + lineIndexWidth + 5))
                out.print("\u001b[31m")
                val numCarets = if (start.line == end.line) {
                    (end.column - start.column).coerceAtMost(lines[i].length)
                } else lines[i].length - start.column
                out.println("^".repeat(numCarets))
                out.print("\u001b[0m")
            }
        }

        out.println()
        out.println("\u001b[31mSyntaxError: $cause\u001b[0m")
    }

    override fun reportRuntimeError(sourceInfo: SourceInfo, cause: JSValue, stackTrace: List<StackTraceFrame>) {
        out.println("\u001B[31m")

        if (cause is JSObject && cause.hasProperty("message".key())) {
            val ctor = cause.get("constructor")
            if (ctor is JSFunction) {
                out.print(ctor.get("name"))
                out.print(": ")
            }
            out.println(cause.get("message"))
        } else {
            out.println(cause.toPrintableString())
        }

        out.println("From: ${sourceInfo.name}")
        for (frame in stackTrace) {
            // TODO: Add location information
            out.println("    at ${frame.enclosingFunction.debugName}")
        }

        out.println("\u001B[0m")
    }

    override fun reportInternalError(sourceInfo: SourceInfo, cause: Throwable) {
        out.println("\u001B[31m")
        cause.printStackTrace(out)
        out.println("\u001B[0m")
    }
}