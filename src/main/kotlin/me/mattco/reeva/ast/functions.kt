package me.mattco.reeva.ast

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.statements.StatementListNode
import me.mattco.reeva.utils.stringBuilder

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
            append(it.expression.dump(indent + 2))
        }
    }
}

data class ArgumentListEntry(val expression: ExpressionNode, val isSpread: Boolean) : NodeBase(listOf(expression)) {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (isSpread=")
        append(isSpread)
        append(")\n")
        append(expression.dump(indent + 1))
    }
}

class FunctionDeclarationNode(
    val identifier: BindingIdentifierNode?,
    val parameters: FormalParametersNode,
    val body: StatementListNode?,
) : NodeBase(listOfNotNull(identifier, parameters, body)), DeclarationNode

class FormalParametersNode(val parameters: List<FormalParameter>) : NodeBase() {
    override fun dump(indent: Int) = stringBuilder {
        parameters.forEach {
            appendIndent(indent)
            append("Parameter (type=")
            append(it.type.name)
            append(")\n")
            append(it.first.dump(indent + 1))
            it.second?.dump(indent + 1)?.also(::append)
        }
    }
}

data class FormalParameter(
    val first: ExpressionNode,
    val second: ExpressionNode?,
    val type: Type,
) {
    enum class Type {
        Normal,
        Rest,
    }
}

class ReturnNode(val node: ExpressionNode?) : NodeBase(listOfNotNull(node)), StatementNode
