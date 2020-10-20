package me.mattco.jsthing.ast

import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.utils.stringBuilder

class InitializerNode(val node: ExpressionNode) : ASTNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(node.dump(indent + 1))
    }
}

class SpreadElementNode(val expression: ExpressionNode) : ASTNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(expression.dump(indent + 1))
    }
}

// CoverParenthesizedExpressionAndArrowParameterList
class CPEAAPL(val node: ExpressionNode) : ExpressionNode()
