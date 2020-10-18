package me.mattco.jsthing.parser.ast.expressions

import me.mattco.jsthing.utils.stringBuilder

class ConditionalExpression(val test: Expression, val consequent: Expression, val alternate: Expression) : Expression() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
        append(test.dump(indent + 1))
        append(consequent.dump(indent + 1))
        append(alternate.dump(indent + 1))
    }
}
