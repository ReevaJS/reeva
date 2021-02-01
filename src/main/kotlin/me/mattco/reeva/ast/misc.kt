package me.mattco.reeva.ast

// CoverCallExpressionAndAsyncArrowHead
class CCEAAAHNode(val node: ExpressionNode) : ASTNodeBase(listOf(node)), ExpressionNode

// CoverParenthesizedExpressionAndArrowParameterList
class CPEAAPLNode(val covered: List<CPEAPPLPart>)

class CPEAPPLPart(val node: ASTNode, val isSpread: Boolean)
