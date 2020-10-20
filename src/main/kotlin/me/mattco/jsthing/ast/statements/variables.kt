package me.mattco.jsthing.ast.statements

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.BindingIdentifierNode
import me.mattco.jsthing.ast.InitializerNode
import me.mattco.jsthing.utils.stringBuilder

class LexicalDeclarationNode(val isConst: Boolean, val bindingList: BindingListNode) : StatementNode(listOf(bindingList)) {
    override fun boundNames() = bindingList.boundNames()
}

class BindingListNode(val lexicalBindings: List<LexicalBindingNode>) : StatementNode(lexicalBindings) {
    override fun boundNames(): List<String> {
        return lexicalBindings.flatMap(ASTNode::boundNames)
    }
}

class LexicalBindingNode(
    val identifier: BindingIdentifierNode,
    val initializer: InitializerNode?
) : StatementNode(listOfNotNull(identifier, initializer)) {
    override fun boundNames(): List<String> {
        return identifier.boundNames()
    }
}

class VariableStatementNode(val declarations: VariableDeclarationList) : StatementNode(listOf(declarations)) {
    override fun containsDuplicateLabels(labelSet: Set<String>) = false

    override fun containsUndefinedBreakTarget(labelSet: Set<String>) = false

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>) = false
}

class VariableDeclarationList(val declarations: List<VariableDeclarationNode>) : StatementNode(declarations) {
    override fun boundNames(): List<String> {
        return declarations.flatMap(ASTNode::boundNames)
    }
}

class VariableDeclarationNode(
    val identifier: BindingIdentifierNode,
    val initializer: InitializerNode?
) : StatementNode(listOfNotNull(identifier, initializer)) {
    override fun boundNames(): List<String> {
        return identifier.boundNames()
    }

    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(identifier.dump(indent + 1))
        append(initializer?.dump(indent + 1))
    }
}
