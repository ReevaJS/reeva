package com.reevajs.reeva.ast.statements

import com.reevajs.reeva.ast.*

class LexicalDeclarationNode(
    val isConst: Boolean,
    val declarations: List<Declaration>,
) : ASTNodeBase(declarations), StatementNode

class VariableDeclarationNode(
    val declarations: List<Declaration>,
) : ASTNodeBase(declarations), StatementNode

sealed interface Declaration : ASTNode {
    val initializer: ExpressionNode?
}

class DestructuringDeclaration(
    val pattern: BindingPatternNode,
    override val initializer: ExpressionNode?,
) : ASTNodeBase(listOfNotNull(pattern, initializer)), Declaration

class NamedDeclaration(
    val identifier: IdentifierNode,
    override val initializer: ExpressionNode?,
) : VariableSourceNode(listOfNotNull(identifier, identifier)), Declaration {
    override fun name() = identifier.name
}
