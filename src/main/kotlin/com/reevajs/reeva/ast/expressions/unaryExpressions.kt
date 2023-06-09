package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.AstNode
import com.reevajs.reeva.ast.AstNode.Companion.appendIndent
import com.reevajs.reeva.ast.AstVisitor
import com.reevajs.reeva.ast.NodeWithScope
import com.reevajs.reeva.parsing.lexer.SourceLocation

// This is a NodeWithScope because the delete operator needs to know whether or not
// it is in strict-mode code
class UnaryExpressionNode(
    val expression: AstNode,
    val op: UnaryOperator,
    sourceLocation: SourceLocation,
) : NodeWithScope(sourceLocation) {
    override val children get() = listOf(expression)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

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
    override val sourceLocation: SourceLocation,
) : AstNode {
    override val children get() = listOf(target)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

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
