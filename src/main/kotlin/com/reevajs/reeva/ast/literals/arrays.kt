package com.reevajs.reeva.ast.literals

import com.reevajs.reeva.ast.AstNode
import com.reevajs.reeva.ast.AstNodeBase
import com.reevajs.reeva.ast.AstVisitor

class ArrayLiteralNode(val elements: List<ArrayElementNode>) : AstNodeBase() {
    override val children get() = elements

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class ArrayElementNode(val expression: AstNode?, val type: Type) : AstNodeBase() {
    override val children get() = listOfNotNull(expression)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    enum class Type {
        Normal,
        Spread,
        Elision,
    }
}
