package me.mattco.jsthing.ast.semantics

import me.mattco.jsthing.ast.ASTNode

interface SSVarScopedDeclarations {
    fun varScopedDeclarations(): List<ASTNode>
}
