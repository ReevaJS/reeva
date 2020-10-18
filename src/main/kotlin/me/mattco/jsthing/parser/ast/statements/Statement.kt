package me.mattco.jsthing.parser.ast.statements

import me.mattco.jsthing.parser.ast.ASTNode

abstract class Statement(private val label: String? = null) : ASTNode()
