package me.mattco.reeva.ast.expressions

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.ASTNodeBase
import me.mattco.reeva.ast.ExpressionNode
import me.mattco.reeva.ast.NodeWithScope

// This is a NodeWithScope because the delete operator needs to know whether or not
// it is in strict-mode code
class UnaryExpressionNode(val expression: ExpressionNode, val op: UnaryOperator) : NodeWithScope(listOf(expression)), ExpressionNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (operator=")
        append(op.name)
        append(")\n")
        append(expression.dump(indent + 1))
    }
}

enum class UnaryOperator {
    Delete,
    Void,
    Typeof,
    Plus,
    Minus,
    BitwiseNot,
    Not
}

class UpdateExpressionNode(val target: ExpressionNode, val isIncrement: Boolean, val isPostfix: Boolean) : ASTNodeBase(listOf(target)), ExpressionNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (isIncrement=")
        append(isIncrement)
        append(", isPostfix=")
        append(isPostfix)
        append(")\n")
        append(target.dump(indent + 1))
    }
}
