package me.mattco.reeva.ast

import me.mattco.reeva.ast.expressions.CallExpressionNode
import me.mattco.reeva.ast.expressions.ParenthesizedExpressionNode
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.utils.expect

class InitializerNode(val node: ExpressionNode) : NodeBase(listOf(node))

class SpreadElementNode(val expression: ExpressionNode) : NodeBase(listOf(expression))

// CoverCallExpressionAndAsyncArrowHead
class CCEAAAHNode(val node: ExpressionNode) : NodeBase(listOf(node)), ExpressionNode {
    override fun assignmentTargetType(): ASTNode.AssignmentTargetType {
        if (node is CallExpressionNode)
            return ASTNode.AssignmentTargetType.Invalid
        TODO()
    }
}

// CoverParenthesizedExpressionAndArrowParameterList
class CPEAAPLNode(val covered: List<CPEAPPLPart>)

class CPEAPPLPart(val node: ASTNode, val isSpread: Boolean)
