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
class CPEAAPLNode(
    val node: ExpressionNode,
    val context: Parser.CPEAAPLContext
) : NodeBase(listOf(node)), PrimaryExpressionNode {
    override fun assignmentTargetType(): ASTNode.AssignmentTargetType {
        return when (context) {
            Parser.CPEAAPLContext.PrimaryExpression -> {
                expect(node is ParenthesizedExpressionNode)
                node.target.assignmentTargetType()
            }
        }
    }

    override fun hasName(): Boolean {
        return when (context) {
            Parser.CPEAAPLContext.PrimaryExpression -> if (node.isFunctionDefinition()) {
                node.hasName()
            } else false
        }
    }

    override fun isFunctionDefinition(): Boolean {
        return when (context) {
            Parser.CPEAAPLContext.PrimaryExpression -> node.isFunctionDefinition()
        }
    }
}
