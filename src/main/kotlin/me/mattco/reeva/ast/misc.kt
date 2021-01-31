package me.mattco.reeva.ast

import me.mattco.reeva.ast.expressions.CallExpressionNode

class InitializerNode(val expression: ExpressionNode) : ASTNodeBase(listOf(expression))

class SpreadElementNode(val expression: ExpressionNode) : ASTNodeBase(listOf(expression))

// CoverCallExpressionAndAsyncArrowHead
class CCEAAAHNode(val node: ExpressionNode) : ASTNodeBase(listOf(node)), ExpressionNode {
    override fun assignmentTargetType(): ASTNode.AssignmentTargetType {
        if (node is CallExpressionNode)
            return ASTNode.AssignmentTargetType.Invalid
        TODO()
    }
}

// CoverParenthesizedExpressionAndArrowParameterList
class CPEAAPLNode(val covered: List<CPEAPPLPart>)

class CPEAPPLPart(val node: ASTNode, val isSpread: Boolean)
