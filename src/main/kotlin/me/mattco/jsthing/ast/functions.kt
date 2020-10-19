package me.mattco.jsthing.ast

import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.utils.stringBuilder

class ArgumentsNodeWrapper(val arguments: List<ArgumentsNode>) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        arguments.forEach {
            append(it.dump(indent + 1))
        }
    }
}

class ArgumentsNode(val expression: ExpressionNode, val isSpread: Boolean) : ASTNode() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (isSpread=")
        append(isSpread)
        append(")\n")
        append(expression.dump(indent + 1))
    }
}
