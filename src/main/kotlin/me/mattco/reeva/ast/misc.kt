package me.mattco.reeva.ast

import me.mattco.reeva.ast.statements.ASTListNode

// CoverCallExpressionAndAsyncArrowHead
class CCEAAAHNode(val node: ExpressionNode) : ASTNodeBase(listOf(node)), ExpressionNode

// CoverParenthesizedExpressionAndArrowParameterList
class CPEAAPLNode(
    val parts: List<CPEAAPLPart>,
    val endsWithComma: Boolean,
) : ASTListNode<CPEAAPLPart>(parts)

data class CPEAAPLPart(val node: ExpressionNode, val isSpread: Boolean) : ASTNodeBase(listOf(node))
