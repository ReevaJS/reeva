package me.mattco.jsthing.ast.statements

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.BindingIdentifierNode
import me.mattco.jsthing.ast.InitializerNode
import me.mattco.jsthing.utils.stringBuilder

class LexicalDeclarationNode(val isConst: Boolean, val bindingList: BindingListNode) : StatementNode()

class BindingListNode(val lexicalBindings: List<LexicalBindingNode>) : StatementNode()

class LexicalBindingNode(val identifier: BindingIdentifierNode, val initializer: InitializerNode?)

class VariableStatementNode(val declarations: VariableDeclarationList) : StatementNode()

class VariableDeclarationList(val declarations: List<VariableDeclarationNode>) : StatementNode()

class VariableDeclarationNode(val identifier: BindingIdentifierNode, val initializer: InitializerNode?) : ASTNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(identifier.dump(indent + 1))
        append(initializer?.dump(indent + 1))
    }
}
