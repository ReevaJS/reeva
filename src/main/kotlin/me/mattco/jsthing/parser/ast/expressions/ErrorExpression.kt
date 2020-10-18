package me.mattco.jsthing.parser.ast.expressions

import me.mattco.jsthing.utils.stringBuilder

class ErrorExpression : Expression() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        append(name)
        append("\n")
    }
}
