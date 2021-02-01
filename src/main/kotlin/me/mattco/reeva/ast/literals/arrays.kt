package me.mattco.reeva.ast.literals

import me.mattco.reeva.ast.ASTNodeBase
import me.mattco.reeva.ast.ExpressionNode

class ArrayLiteralNode(val elements: List<ArrayElementNode>) : ASTNodeBase(elements), ExpressionNode

class ArrayElementNode(val expression: ExpressionNode?, val type: Type) : ASTNodeBase(listOfNotNull(expression)) {
    enum class Type {
        Normal,
        Spread,
        Elision,
    }
}

object ElisionNode : ASTNodeBase(), ExpressionNode
