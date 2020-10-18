package me.mattco.jsthing.parser.ast.statements

import me.mattco.jsthing.parser.ast.ASTNode
import me.mattco.jsthing.parser.ast.expressions.Expression
import me.mattco.jsthing.parser.ast.expressions.Identifier

class VariableDeclarator(val identifier: Identifier, val initializer: Expression? = null) : ASTNode() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
        append(identifier.dump(indent + 1))

        if (initializer != null)
            append(initializer.dump(indent + 1))
    }
}
