package me.mattco.jsthing.parser.ast.expressions

import me.mattco.jsthing.utils.stringBuilder

class CallExpression(val target: Expression, val arguments: List<Argument>) : Expression() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
        appendIndent(indent + 1)
        append("Target:\n")
        append(target.dump(indent + 2))
        appendIndent(indent + 1)
        append("Arguments:\n")
        arguments.forEach {
            append(it.value.dump(indent + 2))
            if (it.isSpread) {
                appendIndent(indent + 3)
                append("spread: true\n")
            }
        }
    }

    data class Argument(val value: Expression, val isSpread: Boolean)
}
