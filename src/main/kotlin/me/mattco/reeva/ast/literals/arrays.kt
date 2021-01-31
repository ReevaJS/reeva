package me.mattco.reeva.ast.literals

import me.mattco.reeva.ast.ExpressionNode
import me.mattco.reeva.ast.ASTNodeBase
import me.mattco.reeva.ast.PrimaryExpressionNode

class ArrayLiteralNode(val elements: List<ArrayElementNode>) : ASTNodeBase(elements), PrimaryExpressionNode

class ArrayElementNode(val expression: ExpressionNode?, val type: Type) : ASTNodeBase(listOfNotNull(expression)) {
    enum class Type {
        Normal,
        Spread,
        Elision,
    }
}

object ElisionNode : ASTNodeBase(), ExpressionNode
