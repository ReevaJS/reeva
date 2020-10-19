package me.mattco.jsthing.ast.semantics

import me.mattco.jsthing.ast.ASTNode

interface SSCoveredParenthesizedExpression {
    fun coveredParenthesizedExpression(): ASTNode
}
