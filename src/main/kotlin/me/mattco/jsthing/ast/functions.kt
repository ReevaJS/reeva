package me.mattco.jsthing.ast

import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.utils.stringBuilder

class ArgumentsNode(private val _argumentsList: ArgumentsListNode) : ASTNode() {
    val arguments: List<ArgumentListEntry>
        get() = _argumentsList.argumentsList

    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        _argumentsList.dump(indent + 1)
    }
}

class ArgumentsListNode(val argumentsList: List<ArgumentListEntry>) : ASTNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        argumentsList.forEach {
            appendIndent(indent + 1)
            append("ArgumentListEntry (isSpread=")
            append(it.isSpread)
            append(")\n")
            append(it.argument.dump(indent + 2))
        }
    }
}

data class ArgumentListEntry(val argument: ExpressionNode, val isSpread: Boolean)
