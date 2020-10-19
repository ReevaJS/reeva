package me.mattco.jsthing.ast.semantics

import me.mattco.jsthing.ast.ASTNode

interface SSTopLevelVarScopedDeclarations {
    fun topLevelVarScopedDeclarations(): List<ASTNode>
}
