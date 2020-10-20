package me.mattco.jsthing.ast.statements

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.BindingIdentifierNode
import me.mattco.jsthing.ast.InitializerNode
import me.mattco.jsthing.utils.stringBuilder

class LexicalDeclarationNode(val isConst: Boolean, val bindingList: BindingListNode) : StatementNode(listOf(bindingList))

class BindingListNode(val lexicalBindings: List<LexicalBindingNode>) : StatementNode(lexicalBindings)

class LexicalBindingNode(
    val identifier: BindingIdentifierNode,
    val initializer: InitializerNode?
) : StatementNode(listOfNotNull(identifier, initializer))

class VariableStatementNode(val declarations: VariableDeclarationList) : StatementNode(listOf(declarations))

class VariableDeclarationList(val declarations: List<VariableDeclarationNode>) : StatementNode(declarations)

class VariableDeclarationNode(
    val identifier: BindingIdentifierNode,
    val initializer: InitializerNode?
) : StatementNode(listOfNotNull(identifier, initializer)) {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(identifier.dump(indent + 1))
        append(initializer?.dump(indent + 1))
    }
}
