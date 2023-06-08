package com.reevajs.reeva.ast.statements

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.ast.AstNode.Companion.appendIndent
import com.reevajs.reeva.parsing.Scope

interface Labellable {
    val labels: MutableSet<String>
}

abstract class LabellableBase : AstNodeBase(), Labellable {
    override val labels: MutableSet<String> = mutableSetOf()
}

class BlockStatementNode(val block: BlockNode) : AstNodeBase() {
    override val children get() = listOf(block)
}

// useStrict is an AstNode so that we can point to it during errors in case it is
// invalid (i.e. in functions with non-simple parameter lists).
class BlockNode(val statements: List<AstNode>, val useStrict: AstNode?) : NodeWithScope(), Labellable {
    override val children get() = statements

    val hasUseStrict: Boolean get() = useStrict != null

    override var labels: MutableSet<String> = mutableSetOf()
}

class EmptyStatementNode : AstNodeBase() {
    override val children get() = emptyList<AstNode>()
}

class ExpressionStatementNode(val node: AstNode) : AstNodeBase() {
    override val children get() = listOf(node)
}

class IfStatementNode(val condition: AstNode, val trueBlock: AstNode, val falseBlock: AstNode?) : LabellableBase() {
    override val children get() = listOfNotNull(condition, trueBlock, falseBlock)
}

class DoWhileStatementNode(val condition: AstNode, val body: AstNode) : LabellableBase() {
    override val children get() = listOf(condition, body)
}

class WhileStatementNode(val condition: AstNode, val body: AstNode) : LabellableBase() {
    override val children get() = listOf(condition, body)
}

class WithStatementNode(val expression: AstNode, val body: AstNode) : AstNodeBase() {
    override val children get() = listOf(expression, body)
}

class SwitchStatementNode(val target: AstNode, val clauses: List<SwitchClause>) : LabellableBase() {
    override val children get() = listOf(target) + clauses
}

class SwitchClause(
    // null target indicates the default case
    val target: AstNode?,
    val body: List<AstNode>?,
) : LabellableBase() {
    override val children get() = listOfNotNull(target) + body.orEmpty()
}

class ForStatementNode(
    val initializer: AstNode?,
    val condition: AstNode?,
    val incrementer: AstNode?,
    val body: AstNode,
) : LabellableBase() {
    override val children get() = listOfNotNull(initializer, condition, incrementer, body)

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

sealed class ForEachNode(val decl: AstNode, val expression: AstNode, val body: AstNode) : LabellableBase() {
    override val children get() = listOf(decl, expression, body)

    var initializerScope: Scope? = null
}

class ForInNode(decl: AstNode, expression: AstNode, body: AstNode) : ForEachNode(decl, expression, body)

class ForOfNode(decl: AstNode, expression: AstNode, body: AstNode) : ForEachNode(decl, expression, body)

class ForAwaitOfNode(decl: AstNode, expression: AstNode, body: AstNode) : ForEachNode(decl, expression, body)

class ThrowStatementNode(val expr: AstNode) : AstNodeBase() {
    override val children get() = listOf(expr)
}

class TryStatementNode(
    val tryBlock: BlockNode,
    val catchNode: CatchNode?,
    val finallyBlock: BlockNode?,
) : LabellableBase() {
    override val children get() = listOfNotNull(tryBlock, catchNode, finallyBlock)

    init {
        if (catchNode == null && finallyBlock == null)
            throw IllegalArgumentException()
    }
}

class CatchNode(val parameter: CatchParameter?, val block: BlockNode) : NodeWithScope() {
    override val children get() = listOfNotNull(parameter, block)
}

class CatchParameter(val declaration: BindingDeclarationOrPattern) : AstNodeBase() {
    override val children get() = listOf(declaration)
}

class BreakStatementNode(val label: String?) : AstNodeBase() {
    override val children get() = emptyList<AstNode>()
}

class ContinueStatementNode(val label: String?) : AstNodeBase() {
    override val children get() = emptyList<AstNode>()
}

class ReturnStatementNode(val expression: AstNode?) : AstNodeBase() {
    override val children get() = listOfNotNull(expression)
}

class DebuggerStatementNode : AstNodeBase() {
    override val children get() = emptyList<AstNode>()
}
