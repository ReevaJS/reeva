package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.AstNode
import com.reevajs.reeva.ast.AstNode.Companion.appendIndent
import com.reevajs.reeva.ast.AstNodeBase
import com.reevajs.reeva.ast.NodeWithScope

// This is a NodeWithScope because the delete operator needs to know whether or not
// it is in strict-mode code
class UnaryExpressionNode(
    val expression: AstNode,
    val op: UnaryOperator,
) : NodeWithScope(listOf(expression)) {
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

class UpdateExpressionNode(
    val target: AstNode,
    val isIncrement: Boolean,
    val isPostfix: Boolean,
) : AstNodeBase(listOf(target)) {
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
