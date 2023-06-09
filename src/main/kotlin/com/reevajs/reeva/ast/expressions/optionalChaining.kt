package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.parsing.lexer.SourceLocation

sealed class OptionalChain(sourceLocation: SourceLocation, val isOptional: Boolean) : AstNodeBase(sourceLocation)

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
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation) {
    override val children get() = parts + base

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}
