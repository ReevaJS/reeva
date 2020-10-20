package me.mattco.jsthing.ast.expressions

import me.mattco.jsthing.ast.ASTNode

abstract class ExpressionNode(children: List<ASTNode> = emptyList()) : ASTNode(children)
