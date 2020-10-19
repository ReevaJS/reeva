package me.mattco.jsthing.ast.statements

import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.utils.stringBuilder

class BlockNode(val statements: List<StatementNode>) : StatementNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        statements.forEach {
            append(it.dump(indent + 1))
        }
    }
}

class StatementListNode(val statements: List<StatementNode>) : StatementNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        statements.forEach {
            append(it.dump(indent + 1))
        }
    }
}

object EmptyStatementNode : StatementNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
    }
}

class ExpressionStatementNode(val node: ExpressionNode): StatementNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(node.dump(indent + 1))
    }
}

class IfStatementNode(
    val condition: ExpressionNode,
    val trueBlock: StatementNode,
    val falseBlock: StatementNode?
) : StatementNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(condition.dump(indent + 1))
        append(trueBlock.dump(indent + 1))
        falseBlock?.dump(indent + 1)?.also(::append)
    }
}

class DoWhileNode(val condition: ExpressionNode, val body: StatementNode) : StatementNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(condition.dump(indent + 1))
        append(body.dump(indent + 1))
    }
}


class WhileNode(val condition: ExpressionNode, val body: StatementNode) : StatementNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(condition.dump(indent + 1))
        append(body.dump(indent + 1))
    }
}

class ForNode(
    val initializer: StatementNode?,
    val condition: ExpressionNode?,
    val incrementer: ExpressionNode?,
    val body: StatementNode,
) : StatementNode() {
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

class ForInNode(val decl: StatementNode, val expression: ExpressionNode) : StatementNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(decl.dump(indent + 1))
        append(expression.dump(indent + 1))
    }
}

class ForOfNode(val decl: StatementNode, val expression: ExpressionNode) : StatementNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(decl.dump(indent + 1))
        append(expression.dump(indent + 1))
    }
}

class ForAwaitOfNode(val decl: StatementNode, val expression: ExpressionNode) : StatementNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(decl.dump(indent + 1))
        append(expression.dump(indent + 1))
    }
}
