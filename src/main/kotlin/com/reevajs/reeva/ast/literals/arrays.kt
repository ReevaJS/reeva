package com.reevajs.reeva.ast.literals

import com.reevajs.reeva.ast.AstNodeBase
import com.reevajs.reeva.ast.ExpressionNode

class ArrayLiteralNode(val elements: List<ArrayElementNode>) : AstNodeBase(elements), ExpressionNode

class ArrayElementNode(val expression: ExpressionNode?, val type: Type) : AstNodeBase(listOfNotNull(expression)) {
    enum class Type {
        Normal,
        Spread,
        Elision,
    }
}

object ElisionNode : AstNodeBase(), ExpressionNode
