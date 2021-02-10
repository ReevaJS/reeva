package me.mattco.reeva.ast.statements

import me.mattco.reeva.ast.*
import me.mattco.reeva.parser.Variable

class LexicalDeclarationNode(
    val isConst: Boolean,
    val declarations: List<Declaration>,
) : ASTNodeBase(declarations), StatementNode

class VariableDeclarationNode(
    val declarations: List<Declaration>,
) : ASTNodeBase(declarations), StatementNode

class Declaration(
    val identifier: BindingIdentifierNode,
    val initializer: ExpressionNode?
) : VariableSourceNode(listOfNotNull(identifier, initializer)) {
    override var variable: Variable by identifier::variable
}
