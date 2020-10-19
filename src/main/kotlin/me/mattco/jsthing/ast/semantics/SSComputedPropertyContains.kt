package me.mattco.jsthing.ast.semantics

import me.mattco.jsthing.ast.ASTNode

interface SSComputedPropertyContains {
    fun computedPropertyContains(symbol: ASTNode): Boolean
}
