package me.mattco.reeva.ast.statements

import me.mattco.reeva.ast.*

class LexicalDeclarationNode(
    val isConst: Boolean,
    val declarations: List<Declaration>,
) : NodeWithScope(declarations), StatementNode

class VariableDeclarationNode(
    val declarations: List<Declaration>,
) : NodeWithScope(declarations), StatementNode

class Declaration(
    val identifier: BindingIdentifierNode,
    val initializer: ExpressionNode?
) : VariableSourceNode(listOfNotNull(identifier, initializer))
