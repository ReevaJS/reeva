package me.mattco.reeva.ast.statements

import me.mattco.reeva.ast.*

class LexicalDeclarationNode(
    val isConst: Boolean,
    val declarations: List<Declaration>,
) : ASTNodeBase(declarations), StatementNode

class VariableDeclarationNode(
    val declarations: List<Declaration>,
) : ASTNodeBase(declarations), StatementNode

class Declaration(
    val identifier: IdentifierNode,
    val initializer: ExpressionNode?
) : VariableSourceNode(listOfNotNull(initializer)) {
    override fun name() = identifier.name
}
