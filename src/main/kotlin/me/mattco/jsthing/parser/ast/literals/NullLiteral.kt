package me.mattco.jsthing.parser.ast.literals

import me.mattco.jsthing.utils.stringBuilder

object NullLiteral : Literal() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
    }
}
