package me.mattco.jsthing.ast.statements

import me.mattco.jsthing.ast.ASTNode

abstract class StatementNode(vararg val children: ASTNode) : ASTNode(*children)
