package me.mattco.reeva.ast.statements

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.literals.StringLiteralNode

typealias StatementList = ASTListNode<StatementNode>
typealias SwitchClauseList = ASTListNode<SwitchClause>

open class ASTListNode<T : ASTNode>(
    statements: List<T> = emptyList()
) : ASTNodeBase(statements), List<T> by statements

class BlockStatementNode(val block: BlockNode) : ASTNodeBase(listOf(block)), StatementNode

class BlockNode(val statements: StatementList) : NodeWithScope(statements), StatementNode {
    override fun containsDirective(directive: String): Boolean {
        if (statements.isEmpty())
            return false

        val statement = statements.first()
        if (statement !is ExpressionStatementNode)
            return false

        return statement.node.let { it is StringLiteralNode && it.value == directive }
    }
}

object EmptyStatementNode : ASTNodeBase(), StatementNode

class ExpressionStatementNode(val node: ExpressionNode): ASTNodeBase(listOf(node)), StatementNode

class IfStatementNode(
    val condition: ExpressionNode,
    val trueBlock: StatementNode,
    val falseBlock: StatementNode?
) : ASTNodeBase(listOfNotNull(condition, trueBlock, falseBlock)), StatementNode

class DoWhileStatementNode(
    val condition: ExpressionNode,
    val body: StatementNode
) : ASTNodeBase(listOf(condition, body)), StatementNode

class WhileStatementNode(
    val condition: ExpressionNode,
    val body: StatementNode
) : ASTNodeBase(listOf(condition, body)), StatementNode

class SwitchStatementNode(
    val target: ExpressionNode,
    val clauses: SwitchClauseList,
) : ASTNodeBase(listOfNotNull()), StatementNode

class SwitchClause(
    // null target indicates the default case
    val target: ExpressionNode?,
    val body: StatementList,
) : ASTNodeBase(listOfNotNull(target) + body), StatementNode

class ForStatementNode(
    val initializer: StatementNode?,
    val condition: ExpressionNode?,
    val incrementer: ExpressionNode?,
    val body: StatementNode,
) : ASTNodeBase(listOfNotNull(initializer, condition, incrementer, body)), StatementNode {
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
) : ASTNodeBase(listOf(decl, expression, body)), StatementNode

class ForOfNode(
    val decl: StatementNode,
    val expression: ExpressionNode,
    val body: StatementNode
) : ASTNodeBase(listOf(decl, expression, body)), StatementNode

class ForAwaitOfNode(
    val decl: StatementNode,
    val expression: ExpressionNode,
    val body: StatementNode
) : ASTNodeBase(listOf(decl, expression, body)), StatementNode

class LabelledStatementNode(
    val label: String,
    val item: StatementNode
) : ASTNodeBase(listOf(item)), StatementNode

class ThrowStatementNode(val expr: ExpressionNode) : ASTNodeBase(listOf(expr)), StatementNode

class TryStatementNode(
    val tryBlock: BlockNode,
    val catchNode: CatchNode?,
    val finallyBlock: BlockNode?,
) : ASTNodeBase(listOfNotNull(tryBlock, catchNode, finallyBlock)), StatementNode {
    init {
        if (catchNode == null && finallyBlock == null)
            throw IllegalArgumentException()
    }
}

class CatchNode(
    val catchParameter: BindingIdentifierNode?,
    val block: BlockNode
) : ASTNodeBase(listOfNotNull(catchParameter, block))

class BreakStatementNode(val label: String?) : ASTNodeBase(), StatementNode

class ContinueStatementNode(val label: String?) : ASTNodeBase(), StatementNode

class ReturnStatementNode(val expression: ExpressionNode?) : ASTNodeBase(listOfNotNull(expression)), StatementNode
