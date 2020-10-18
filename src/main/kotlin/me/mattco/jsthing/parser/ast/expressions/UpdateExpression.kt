package me.mattco.jsthing.parser.ast.expressions

class UpdateExpression(val expression: Expression, val increment: Boolean, val prefixed: Boolean) : Expression() {
    override fun dump(indent: Int) = stringBuilder {
        val op = if (increment) "++" else "--"

        appendIndent(indent)
        appendName()
        append("\n")
        appendIndent(indent + 1)
        if (prefixed)
            append(op)
        append("(")
        append(expression.dump(0))
        append(")\n")
        if (!prefixed)
            append(op)
    }
}
