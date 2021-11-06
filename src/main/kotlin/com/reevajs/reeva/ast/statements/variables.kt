package com.reevajs.reeva.ast.statements

import com.reevajs.reeva.ast.*

interface DeclarationNode : StatementNode {
    val declarations: List<VariableSourceProvider>
}

interface VariableSourceProvider : ASTNode {
    fun sources(): List<VariableSourceNode>

    fun names() = sources().map { it.name() }
}

sealed interface Declaration : VariableSourceProvider {
    val initializer: ExpressionNode?
}

class LexicalDeclarationNode(
    val isConst: Boolean,
    override val declarations: List<Declaration>,
) : ASTNodeBase(declarations), DeclarationNode

class VariableDeclarationNode(
    override val declarations: List<Declaration>,
) : ASTNodeBase(declarations), DeclarationNode

class DestructuringDeclaration(
    val pattern: BindingPatternNode,
    override val initializer: ExpressionNode?,
) : ASTNodeBase(listOf(pattern)), Declaration {
    override fun sources() = pattern.sources()
}

class NamedDeclaration(
    val identifier: IdentifierNode,
    override val initializer: ExpressionNode?,
) : VariableSourceNode(listOfNotNull(identifier, identifier)), Declaration {
    override fun name() = identifier.processedName

    override fun sources() = listOf(this)
}
