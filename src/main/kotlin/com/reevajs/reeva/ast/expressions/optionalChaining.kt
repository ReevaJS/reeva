package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.parsing.lexer.SourceLocation

sealed class OptionalChain(override val sourceLocation: SourceLocation, val isOptional: Boolean) : AstNode

class OptionalCallChain(
    val arguments: List<ArgumentNode>,
    isOptional: Boolean,
    sourceLocation: SourceLocation,
) : OptionalChain(sourceLocation, isOptional) {
    override val children get() = arguments

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class OptionalComputedAccessChain(
    val expr: AstNode,
    isOptional: Boolean,
    sourceLocation: SourceLocation,
) : OptionalChain(sourceLocation, isOptional) {
    override val children get() = listOf(expr)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class OptionalAccessChain(
    val identifier: IdentifierNode,
    isOptional: Boolean,
    sourceLocation: SourceLocation,
) : OptionalChain(sourceLocation, isOptional) {
    override val children get() = listOf(identifier)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class OptionalChainNode(
    val base: AstNode,
    val parts: List<OptionalChain>,
    override val sourceLocation: SourceLocation,
) : AstNode {
    override val children get() = parts + base

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}
