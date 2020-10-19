package me.mattco.jsthing.ast

import me.mattco.jsthing.utils.newline
import me.mattco.jsthing.utils.stringBuilder

abstract class ASTNode {
    val name: String
        get() = this::class.java.simpleName

    abstract fun dump(indent: Int = 0): String

    fun StringBuilder.dumpSelf(indent: Int) {
        appendIndent(indent)
        append(name)
        newline()
    }

    fun StringBuilder.appendName() = append(name)

    enum class Suffix {
        Yield,
        Await,
        In,
        Return,
        Tagged,
    }

    companion object {
        private const val INDENT = "    "

        fun makeIndent(indent: Int) = stringBuilder {
            repeat(indent) {
                append(INDENT)
            }
        }

        fun StringBuilder.appendIndent(indent: Int) = append(makeIndent(indent))
    }
}
