package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.*

sealed class OptionalChain(
    children: List<ASTNode>,
    val isOptional: Boolean,
) : ASTNodeBase(children)

class OptionalCallChain(
    val arguments: ArgumentList,
    isOptional: Boolean,
) : OptionalChain(arguments, isOptional)

class OptionalComputedAccessChain(
    val expr: ExpressionNode,
    isOptional: Boolean,
) : OptionalChain(listOf(expr), isOptional)

class OptionalAccessChain(
    val identifier: IdentifierNode,
    isOptional: Boolean,
) : OptionalChain(listOf(identifier), isOptional)

class OptionalChainNode(
    val base: ExpressionNode,
    val parts: List<OptionalChain>
) : ASTNodeBase(listOf(base) + parts), ExpressionNode