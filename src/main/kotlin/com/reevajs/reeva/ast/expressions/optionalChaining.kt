package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.*

sealed class OptionalChain(val isOptional: Boolean) : AstNodeBase()

class OptionalCallChain(
    val arguments: List<ArgumentNode>,
    isOptional: Boolean,
) : OptionalChain(isOptional) {
    override val children get() = arguments
}

class OptionalComputedAccessChain(
    val expr: AstNode,
    isOptional: Boolean,
) : OptionalChain(isOptional) {
    override val children get() = listOf(expr)
}

class OptionalAccessChain(
    val identifier: IdentifierNode,
    isOptional: Boolean,
) : OptionalChain(isOptional) {
    override val children get() = listOf(identifier)
}

class OptionalChainNode(
    val base: AstNode,
    val parts: List<OptionalChain>
) : AstNodeBase() {
    override val children get() = parts + base
}
