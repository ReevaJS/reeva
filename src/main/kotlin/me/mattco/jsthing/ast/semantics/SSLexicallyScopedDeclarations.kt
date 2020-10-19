package me.mattco.jsthing.ast.semantics

import me.mattco.jsthing.ast.ASTNode

interface SSLexicallyScopedDeclarations {
    fun lexicallyScopedDeclarations(): List<ASTNode>
}
