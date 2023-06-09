package com.reevajs.reeva.ast.literals

import com.reevajs.reeva.ast.AstNode
import com.reevajs.reeva.ast.AstVisitor
import com.reevajs.reeva.parsing.lexer.SourceLocation

class ArrayLiteralNode(
    val elements: List<ArrayElementNode>,
    override val sourceLocation: SourceLocation,
) : AstNode {
    override val children get() = elements

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class ArrayElementNode(
    val expression: AstNode?,
    val type: Type,
    override val sourceLocation: SourceLocation,
) : AstNode {
    override val children get() = listOfNotNull(expression)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    enum class Type {
        Normal,
        Spread,
        Elision,
    }
}
