package me.mattco.jsthing.ast.statements

import me.mattco.jsthing.ast.ASTNode

abstract class StatementNode(children: List<ASTNode> = emptyList()) : ASTNode(children)
