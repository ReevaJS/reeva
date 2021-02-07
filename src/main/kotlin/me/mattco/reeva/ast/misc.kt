package me.mattco.reeva.ast

// CoverCallExpressionAndAsyncArrowHead
class CCEAAAHNode(val node: ExpressionNode) : ASTNodeBase(listOf(node)), ExpressionNode

// CoverParenthesizedExpressionAndArrowParameterList
class CPEAAPLNode(
    val covered: List<CPEAAPLPart>,
    val endsWithComma: Boolean,
) : ASTNodeBase()

data class CPEAAPLPart(val node: ASTNode, val isSpread: Boolean) : ASTNodeBase()
