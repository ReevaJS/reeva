package me.mattco.jsthing.parser.ast.statements.flow

import me.mattco.jsthing.parser.ast.expressions.Expression
import me.mattco.jsthing.parser.ast.statements.Statement
import me.mattco.jsthing.utils.stringBuilder

class ReturnStatement(val expression: Expression?) : Statement() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        append(name)
        append("\n")
        if (expression != null)
            append(expression.dump(indent + 1))
    }
}
