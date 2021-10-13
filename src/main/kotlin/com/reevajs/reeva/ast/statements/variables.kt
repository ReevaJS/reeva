package com.reevajs.reeva.ast.statements

import com.reevajs.reeva.ast.*

interface DeclarationNode {
    val declarations: List<Declaration>
}

class LexicalDeclarationNode(
    val isConst: Boolean,
    override val declarations: List<Declaration>,
) : ASTNodeBase(declarations), DeclarationNode, StatementNode

class VariableDeclarationNode(
    override val declarations: List<Declaration>,
) : ASTNodeBase(declarations), DeclarationNode, StatementNode

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
