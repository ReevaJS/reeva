package me.mattco.jsthing.ast.statements

import me.mattco.jsthing.ast.NodeBase
import me.mattco.jsthing.ast.BindingIdentifierNode
import me.mattco.jsthing.ast.InitializerNode
import me.mattco.jsthing.ast.StatementNode
import me.mattco.jsthing.utils.stringBuilder

class LexicalDeclarationNode(val isConst: Boolean, val bindingList: BindingListNode) : NodeBase(listOf(bindingList)), StatementNode {
    override fun boundNames() = bindingList.boundNames()

    override fun isConstantDeclaration() = isConst
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
}
