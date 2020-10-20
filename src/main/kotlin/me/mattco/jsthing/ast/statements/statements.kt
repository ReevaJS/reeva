package me.mattco.jsthing.ast.statements

import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.utils.stringBuilder

class BlockStatementNode(val block: BlockNode) : StatementNode(listOf(block))

class BlockNode(val statements: StatementListNode) : StatementNode(listOf(statements))

class StatementListNode(val statements: List<StatementListItem>) : StatementNode(statements)

class StatementListItem(val item: StatementNode) : StatementNode(listOf(item))

object EmptyStatementNode : StatementNode()

class ExpressionStatementNode(val node: ExpressionNode): StatementNode(listOf(node)) {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(node.dump(indent + 1))
    }
}

class IfStatementNode(
    val condition: ExpressionNode,
    val trueBlock: StatementNode,
    val falseBlock: StatementNode?
) : StatementNode(listOfNotNull(condition, trueBlock, falseBlock))

class DoWhileNode(val condition: ExpressionNode, val body: StatementNode) : StatementNode(listOf(condition, body))


class WhileNode(val condition: ExpressionNode, val body: StatementNode) : StatementNode(listOf(condition, body))

class ForNode(
    val initializer: StatementNode?,
    val condition: ExpressionNode?,
    val incrementer: ExpressionNode?,
    val body: StatementNode,
) : StatementNode(listOfNotNull(initializer, condition, incrementer, body)) {
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

class ForInNode(val decl: StatementNode, val expression: ExpressionNode) : StatementNode(listOf(decl, expression))

class ForOfNode(val decl: StatementNode, val expression: ExpressionNode) : StatementNode(listOf(decl, expression))

class ForAwaitOfNode(val decl: StatementNode, val expression: ExpressionNode) : StatementNode(listOf(decl, expression))
