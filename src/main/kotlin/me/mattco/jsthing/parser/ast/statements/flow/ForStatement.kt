package me.mattco.jsthing.parser.ast.statements.flow

import me.mattco.jsthing.parser.ast.ASTNode
import me.mattco.jsthing.parser.ast.expressions.Expression
import me.mattco.jsthing.parser.ast.statements.Statement

class ForStatement(val initializer: ASTNode?, val test: Expression?, val updater: Expression?, val body: Statement) : Statement() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
        if (initializer != null) {
            appendIndent(indent + 1)
            append("Initializer:\n")
            append(initializer.dump(indent + 2))
        }
        if (test != null) {
            appendIndent(indent + 1)
            append("test:\n")
            append(test.dump(indent + 2))
        }
        if (updater != null) {
            appendIndent(indent + 1)
            append("Updater:\n")
            append(updater.dump(indent + 2))
        }
        append(body.dump(indent + 2))
    }
}
