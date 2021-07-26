package com.reevajs.reeva.ast.literals

import com.reevajs.reeva.ast.ASTNodeBase
import com.reevajs.reeva.ast.ExpressionNode

class ArrayLiteralNode(val elements: List<ArrayElementNode>) : ASTNodeBase(elements), ExpressionNode

class ArrayElementNode(val expression: ExpressionNode?, val type: Type) : ASTNodeBase(listOfNotNull(expression)) {
    enum class Type {
        Normal,
        Spread,
        Elision,
    }
}

object ElisionNode : ASTNodeBase(), ExpressionNode
