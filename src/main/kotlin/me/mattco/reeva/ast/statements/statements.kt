package me.mattco.reeva.ast.statements

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.literals.StringLiteralNode

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

    override fun topLevelVarDeclaredNames() = statements?.statements?.flatMap {
        if (it is LabelledStatementNode) {
            it.topLevelVarDeclaredNames()
        } else it.varDeclaredNames()
    } ?: emptyList()

    override fun topLevelVarScopedDeclarations() = statements?.statements?.flatMap {
        if (it is LabelledStatementNode) {
            it.topLevelVarScopedDeclarations()
        } else it.varScopedDeclarations()
    } ?: emptyList()

    override fun varDeclaredNames() = statements?.statements?.filter {
        it !is DeclarationNode
    }?.flatMap {
        it.varDeclaredNames()
    } ?: emptyList()

    override fun varScopedDeclarations() = statements?.statements?.filter {
        it !is DeclarationNode
    }?.flatMap {
        it.varScopedDeclarations()
    } ?: emptyList()
}

class StatementListNode(val statements: List<StatementNode>) : NodeBase(statements), StatementNode {
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
        return statements.flatMap(StatementNode::lexicallyDeclaredNames)
    }

    override fun lexicallyScopedDeclarations(): List<NodeBase> {
        return statements.flatMap(StatementNode::lexicallyScopedDeclarations)
    }

    override fun topLevelLexicallyDeclaredNames(): List<String> {
        return statements.flatMap(StatementNode::topLevelLexicallyDeclaredNames)
    }

    override fun topLevelLexicallyScopedDeclarations(): List<NodeBase> {
        return statements.flatMap(StatementNode::topLevelLexicallyScopedDeclarations)
    }

    override fun topLevelVarDeclaredNames(): List<String> {
        return statements.flatMap {
            when (it) {
                is LabelledStatementNode -> it.topLevelVarDeclaredNames()
                is DeclarationNode -> if (it is FunctionDeclarationNode) {
                    it.boundNames()
                } else emptyList()
                else -> it.varDeclaredNames()
            }
        }
    }

    override fun topLevelVarScopedDeclarations(): List<NodeBase> {
        return statements.flatMap {
            when (it) {
                is LabelledStatementNode -> it.topLevelVarScopedDeclarations()
                is DeclarationNode -> if (it is FunctionDeclarationNode) {
                    listOf(it.declarationPart())
                } else emptyList()
                else -> it.varScopedDeclarations()
            }
        }
    }

    override fun varDeclaredNames(): List<String> {
        return statements.flatMap(StatementNode::varDeclaredNames)
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return statements.flatMap(StatementNode::varScopedDeclarations)
    }

    fun hasUseStrictDirective(): Boolean {
        return statements.isNotEmpty() && statements[0].let {
            it is ExpressionStatementNode && it.node is StringLiteralNode && it.node.value == "use strict"
        }
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

class SwitchClauses(
    val clauses: List<SwitchClause>,
) : NodeBase(clauses) {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        return clauses.any { it.containsDuplicateLabels(labelSet) }
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return clauses.any { it.containsUndefinedBreakTarget(labelSet) }
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return clauses.any { it.containsUndefinedContinueTarget(iterationSet, emptySet()) }
    }

    override fun lexicallyDeclaredNames(): List<String> {
        return clauses.flatMap { it.lexicallyDeclaredNames() }
    }

    override fun lexicallyScopedDeclarations(): List<NodeBase> {
        return clauses.flatMap { it.lexicallyScopedDeclarations() }
    }

    override fun varDeclaredNames(): List<String> {
        return clauses.flatMap { it.varDeclaredNames() }
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return clauses.flatMap { it.varScopedDeclarations() }
    }
}

class SwitchStatementNode(
    val target: ExpressionNode,
    val clauses: SwitchClauses,
) : NodeBase(listOfNotNull()), BreakableStatement {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        return clauses.containsDuplicateLabels(labelSet)
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return clauses.containsUndefinedBreakTarget(labelSet)
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return clauses.containsUndefinedContinueTarget(iterationSet, labelSet)
    }

    override fun lexicallyDeclaredNames(): List<String> {
        return clauses.lexicallyDeclaredNames()
    }

    override fun lexicallyScopedDeclarations(): List<NodeBase> {
        return clauses.lexicallyScopedDeclarations()
    }

    override fun varDeclaredNames(): List<String> {
        return clauses.varDeclaredNames()
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return clauses.varScopedDeclarations()
    }
}

class SwitchClause(
    // null target indicates the default case
    val target: ExpressionNode?,
    val body: StatementListNode?,
) : NodeBase(listOfNotNull(target, body)) {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        return body?.containsDuplicateLabels(labelSet) == true
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return body?.containsUndefinedBreakTarget(labelSet) == true
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return body?.containsUndefinedContinueTarget(iterationSet, labelSet) == true
    }

    override fun lexicallyDeclaredNames(): List<String> {
        return body?.lexicallyDeclaredNames() ?: emptyList()
    }

    override fun lexicallyScopedDeclarations(): List<NodeBase> {
        return body?.lexicallyScopedDeclarations() ?: emptyList()
    }

    override fun varDeclaredNames(): List<String> {
        return body?.varDeclaredNames() ?: emptyList()
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return body?.varScopedDeclarations() ?: emptyList()
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
    val decl: ASTNode,
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
        if (decl is ForBindingNode)
            return decl.boundNames()
        return body.varDeclaredNames()
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        if (decl is ForBindingNode)
            return listOf(decl)
        return body.varScopedDeclarations()
    }
}

class ForOfNode(
    val decl: ASTNode,
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
        if (decl is ForBindingNode)
            return decl.boundNames()
        return body.varDeclaredNames()
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        if (decl is ForBindingNode)
            return listOf(decl)
        return body.varScopedDeclarations()
    }
}

class ForAwaitOfNode(
    val decl: ASTNode,
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
        if (decl is ForBindingNode)
            return decl.boundNames()
        return body.varDeclaredNames()
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        if (decl is ForBindingNode)
            return listOf(decl)
        return body.varScopedDeclarations()
    }
}

class ForBindingNode(val identifier: BindingIdentifierNode) : NodeBase(listOf(identifier)), ExpressionNode

class ForDeclarationNode(val isConst: Boolean, val binding: ForBindingNode) : NodeBase(listOf(binding))

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
    val catchNode: CatchNode?,
    val finallyBlock: BlockNode?,
) : NodeBase(listOfNotNull(tryBlock, catchNode, finallyBlock)), StatementNode {
    override fun containsDuplicateLabels(labelSet: Set<String>): Boolean {
        if (tryBlock.containsDuplicateLabels(labelSet))
            return true
        if (catchNode?.containsDuplicateLabels(labelSet) == true)
            return true
        if (finallyBlock?.containsDuplicateLabels(labelSet) == true)
            return true
        return false
    }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>): Boolean {
        return tryBlock.containsUndefinedBreakTarget(labelSet) ||
                catchNode?.containsDuplicateLabels(labelSet) == true ||
                finallyBlock?.containsDuplicateLabels(labelSet) == true
    }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return tryBlock.containsUndefinedContinueTarget(iterationSet, labelSet) ||
                catchNode?.containsUndefinedContinueTarget(iterationSet, labelSet) == true ||
                finallyBlock?.containsUndefinedContinueTarget(iterationSet, labelSet) == true
    }

    override fun varDeclaredNames(): List<String> {
        return tryBlock.varDeclaredNames() +
                (catchNode?.varDeclaredNames() ?: emptyList()) +
                (finallyBlock?.varDeclaredNames() ?: emptyList())
    }

    override fun varScopedDeclarations(): List<NodeBase> {
        return tryBlock.varScopedDeclarations() +
                (catchNode?.varScopedDeclarations() ?: emptyList()) +
                (finallyBlock?.varScopedDeclarations() ?: emptyList())
    }
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
        return label != null && label.identifierName !in labelSet
    }
}

class ContinueStatementNode(val label: LabelIdentifierNode?) : NodeBase(listOfNotNull(label)), StatementNode {
    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>): Boolean {
        return label != null && label.identifierName !in iterationSet
    }
}

class ReturnStatementNode(val expression: ExpressionNode?) : NodeBase(listOfNotNull(expression)), StatementNode
