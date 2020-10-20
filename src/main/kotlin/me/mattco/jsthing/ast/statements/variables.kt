package me.mattco.jsthing.ast.statements

import me.mattco.jsthing.ast.NodeBase
import me.mattco.jsthing.ast.BindingIdentifierNode
import me.mattco.jsthing.ast.InitializerNode
import me.mattco.jsthing.ast.StatementNode
import me.mattco.jsthing.utils.stringBuilder

class LexicalDeclarationNode(val isConst: Boolean, val bindingList: BindingListNode) : NodeBase(listOf(bindingList)), StatementNode {
    override fun boundNames() = bindingList.boundNames()

    override fun isConstantDeclaration(): Boolean {
        return isConst
    }
}

class BindingListNode(val lexicalBindings: List<LexicalBindingNode>) : NodeBase(lexicalBindings), StatementNode {
    override fun boundNames(): List<String> {
        return lexicalBindings.flatMap(NodeBase::boundNames)
    }
}

class LexicalBindingNode(
    val identifier: BindingIdentifierNode,
    val initializer: InitializerNode?
) : NodeBase(listOfNotNull(identifier, initializer)), StatementNode {
    override fun boundNames(): List<String> {
        return identifier.boundNames()
    }
}

class VariableStatementNode(val declarations: VariableDeclarationList) : NodeBase(listOf(declarations)), StatementNode {
    override fun containsDuplicateLabels(labelSet: Set<String>) = false

    override fun containsUndefinedBreakTarget(labelSet: Set<String>) = false

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>) = false

    override fun varDeclaredNames(): List<String> {
        return declarations.boundNames()
    }
}

class VariableDeclarationList(val declarations: List<VariableDeclarationNode>) : NodeBase(declarations), StatementNode {
    override fun boundNames(): List<String> {
        return declarations.flatMap(NodeBase::boundNames)
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return declarations
    }
}

class VariableDeclarationNode(
    val identifier: BindingIdentifierNode,
    val initializer: InitializerNode?
) : NodeBase(listOfNotNull(identifier, initializer)), StatementNode {
    override fun boundNames(): List<String> {
        return identifier.boundNames()
    }

    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(identifier.dump(indent + 1))
        append(initializer?.dump(indent + 1))
    }
}
