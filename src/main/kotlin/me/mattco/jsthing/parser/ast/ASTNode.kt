package me.mattco.jsthing.parser.ast

import me.mattco.jsthing.utils.stringBuilder

abstract class ASTNode {
    val name: String
        get() = this::class.java.simpleName

    abstract fun dump(indent: Int = 0): String

    fun StringBuilder.appendName() = append(name)

    companion object {
        const val INDENT = "  "

        fun makeIndent(indent: Int) = stringBuilder {
            repeat(indent) {
                append(INDENT)
            }
        }

        fun StringBuilder.appendIndent(indent: Int) = append(makeIndent(indent))
    }
}
