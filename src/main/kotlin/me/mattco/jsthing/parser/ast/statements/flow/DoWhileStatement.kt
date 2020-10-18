package me.mattco.jsthing.parser.ast.statements.flow

import me.mattco.jsthing.parser.ast.ASTNode.Companion.appendIndent
import me.mattco.jsthing.parser.ast.expressions.Expression
import me.mattco.jsthing.parser.ast.statements.Statement

class DoWhileStatement(val condition: Expression, val body: Statement) : Statement() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
        appendIndent(indent + 1)
        append("Condition:\n")
        append(condition.dump(indent + 2))
        appendIndent(indent + 1)
        append("Body:\n")
        append(body.dump(indent + 2))
    }
}
