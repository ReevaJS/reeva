package me.mattco.reeva.ast.statements

import me.mattco.reeva.ast.*

class LexicalDeclarationNode(
    override val isConst: Boolean,
    val declarations: List<Declaration>,
) : VariableSourceNode(declarations), StatementNode {
    override fun boundNames() = declarations.map { it.identifier.identifierName }

    override fun lexicalDeclarations() = listOf(this)

    override fun scopedLexicalDeclarations() = listOf(this)
}

class VariableDeclarationNode(
    val declarations: List<Declaration>,
) : VariableSourceNode(declarations), StatementNode {
    override fun boundNames() = declarations.map { it.identifier.identifierName }

    override fun variableDeclarations() = listOf(this)

    override fun scopedVariableDeclarations() = listOf(this)
}

class Declaration(
    val identifier: BindingIdentifierNode,
    val initializer: ExpressionNode?
) : ASTNodeBase(listOfNotNull(identifier, initializer))
