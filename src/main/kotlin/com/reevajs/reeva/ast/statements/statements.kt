package com.reevajs.reeva.ast.statements

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.ast.AstNode.Companion.appendIndent
import com.reevajs.reeva.parsing.Scope

interface Labellable {
    val labels: MutableSet<String>
}

abstract class LabellableBase(children: List<AstNode>) : AstNodeBase(children), StatementNode, Labellable {
    override val labels: MutableSet<String> = mutableSetOf()
}

typealias StatementList = AstListNode<StatementNode>
typealias SwitchClauseList = AstListNode<SwitchClause>

open class AstListNode<T : AstNode>(
    statements: List<T> = emptyList()
) : AstNodeBase(statements), List<T> by statements

class BlockStatementNode(val block: BlockNode) : AstNodeBase(listOf(block)), StatementNode

// useStrict is an AstNode so that we can point to it during errors in case it is
// invalid (i.e. in functions with non-simple parameter lists).
class BlockNode(
    val statements: StatementList,
    val useStrict: AstNode?,
) : NodeWithScope(statements), StatementNode, Labellable {
    val hasUseStrict: Boolean get() = useStrict != null

    override var labels: MutableSet<String> = mutableSetOf()
}

class EmptyStatementNode : AstNodeBase(), StatementNode

class ExpressionStatementNode(val node: ExpressionNode) : AstNodeBase(listOf(node)), StatementNode

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
) : AstNodeBase(listOf(expression, body)), StatementNode

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
    val initializer: AstNode?,
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

sealed class ForEachNode(
    val decl: AstNode,
    val expression: ExpressionNode,
    val body: StatementNode
) : LabellableBase(listOf(decl, expression, body)), StatementNode {
    var initializerScope: Scope? = null
}

class ForInNode(
    decl: AstNode,
    expression: ExpressionNode,
    body: StatementNode
) : ForEachNode(decl, expression, body)

class ForOfNode(
    decl: AstNode,
    expression: ExpressionNode,
    body: StatementNode
) : ForEachNode(decl, expression, body)

class ForAwaitOfNode(
    decl: AstNode,
    expression: ExpressionNode,
    body: StatementNode
) : ForEachNode(decl, expression, body)

class ThrowStatementNode(val expr: ExpressionNode) : AstNodeBase(listOf(expr)), StatementNode

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
    val parameter: CatchParameter?,
    val block: BlockNode
) : NodeWithScope(listOfNotNull(parameter, block))

class CatchParameter(
    val declaration: BindingDeclarationOrPattern,
) : AstNodeBase()

class BreakStatementNode(val label: String?) : AstNodeBase(), StatementNode

class ContinueStatementNode(val label: String?) : AstNodeBase(), StatementNode

class ReturnStatementNode(val expression: ExpressionNode?) : AstNodeBase(listOfNotNull(expression)), StatementNode

class DebuggerStatementNode : AstNodeBase(), StatementNode
