package me.mattco.jsthing.ast.statements

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.LabelIdentifierNode
import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.utils.stringBuilder

class BlockStatementNode(val block: BlockNode) : StatementNode(listOf(block))

class BlockNode(val statements: StatementListNode?) : StatementNode(listOfNotNull(statements)) {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        if (statements == null)
            return false
        return super.containsDuplicateLabels(labelSet)
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        if (statements == null)
            return false
        return super.containsUndefinedBreakTarget(labelSet)
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        if (statements == null)
            return false
        return super.containsUndefinedContinueTarget(iterationSet, labelSet)
    }

    override fun lexicallyDeclaredNames(): List<String> {
        if (statements == null)
            return emptyList()
        return super.lexicallyDeclaredNames()
    }

    override fun topLevelLexicallyScopedDeclarations() = emptyList<ASTNode>()

    override fun topLevelVarDeclaredNames() = emptyList<String>()

    override fun topLevelVarScopedDeclarations() = emptyList<ASTNode>()

    override fun varDeclaredNames() = emptyList<String>()

    override fun varScopedDeclarations() = emptyList<ASTNode>()
}

class StatementListNode(val statements: List<StatementListItem>) : StatementNode(statements) {
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
        return statements.flatMap(ASTNode::lexicallyDeclaredNames)
    }

    override fun lexicallyScopedDeclarations(): List<ASTNode> {
        return statements.flatMap(ASTNode::lexicallyScopedDeclarations)
    }

    override fun topLevelLexicallyDeclaredNames(): List<String> {
        return statements.flatMap(ASTNode::topLevelLexicallyDeclaredNames)
    }

    override fun topLevelLexicallyScopedDeclarations(): List<ASTNode> {
        return statements.flatMap(ASTNode::topLevelLexicallyScopedDeclarations)
    }

    override fun topLevelVarDeclaredNames(): List<String> {
        return statements.flatMap(ASTNode::topLevelVarDeclaredNames)
    }

    override fun topLevelVarScopedDeclarations(): List<ASTNode> {
        return statements.flatMap(ASTNode::topLevelVarScopedDeclarations)
    }

    override fun varDeclaredNames(): List<String> {
        return statements.flatMap(ASTNode::varDeclaredNames)
    }

    override fun varScopedDeclarations(): List<ASTNode> {
        return statements.flatMap(ASTNode::varScopedDeclarations)
    }
}

class StatementListItem(val item: StatementNode) : StatementNode(listOf(item)) {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        if (item is DeclarationNode)
            return false
        return super.containsDuplicateLabels(labelSet)
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        if (item is DeclarationNode)
            return false
        return super.containsUndefinedBreakTarget(labelSet)
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        if (item is DeclarationNode)
            return false
        return super.containsUndefinedContinueTarget(iterationSet, labelSet)
    }

    override fun lexicallyDeclaredNames(): List<String> {
        if (item is DeclarationNode)
            return item.boundNames()
        if (item is LabelledStatement)
            return item.lexicallyDeclaredNames()
        return emptyList()
    }

    override fun lexicallyScopedDeclarations(): List<ASTNode> {
        if (item is DeclarationNode)
            TODO()
        if (item is LabelledStatement)
            return item.lexicallyScopedDeclarations()
        return emptyList()
    }

    override fun topLevelLexicallyDeclaredNames(): List<String> {
        if (item is HoistableDeclarationNode)
            return emptyList()
        if (item is DeclarationNode)
            return item.boundNames()
        return emptyList()
    }

    override fun topLevelLexicallyScopedDeclarations(): List<ASTNode> {
        if (item is HoistableDeclarationNode)
            return emptyList()
        if (item is DeclarationNode)
            return listOf(item)
        return emptyList()
    }

    override fun topLevelVarDeclaredNames(): List<String> {
        if (item is HoistableDeclarationNode)
            return item.boundNames()
        if (item is DeclarationNode)
            return emptyList()
        if (item is LabelledStatement)
            return item.topLevelVarDeclaredNames()
        return item.varDeclaredNames()
    }

    override fun topLevelVarScopedDeclarations(): List<ASTNode> {
        if (item is HoistableDeclarationNode)
            TODO()
        if (item is DeclarationNode)
            return emptyList()
        if (item is LabelledStatement)
            return item.topLevelVarScopedDeclarations()
        return item.varScopedDeclarations()
    }

    override fun varDeclaredNames(): List<String> {
        if (item is DeclarationNode)
            return emptyList()
        return super.varDeclaredNames()
    }

    override fun varScopedDeclarations(): List<ASTNode> {
        if (item is DeclarationNode)
            return emptyList()
        return super.varScopedDeclarations()
    }
}

object EmptyStatementNode : StatementNode() {
    override fun containsDuplicateLabels(labelSet: Set<String>) = false

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>) = false

    override fun varDeclaredNames() = emptyList<String>()

    override fun varScopedDeclarations() = emptyList<ASTNode>()
}

class ExpressionStatementNode(val node: ExpressionNode): StatementNode(listOf(node)) {
    override fun containsDuplicateLabels(labelSet: Set<String>) = false

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>) = false

    override fun varDeclaredNames() = emptyList<String>()

    override fun varScopedDeclarations() = emptyList<ASTNode>()

    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(node.dump(indent + 1))
    }
}

class IfStatementNode(
    val condition: ExpressionNode,
    val trueBlock: StatementNode,
    val falseBlock: StatementNode?
) : StatementNode(listOfNotNull(condition, trueBlock, falseBlock)) {
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

    override fun varScopedDeclarations(): List<ASTNode> {
        return trueBlock.varScopedDeclarations() + (falseBlock?.varScopedDeclarations() ?: emptyList())
    }
}

class DoWhileNode(val condition: ExpressionNode, val body: StatementNode) : StatementNode(listOf(condition, body)) {
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

    override fun varScopedDeclarations(): List<ASTNode> {
        return body.varScopedDeclarations()
    }
}

class WhileNode(val condition: ExpressionNode, val body: StatementNode) : StatementNode(listOf(condition, body)) {
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

    override fun varScopedDeclarations(): List<ASTNode> {
        return body.varScopedDeclarations()
    }
}

class ForNode(
    val initializer: StatementNode?,
    val condition: ExpressionNode?,
    val incrementer: ExpressionNode?,
    val body: StatementNode,
) : StatementNode(listOfNotNull(initializer, condition, incrementer, body)) {
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

    override fun varScopedDeclarations(): List<ASTNode> {
        return body.varScopedDeclarations()
    }

    override fun dump(indent: Int) = stringBuilder {
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
) : StatementNode(listOf(decl, expression)) {
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

    override fun varScopedDeclarations(): List<ASTNode> {
        return body.varScopedDeclarations()
    }
}

class ForOfNode(
    val decl: StatementNode,
    val expression: ExpressionNode,
    val body: StatementNode
) : StatementNode(listOf(decl, expression)) {
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

    override fun varScopedDeclarations(): List<ASTNode> {
        return body.varScopedDeclarations()
    }
}

class ForAwaitOfNode(
    val decl: StatementNode,
    val expression: ExpressionNode,
     val body: StatementNode
) : StatementNode(listOf(decl, expression)) {
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

    override fun varScopedDeclarations(): List<ASTNode> {
        return body.varScopedDeclarations()
    }
}

class LabelledStatement(val label: LabelIdentifierNode, val item: StatementNode) : StatementNode(listOf(label, item)) {
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

    override fun lexicallyScopedDeclarations(): List<ASTNode> {
//        if (item is FunctionDeclarationNode)
//            return listOf(item)
        return emptyList()
    }

    override fun topLevelLexicallyDeclaredNames(): List<String> {
        return emptyList()
    }

    override fun topLevelLexicallyScopedDeclarations(): List<ASTNode> {
        return emptyList()
    }

    override fun topLevelVarDeclaredNames(): List<String> {
        return item.topLevelVarDeclaredNames()
    }

    override fun topLevelVarScopedDeclarations(): List<ASTNode> {
        return item.topLevelVarScopedDeclarations()
    }

    override fun varDeclaredNames(): List<String> {
//        if (item is FunctionDeclarationNode)
//            return emptyList()
        return item.varDeclaredNames()
    }

    override fun varScopedDeclarations(): List<ASTNode> {
//        if (item is FunctionDeclarationNode)
//            return emptyList()
        return item.varScopedDeclarations()
    }
}
