package me.mattco.jsthing.ast.semantics

import me.mattco.jsthing.ast.ASTNode

interface SSCoveredCallExpression {
    fun coveredCallExpression(): ASTNode
}
