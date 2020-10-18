package me.mattco.jsthing.parser.ast.expressions

import me.mattco.jsthing.utils.stringBuilder

class Identifier(val string: String) : Expression() {
    override fun dump(indent: Int) = stringBuilder {
        append(makeIndent(indent))
        append(name)
        append(" (")
        append(string)
        append(")\n")
    }
}
