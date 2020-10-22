package me.mattco.reeva.ast.literals

import me.mattco.reeva.ast.ExpressionNode
import me.mattco.reeva.ast.NodeBase
import me.mattco.reeva.ast.PrimaryExpressionNode

class ArrayLiteralNode(val elements: List<ArrayElementNode>) : NodeBase(elements), PrimaryExpressionNode {

}

class ArrayElementNode(val expression: ExpressionNode?, val type: Type) : NodeBase(listOfNotNull(expression)) {
    enum class Type {
        Normal,
        Spread,
        Elision,
    }
}

object ElisionNode : NodeBase(), ExpressionNode
