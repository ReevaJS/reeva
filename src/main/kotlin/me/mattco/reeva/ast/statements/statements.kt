package me.mattco.reeva.ast.statements

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.ASTNode.Companion.appendIndent

class BlockStatementNode(val block: BlockNode) : NodeBase(listOf(block)), StatementNode

class BlockNode(val statements: StatementListNode?) : NodeBase(listOfNotNull(statements)), StatementNode {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        if (statements == null)
            return false
        return super<NodeBase>.containsDuplicateLabels(labelSet)
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        if (statements == null)
            return false
        return super<NodeBase>.containsUndefinedBreakTarget(labelSet)
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        if (statements == null)
            return false
        return super<NodeBase>.containsUndefinedContinueTarget(iterationSet, labelSet)
    }

    override fun lexicallyDeclaredNames(): List<String> {
        if (statements == null)
            return emptyList()
        return super<NodeBase>.lexicallyDeclaredNames()
    }

    override fun topLevelLexicallyScopedDeclarations() = emptyList<NodeBase>()

    override fun topLevelVarDeclaredNames() = emptyList<String>()

    override fun topLevelVarScopedDeclarations() = emptyList<NodeBase>()

    override fun varDeclaredNames() = emptyList<String>()

    override fun varScopedDeclarations() = emptyList<NodeBase>()
}

class StatementListNode(val statements: List<StatementListItemNode>) : NodeBase(statements), StatementNode {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        return statements.any { it.containsDuplicateLabels(labelSet) }
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return statements.any { it.containsUndefinedBreakTarget(labelSet) }
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return statements.any { it.containsUndefinedContinueTarget(iterationSet, labelSet) }
    }

    override fun lexicallyDeclaredNames(): List<String> {
        return statements.flatMap(StatementListItemNode::lexicallyDeclaredNames)
    }

    override fun lexicallyScopedDeclarations(): List<NodeBase> {
        return statements.flatMap(StatementListItemNode::lexicallyScopedDeclarations)
    }

    override fun topLevelLexicallyDeclaredNames(): List<String> {
        return statements.flatMap(StatementListItemNode::topLevelLexicallyDeclaredNames)
    }

    override fun topLevelLexicallyScopedDeclarations(): List<NodeBase> {
        return statements.flatMap(StatementListItemNode::topLevelLexicallyScopedDeclarations)
    }

    override fun topLevelVarDeclaredNames(): List<String> {
        return statements.flatMap(StatementListItemNode::topLevelVarDeclaredNames)
    }

    override fun topLevelVarScopedDeclarations(): List<NodeBase> {
        return statements.flatMap(StatementListItemNode::topLevelVarScopedDeclarations)
    }

    override fun varDeclaredNames(): List<String> {
        return statements.flatMap(StatementListItemNode::varDeclaredNames)
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return statements.flatMap(StatementListItemNode::varScopedDeclarations)
    }
}

object EmptyStatementNode : NodeBase(), StatementNode

class ExpressionStatementNode(val node: ExpressionNode): NodeBase(listOf(node)), StatementNode

class IfStatementNode(
    val condition: ExpressionNode,
    val trueBlock: StatementNode,
    val falseBlock: StatementNode?
) : NodeBase(listOfNotNull(condition, trueBlock, falseBlock)), StatementNode {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        return trueBlock.containsDuplicateLabels(labelSet) || falseBlock?.containsDuplicateLabels(labelSet) ?: false
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return trueBlock.containsUndefinedBreakTarget(labelSet) ||
            falseBlock?.containsUndefinedBreakTarget(labelSet) ?: false
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return trueBlock.containsUndefinedContinueTarget(iterationSet, emptySet()) ||
            falseBlock?.containsUndefinedContinueTarget(iterationSet, emptySet()) ?: false
    }

    override fun varDeclaredNames(): List<String> {
        return trueBlock.varDeclaredNames() + (falseBlock?.varDeclaredNames() ?: emptyList())
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return trueBlock.varScopedDeclarations() + (falseBlock?.varScopedDeclarations() ?: emptyList())
    }
}

class DoWhileStatementNode(val condition: ExpressionNode, val body: StatementNode) : NodeBase(listOf(condition, body)), IterationStatement {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        return body.containsDuplicateLabels(labelSet)
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return body.containsUndefinedBreakTarget(labelSet)
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return body.containsUndefinedContinueTarget(iterationSet, emptySet())
    }

    override fun topLevelVarDeclaredNames(): List<String> {
        return body.varDeclaredNames()
    }

    override fun topLevelVarScopedDeclarations(): List<NodeBase> {
        return body.varScopedDeclarations()
    }

    override fun varDeclaredNames(): List<String> {
        return body.varDeclaredNames()
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return body.varScopedDeclarations()
    }
}

class WhileStatementNode(val condition: ExpressionNode, val body: StatementNode) : NodeBase(listOf(condition, body)), IterationStatement {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        return body.containsDuplicateLabels(labelSet)
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return body.containsUndefinedBreakTarget(labelSet)
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return body.containsUndefinedContinueTarget(iterationSet, emptySet())
    }

    override fun topLevelVarDeclaredNames(): List<String> {
        return body.varDeclaredNames()
    }

    override fun topLevelVarScopedDeclarations(): List<NodeBase> {
        return body.varScopedDeclarations()
    }

    override fun varDeclaredNames(): List<String> {
        return body.varDeclaredNames()
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return body.varScopedDeclarations()
    }
}

class ForStatementNode(
    val initializer: ASTNode?, // can be an expression or a statement
    val condition: ExpressionNode?,
    val incrementer: ExpressionNode?,
    val body: StatementNode,
) : NodeBase(listOfNotNull(initializer, condition, incrementer, body)), IterationStatement {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        return body.containsDuplicateLabels(labelSet)
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return body.containsUndefinedBreakTarget(labelSet)
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return body.containsUndefinedContinueTarget(iterationSet, emptySet())
    }

    override fun topLevelVarDeclaredNames() = varDeclaredNames()

    override fun topLevelVarScopedDeclarations() = varScopedDeclarations()

    override fun varDeclaredNames(): List<String> {
        return body.varDeclaredNames() + if (initializer is VariableStatementNode) {
            initializer.varDeclaredNames()
        } else emptyList()
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return body.varScopedDeclarations() + if (initializer is VariableStatementNode) {
            initializer.varScopedDeclarations()
        } else emptyList()
    }

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (")
        if (initializer != null)
            append("initializer")
        append(";")
        if (condition != null)
            append("condition")
        append(";")
        if (incrementer != null)
            append("incrementer")
        append(")\n")
        initializer?.dump(indent + 1)?.also(::append)
        condition?.dump(indent + 1)?.also(::append)
        incrementer?.dump(indent + 1)?.also(::append)
        append(body.dump(indent + 1))
    }
}

class ForInNode(
    val decl: StatementNode,
    val expression: ExpressionNode,
    val body: StatementNode
) : NodeBase(listOf(decl, expression)), IterationStatement {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        return body.containsDuplicateLabels(labelSet)
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return body.containsUndefinedBreakTarget(labelSet)
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return body.containsUndefinedContinueTarget(iterationSet, emptySet())
    }

    override fun varDeclaredNames(): List<String> {
        return body.varDeclaredNames()
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return body.varScopedDeclarations()
    }
}

class ForOfNode(
    val decl: StatementNode,
    val expression: ExpressionNode,
    val body: StatementNode
) : NodeBase(listOf(decl, expression)), IterationStatement {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        return body.containsDuplicateLabels(labelSet)
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return body.containsUndefinedBreakTarget(labelSet)
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return body.containsUndefinedContinueTarget(iterationSet, emptySet())
    }

    override fun varDeclaredNames(): List<String> {
        return body.varDeclaredNames()
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return body.varScopedDeclarations()
    }
}

class ForAwaitOfNode(
    val decl: StatementNode,
    val expression: ExpressionNode,
    val body: StatementNode
) : NodeBase(listOf(decl, expression)), IterationStatement {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        return body.containsDuplicateLabels(labelSet)
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return body.containsUndefinedBreakTarget(labelSet)
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return body.containsUndefinedContinueTarget(iterationSet, emptySet())
    }

    override fun varDeclaredNames(): List<String> {
        return body.varDeclaredNames()
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return body.varScopedDeclarations()
    }
}

class LabelledStatementNode(val label: LabelIdentifierNode, val item: StatementNode) : NodeBase(listOf(label, item)), StatementNode {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        if (label.identifierName in labelSet)
            return true
        return item.containsDuplicateLabels(labelSet + label.identifierName)
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return item.containsUndefinedBreakTarget(labelSet + label.identifierName)
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return item.containsUndefinedContinueTarget(iterationSet, labelSet + label.identifierName)
    }

    override fun lexicallyDeclaredNames(): List<String> {
//        if (item is FunctionDeclarationNode)
//            return item.boundNames()
        return emptyList()
    }

    override fun lexicallyScopedDeclarations(): List<NodeBase> {
//        if (item is FunctionDeclarationNode)
//            return listOf(item)
        return emptyList()
    }

    override fun topLevelLexicallyDeclaredNames(): List<String> {
        return emptyList()
    }

    override fun topLevelLexicallyScopedDeclarations(): List<NodeBase> {
        return emptyList()
    }

    override fun topLevelVarDeclaredNames(): List<String> {
        return item.topLevelVarDeclaredNames()
    }

    override fun topLevelVarScopedDeclarations(): List<NodeBase> {
        return item.topLevelVarScopedDeclarations()
    }

    override fun varDeclaredNames(): List<String> {
//        if (item is FunctionDeclarationNode)
//            return emptyList()
        return item.varDeclaredNames()
    }

    override fun varScopedDeclarations(): List<NodeBase> {
//        if (item is FunctionDeclarationNode)
//            return emptyList()
        return item.varScopedDeclarations()
    }
}

class ThrowStatementNode(val expr: ExpressionNode) : NodeBase(listOf(expr)), StatementNode

class TryStatementNode(
    val tryBlock: BlockNode,
    val catchNode: CatchNode
) : NodeBase(listOf(tryBlock, catchNode)), StatementNode {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        return tryBlock.containsDuplicateLabels(labelSet) || catchNode.containsDuplicateLabels(labelSet)
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return tryBlock.containsUndefinedBreakTarget(labelSet) ||
            catchNode.containsUndefinedBreakTarget(labelSet)
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return tryBlock.containsUndefinedContinueTarget(iterationSet, labelSet) ||
            catchNode.containsUndefinedContinueTarget(iterationSet, labelSet)
    }

    override fun varDeclaredNames() = tryBlock.varDeclaredNames() + catchNode.varDeclaredNames()

    override fun varScopedDeclarations() = tryBlock.varScopedDeclarations() + catchNode.varScopedDeclarations()
}

class CatchNode(
    val catchParameter: BindingIdentifierNode?,
    val block: BlockNode
) : NodeBase(listOfNotNull(catchParameter, block)) {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        return block.containsDuplicateLabels(labelSet)
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return block.containsUndefinedBreakTarget(labelSet)
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return block.containsUndefinedContinueTarget(iterationSet, emptySet())
    }

    override fun varDeclaredNames() = block.varDeclaredNames()

    override fun varScopedDeclarations() = block.varScopedDeclarations()
}

class BreakStatementNode(val label: LabelIdentifierNode?) : NodeBase(listOfNotNull(label)), StatementNode {
    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        if (label == null)
            return false
        return label.identifierName !in labelSet
    }
}
