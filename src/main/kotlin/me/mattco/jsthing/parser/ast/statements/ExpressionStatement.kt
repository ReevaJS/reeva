package me.mattco.jsthing.parser.ast.statements

import me.mattco.jsthing.parser.ast.expressions.Expression
import me.mattco.jsthing.utils.stringBuilder

class ExpressionStatement(private val expression: Expression) : Statement() {
    override fun dump(indent: Int) = stringBuilder {
        append(makeIndent(indent))
        append(name)
        append("\n")
        append(expression.dump(indent + 1))
    }
}
