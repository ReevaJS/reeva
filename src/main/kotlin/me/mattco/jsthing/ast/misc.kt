package me.mattco.jsthing.ast

import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.utils.stringBuilder

class InitializerNode(val node: ExpressionNode) : ASTNode(listOf(node))

class SpreadElementNode(val expression: ExpressionNode) : ASTNode(listOf(expression))

// CoverParenthesizedExpressionAndArrowParameterList
class CPEAAPL(val node: ExpressionNode) : ExpressionNode(listOf(node))
