package me.mattco.jsthing.parser.ast.statements.flow

import me.mattco.jsthing.parser.ast.ASTNode
import me.mattco.jsthing.parser.ast.expressions.Expression
import me.mattco.jsthing.parser.ast.statements.Statement
import me.mattco.jsthing.utils.stringBuilder

class ForInStatement(val lhs: ASTNode, val rhs: Expression, val body: Statement) : Statement() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
        appendIndent(indent + 1)
        append("lhs:\n")
        append(lhs.dump(indent + 2))
        appendIndent(indent + 2)
        append("rhs:\n")
        append(rhs.dump(indent + 2))
        appendIndent(indent + 2)
        append("body:\n")
        append(body.dump(indent + 2))
    }
}
