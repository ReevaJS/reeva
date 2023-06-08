package com.reevajs.reeva.ast.statements

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.ast.AstNode.Companion.appendIndent
import com.reevajs.reeva.parsing.Scope

interface Labellable {
    val labels: MutableSet<String>
}

abstract class LabellableBase(children: List<AstNode>) : AstNodeBase(children), Labellable {
    override val labels: MutableSet<String> = mutableSetOf()
}

class BlockStatementNode(val block: BlockNode) : AstNodeBase(listOf(block))

// useStrict is an AstNode so that we can point to it during errors in case it is
// invalid (i.e. in functions with non-simple parameter lists).
class BlockNode(
    val statements: List<AstNode>,
    val useStrict: AstNode?,
) : NodeWithScope(statements), Labellable {
    val hasUseStrict: Boolean get() = useStrict != null

    override var labels: MutableSet<String> = mutableSetOf()
}

class EmptyStatementNode : AstNodeBase()

class ExpressionStatementNode(val node: AstNode) : AstNodeBase(listOf(node))

class IfStatementNode(
    val condition: AstNode,
    val trueBlock: AstNode,
    val falseBlock: AstNode?
) : LabellableBase(listOfNotNull(condition, trueBlock, falseBlock))

class DoWhileStatementNode(
    val condition: AstNode,
    val body: AstNode
) : LabellableBase(listOf(condition, body))

class WhileStatementNode(
    val condition: AstNode,
    val body: AstNode
) : LabellableBase(listOf(condition, body))

class WithStatementNode(
    val expression: AstNode,
    val body: AstNode,
) : AstNodeBase(listOf(expression, body))

class SwitchStatementNode(
    val target: AstNode,
    val clauses: List<SwitchClause>,
) : LabellableBase(listOfNotNull())

class SwitchClause(
    // null target indicates the default case
    val target: AstNode?,
    val body: List<AstNode>?,
) : LabellableBase(listOfNotNull(target) + (body ?: emptyList()))

class ForStatementNode(
    val initializer: AstNode?,
    val condition: AstNode?,
    val incrementer: AstNode?,
    val body: AstNode,
) : LabellableBase(listOfNotNull(initializer, condition, incrementer, body)) {
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
    val expression: AstNode,
    val body: AstNode
) : LabellableBase(listOf(decl, expression, body)) {
    var initializerScope: Scope? = null
}

class ForInNode(
    decl: AstNode,
    expression: AstNode,
    body: AstNode
) : ForEachNode(decl, expression, body)

class ForOfNode(
    decl: AstNode,
    expression: AstNode,
    body: AstNode
) : ForEachNode(decl, expression, body)

class ForAwaitOfNode(
    decl: AstNode,
    expression: AstNode,
    body: AstNode
) : ForEachNode(decl, expression, body)

class ThrowStatementNode(val expr: AstNode) : AstNodeBase(listOf(expr))

class TryStatementNode(
    val tryBlock: BlockNode,
    val catchNode: CatchNode?,
    val finallyBlock: BlockNode?,
) : LabellableBase(listOfNotNull(tryBlock, catchNode, finallyBlock)) {
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

class BreakStatementNode(val label: String?) : AstNodeBase()

class ContinueStatementNode(val label: String?) : AstNodeBase()

class ReturnStatementNode(val expression: AstNode?) : AstNodeBase(listOfNotNull(expression))

class DebuggerStatementNode : AstNodeBase()
