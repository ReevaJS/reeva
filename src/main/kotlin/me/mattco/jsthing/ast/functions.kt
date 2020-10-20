package me.mattco.jsthing.ast

import me.mattco.jsthing.ast.ASTNode.Companion.appendIndent
import me.mattco.jsthing.utils.stringBuilder

// This is an ExpressionNode so it can be passed to MemberExpressionNode
class ArgumentsNode(private val _argumentsList: ArgumentsListNode) : NodeBase(listOf(_argumentsList)), ExpressionNode {
    val arguments: List<ArgumentListEntry>
        get() = _argumentsList.argumentsList
}

class ArgumentsListNode(val argumentsList: List<ArgumentListEntry>) : NodeBase(argumentsList) {
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

data class ArgumentListEntry(val argument: ExpressionNode, val isSpread: Boolean) : NodeBase(listOf(argument)) {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (isSpread=")
        append(isSpread)
        append(")\n")
        append(argument.dump(indent + 1))
    }
}
