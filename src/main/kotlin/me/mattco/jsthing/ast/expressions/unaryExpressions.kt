package me.mattco.jsthing.ast.expressions

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.utils.stringBuilder

class UnaryExpressionNode(val node: ExpressionNode, val op: Operator) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (operator=")
        append(op.name)
        append(")\n")
        append(node.dump(indent + 1))
    }

    enum class Operator {
        Delete,
        Void,
        Typeof,
        Plus,
        Minus,
        BitwiseNot,
        Not
    }
}

class UpdateExpressionNode(val target: ExpressionNode, val isIncrement: Boolean, val isPostfix: Boolean) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
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
