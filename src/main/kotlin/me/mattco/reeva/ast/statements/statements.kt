package me.mattco.reeva.ast.statements

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.parser.Scope

interface Labellable {
    val labels: MutableSet<String>
}

abstract class LabellableBase(children: List<ASTNode>) : ASTNodeBase(children), StatementNode, Labellable {
    override val labels: MutableSet<String> = mutableSetOf()
}

typealias StatementList = ASTListNode<StatementNode>
typealias SwitchClauseList = ASTListNode<SwitchClause>

open class ASTListNode<T : ASTNode>(
    statements: List<T> = emptyList()
) : ASTNodeBase(statements), List<T> by statements

class BlockStatementNode(val block: BlockNode) : ASTNodeBase(listOf(block)), StatementNode

class BlockNode(val statements: StatementList, val hasUseStrict: Boolean) : NodeWithScope(statements), StatementNode, Labellable {
    override var labels: MutableSet<String> = mutableSetOf()
}

class EmptyStatementNode : ASTNodeBase(), StatementNode

class ExpressionStatementNode(val node: ExpressionNode): ASTNodeBase(listOf(node)), StatementNode

class IfStatementNode(
    val condition: ExpressionNode,
    val trueBlock: StatementNode,
    val falseBlock: StatementNode?
) : LabellableBase(listOfNotNull(condition, trueBlock, falseBlock)), StatementNode

class DoWhileStatementNode(
    val condition: ExpressionNode,
    val body: StatementNode
) : LabellableBase(listOf(condition, body)), StatementNode

class WhileStatementNode(
    val condition: ExpressionNode,
    val body: StatementNode
) : LabellableBase(listOf(condition, body)), StatementNode

class WithStatementNode(
    val expression: ExpressionNode,
    val body: StatementNode,
) : ASTNodeBase(listOf(expression, body)), StatementNode

class SwitchStatementNode(
    val target: ExpressionNode,
    val clauses: SwitchClauseList,
) : LabellableBase(listOfNotNull()), StatementNode

class SwitchClause(
    // null target indicates the default case
    val target: ExpressionNode?,
    val body: StatementList?,
) : LabellableBase(listOfNotNull(target) + (body ?: emptyList())), StatementNode

class ForStatementNode(
    val initializer: ASTNode?,
    val condition: ExpressionNode?,
    val incrementer: ExpressionNode?,
    val body: StatementNode,
) : LabellableBase(listOfNotNull(initializer, condition, incrementer, body)), StatementNode {
    var initializerScope: Scope? = null

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
) : LabellableBase(listOf(decl, expression, body)), StatementNode

class ForOfNode(
    val decl: ASTNode,
    val expression: ExpressionNode,
    val body: StatementNode
) : LabellableBase(listOf(decl, expression, body)), StatementNode

class ForAwaitOfNode(
    val decl: ASTNode,
    val expression: ExpressionNode,
    val body: StatementNode
) : LabellableBase(listOf(decl, expression, body)), StatementNode

class ThrowStatementNode(val expr: ExpressionNode) : ASTNodeBase(listOf(expr)), StatementNode

class TryStatementNode(
    val tryBlock: BlockNode,
    val catchNode: CatchNode?,
    val finallyBlock: BlockNode?,
) : LabellableBase(listOfNotNull(tryBlock, catchNode, finallyBlock)), StatementNode {
    init {
        if (catchNode == null && finallyBlock == null)
            throw IllegalArgumentException()
    }
}

class CatchNode(
    val parameter: BindingIdentifierNode?,
    val block: BlockNode
) : NodeWithScope(listOfNotNull(parameter, block))

class BreakStatementNode(val label: String?) : ASTNodeBase(), StatementNode

class ContinueStatementNode(val label: String?) : ASTNodeBase(), StatementNode

class ReturnStatementNode(val expression: ExpressionNode?) : ASTNodeBase(listOfNotNull(expression)), StatementNode
