package com.reevajs.reeva.ast.statements

import com.reevajs.reeva.ast.*

interface DeclarationNode : AstNode {
    val declarations: List<VariableSourceProvider>
}

interface VariableSourceProvider : AstNode {
    fun sources(): List<VariableSourceNode>

    fun names() = sources().map { it.name() }
}

sealed interface Declaration : VariableSourceProvider {
    val initializer: AstNode?
}

class LexicalDeclarationNode(
    val isConst: Boolean,
    override val declarations: List<Declaration>,
) : AstNodeBase(declarations), DeclarationNode

class VariableDeclarationNode(
    override val declarations: List<Declaration>,
) : AstNodeBase(declarations), DeclarationNode

class DestructuringDeclaration(
    val pattern: BindingPatternNode,
    override val initializer: AstNode?,
) : AstNodeBase(listOf(pattern)), Declaration {
    override fun sources() = pattern.sources()
}

class NamedDeclaration(
    val identifier: IdentifierNode,
    override val initializer: AstNode?,
) : VariableSourceNode(listOfNotNull(identifier, identifier)), Declaration {
    override fun name() = identifier.processedName

    override fun sources() = listOf(this)
}
