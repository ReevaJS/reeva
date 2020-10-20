package me.mattco.jsthing.ast.expressions

import me.mattco.jsthing.ast.ASTNode

abstract class ExpressionNode(vararg children: ASTNode) : ASTNode(*children)
