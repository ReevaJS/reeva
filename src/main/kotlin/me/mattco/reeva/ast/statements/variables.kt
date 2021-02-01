package me.mattco.reeva.ast.statements

import me.mattco.reeva.ast.*

class LexicalDeclarationNode(
    val isConst: Boolean,
    val bindingList: BindingListNode
) : ASTNodeBase(listOf(bindingList)), DeclarationNode {
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

    override fun topLevelVarScopedDeclarations() = emptyList<ASTNodeBase>()

    override fun varDeclaredNames() = emptyList<String>()

    override fun varScopedDeclarations() = emptyList<ASTNodeBase>()
}

class BindingListNode(val lexicalBindings: List<LexicalBindingNode>) : ASTNodeBase(lexicalBindings), StatementNode {
    override fun boundNames() = lexicalBindings.flatMap(ASTNodeBase::boundNames)
}

class LexicalBindingNode(
    val identifier: BindingIdentifierNode,
    val initializer: InitializerNode?
) : VariableSourceNode(listOfNotNull(identifier, initializer)), StatementNode {
    override fun boundNames() = identifier.boundNames()
}

class VariableStatementNode(val declarations: VariableDeclarationList) : ASTNodeBase(listOf(declarations)), StatementNode {
    override fun varDeclaredNames() = declarations.boundNames()

    override fun topLevelVarDeclaredNames() = varDeclaredNames()
}

class VariableDeclarationList(val declarations: List<VariableDeclarationNode>) : ASTNodeBase(declarations), StatementNode {
    override fun boundNames() = declarations.flatMap(ASTNodeBase::boundNames)

    override fun varScopedDeclarations() = declarations
}

class VariableDeclarationNode(
    val identifier: BindingIdentifierNode,
    val initializer: InitializerNode?
) : VariableSourceNode(listOfNotNull(identifier, initializer)), StatementNode {
    override fun boundNames() = identifier.boundNames()

    override fun varScopedDeclarations() = listOf(this)
}
