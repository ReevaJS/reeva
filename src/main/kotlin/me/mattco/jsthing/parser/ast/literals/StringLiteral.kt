package me.mattco.jsthing.parser.ast.literals

import me.mattco.jsthing.utils.stringBuilder

class StringLiteral(val value: String) : Literal() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (")
        append(value)
        append(")\n")
    }
}
