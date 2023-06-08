package com.reevajs.reeva.ast.literals

import com.reevajs.reeva.ast.AstNode
import com.reevajs.reeva.ast.AstNodeBase

class ArrayLiteralNode(val elements: List<ArrayElementNode>) : AstNodeBase() {
    override val children get() = elements
}

class ArrayElementNode(val expression: AstNode?, val type: Type) : AstNodeBase() {
    override val children get() = listOfNotNull(expression)

    enum class Type {
        Normal,
        Spread,
        Elision,
    }
}
