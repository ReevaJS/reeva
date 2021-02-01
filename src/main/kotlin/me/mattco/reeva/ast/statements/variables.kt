package me.mattco.reeva.ast.statements

import me.mattco.reeva.ast.*

class LexicalDeclarationNode(
    val isConst: Boolean,
    val declarations: List<Declaration>,
) : NodeWithScope(declarations), StatementNode {
    override fun lexicalDeclarations() = declarations

    override fun scopedLexicalDeclarations() = declarations
}

class VariableDeclarationNode(
    val declarations: List<Declaration>,
) : NodeWithScope(declarations), StatementNode {
    override fun variableDeclarations() = declarations

    override fun scopedVariableDeclarations() = declarations
}

class Declaration(
    override val isConst: Boolean,
    val identifier: BindingIdentifierNode,
    val initializer: ExpressionNode?
) : VariableSourceNode(listOfNotNull(identifier, initializer)) {
    override fun boundName() = identifier.identifierName
}
