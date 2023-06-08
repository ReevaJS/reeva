package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.*

sealed class OptionalChain(
    children: List<AstNode>,
    val isOptional: Boolean,
) : AstNodeBase(children)

class OptionalCallChain(
    val arguments: List<ArgumentNode>,
    isOptional: Boolean,
) : OptionalChain(arguments, isOptional)

class OptionalComputedAccessChain(
    val expr: AstNode,
    isOptional: Boolean,
) : OptionalChain(listOf(expr), isOptional)

class OptionalAccessChain(
    val identifier: IdentifierNode,
    isOptional: Boolean,
) : OptionalChain(listOf(identifier), isOptional)

class OptionalChainNode(
    val base: AstNode,
    val parts: List<OptionalChain>
) : AstNodeBase(listOf(base) + parts)
