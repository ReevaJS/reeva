package me.mattco.jsthing.ast.semantics

import me.mattco.jsthing.ast.ASTNode

interface SSTopLevelLexicallyScopedDeclarations {
    fun topLevelLexicallyScopedDeclarations(): List<ASTNode>
}
