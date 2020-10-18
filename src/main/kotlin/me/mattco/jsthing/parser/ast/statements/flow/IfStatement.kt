package me.mattco.jsthing.parser.ast.statements.flow

import me.mattco.jsthing.parser.ast.expressions.Expression
import me.mattco.jsthing.parser.ast.statements.Statement

class IfStatement(val predicate: Expression, val consequent: Statement, val alternate: Statement?) : Statement() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
        appendIndent(indent + 1)
        append("Predicate:\n")
        append(predicate.dump(indent + 2))
        appendIndent(indent + 1)
        append("Consequent:\n")
        append(consequent.dump(indent + 2))
        if (alternate != null) {
            appendIndent(indent + 1)
            append("Alternate:\n")
            append(alternate.dump(indent + 2))
        }
    }
}
