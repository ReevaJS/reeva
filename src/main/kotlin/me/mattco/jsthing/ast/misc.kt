package me.mattco.jsthing.ast

import me.mattco.jsthing.ast.expressions.CallExpressionNode
import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.utils.stringBuilder

class InitializerNode(val node: ExpressionNode) : ASTNode(listOf(node))

class SpreadElementNode(val expression: ExpressionNode) : ASTNode(listOf(expression))

// CoverCallExpressionAndAsyncArrowHead
class CCEAAAHNode(val node: ExpressionNode) : ExpressionNode(listOf(node)) {
    override fun assignmentTargetType(): AssignmentTargetType {
        if (node is CallExpressionNode)
            return AssignmentTargetType.Invalid
        TODO()
    }
}

// CoverParenthesizedExpressionAndArrowParameterList
class CPEAAPLNode(val node: ExpressionNode) : ExpressionNode(listOf(node))
