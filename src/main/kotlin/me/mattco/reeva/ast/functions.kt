package me.mattco.reeva.ast

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.statements.ASTListNode
import me.mattco.reeva.ast.statements.ReturnStatementNode
import me.mattco.reeva.ast.statements.StatementList

typealias ArgumentList = ASTListNode<Argument>

class ParameterList(parameters: List<Parameter> = emptyList()) : ASTListNode<Parameter>(parameters) {
    fun isSimple(): Boolean {
        // TODO: Eventually check for destructuring patterns
        return all { it.initializer == null && !it.isRest }
    }

    fun containsDuplicates(): Boolean {
        return distinctBy { it.identifier.identifierName }.size == size
    }
}

class Argument(val expression: ExpressionNode, val isSpread: Boolean) : ASTNodeBase(listOf(expression)) {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (isSpread=")
        append(isSpread)
        append(")\n")
        append(expression.dump(indent + 1))
    }
}

class Parameter(
    val identifier: BindingIdentifierNode,
    val initializer: ExpressionNode?,
    val isRest: Boolean
) : VariableSourceNode(listOfNotNull(identifier, initializer)) {
    init {
        if (isRest && initializer != null)
            throw IllegalArgumentException()
    }

    override fun boundNames() = listOf(identifier.identifierName)
}

open class GenericFunctionDeclarationNode(
    val identifier: BindingIdentifierNode,
    val parameters: ParameterList,
    val body: StatementList,
) : VariableSourceNode(listOf(identifier) + body + parameters), StatementNode {
    override fun boundNames() = listOf(identifier.identifierName)
}

class FunctionDeclarationNode(
    identifier: BindingIdentifierNode,
    parameters: ParameterList,
    body: StatementList,
) : GenericFunctionDeclarationNode(identifier, parameters, body)

class FunctionExpressionNode(
    val identifier: BindingIdentifierNode?,
    val parameters: ParameterList,
    val body: StatementList,
) : NodeWithScope(listOfNotNull(identifier) + parameters + body), ExpressionNode

class ArrowFunctionNode(
    val parameters: ParameterList,
    val body: StatementList
) : NodeWithScope(parameters + body), ExpressionNode {
    companion object {
        fun fromExpressionBody(parameters: ParameterList, expr: ExpressionNode) =
            ArrowFunctionNode(parameters, StatementList(listOf(ReturnStatementNode(expr))))
    }
}
