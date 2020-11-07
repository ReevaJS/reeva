package me.mattco.reeva.ast.statements

import me.mattco.reeva.ast.*

class LexicalDeclarationNode(
    val isConst: Boolean,
    val bindingList: BindingListNode
) : NodeBase(listOf(bindingList)), DeclarationNode {
    override fun boundNames() = bindingList.boundNames()

    override fun containsDuplicateLabels(labelSet: Set<String>) = false

    override fun containsUndefinedBreakTarget(labelSet: Set<String>) = false

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>) = false

    override fun declarationPart() = this

    override fun isConstantDeclaration() = isConst

    override fun lexicallyDeclaredNames() = boundNames()

    override fun lexicallyScopedDeclarations() = listOf(this)

    override fun topLevelLexicallyDeclaredNames() = boundNames()

    override fun topLevelLexicallyScopedDeclarations() = listOf(this)

    override fun topLevelVarDeclaredNames() = emptyList<String>()

    override fun topLevelVarScopedDeclarations() = emptyList<NodeBase>()

    override fun varDeclaredNames() = emptyList<String>()

    override fun varScopedDeclarations() = emptyList<NodeBase>()
}

class BindingListNode(val lexicalBindings: List<LexicalBindingNode>) : NodeBase(lexicalBindings), StatementNode {
    override fun boundNames() = lexicalBindings.flatMap(NodeBase::boundNames)
}

class LexicalBindingNode(
    val identifier: BindingIdentifierNode,
    val initializer: InitializerNode?
) : NodeBase(listOfNotNull(identifier, initializer)), StatementNode {
    override fun boundNames() = identifier.boundNames()
}

class VariableStatementNode(val declarations: VariableDeclarationList) : NodeBase(listOf(declarations)), StatementNode {
    override fun varDeclaredNames() = declarations.boundNames()

    override fun topLevelVarDeclaredNames() = varDeclaredNames()
}

class VariableDeclarationList(val declarations: List<VariableDeclarationNode>) : NodeBase(declarations), StatementNode {
    override fun boundNames() = declarations.flatMap(NodeBase::boundNames)

    override fun varScopedDeclarations() = declarations
}

class VariableDeclarationNode(
    val identifier: BindingIdentifierNode,
    val initializer: InitializerNode?
) : NodeBase(listOfNotNull(identifier, initializer)), StatementNode {
    override fun boundNames() = identifier.boundNames()

    override fun varScopedDeclarations() = listOf(this)
}
