package me.mattco.jsthing.parser.ast.expressions

import me.mattco.jsthing.utils.stringBuilder

class MemberExpression(val target: Expression, val property: Expression, val computed: Boolean = false) : Expression() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (computed=")
        append(computed)
        append(")\n")
        appendIndent(indent + 1)
        append("Target: ")
        append(target.dump(0))
        appendIndent(indent + 1)
        append("Property: ")
        append(property.dump(0))
    }
}
