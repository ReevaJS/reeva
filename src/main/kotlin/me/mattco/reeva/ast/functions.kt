package me.mattco.reeva.ast

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.statements.ASTListNode
import me.mattco.reeva.ast.statements.BlockNode

typealias ArgumentList = ASTListNode<ArgumentNode>

class ParameterList(parameters: List<Parameter> = emptyList()) : ASTListNode<Parameter>(parameters) {
    fun isSimple(): Boolean {
        // TODO: Eventually check for destructuring patterns
        return all { it.initializer == null && !it.isRest }
    }

    fun containsDuplicates(): Boolean {
        return distinctBy { it.identifier.identifierName }.size == size
    }
}

class ArgumentNode(
    val expression: ExpressionNode,
    val isSpread: Boolean
) : ASTNodeBase(listOf(expression)), ExpressionNode {
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
}

open class GenericFunctionDeclarationNode(
    val identifier: BindingIdentifierNode?, // Null if in a default export
    val parameters: ParameterList,
    val body: BlockNode,
) : VariableSourceNode(listOfNotNull(identifier) + parameters + body), StatementNode

class FunctionDeclarationNode(
    identifier: BindingIdentifierNode?,
    parameters: ParameterList,
    body: BlockNode,
) : GenericFunctionDeclarationNode(identifier, parameters, body)

class FunctionExpressionNode(
    val identifier: BindingIdentifierNode?,
    val parameters: ParameterList,
    val body: BlockNode,
) : NodeWithScope(listOfNotNull(identifier) + parameters + body), ExpressionNode

class ArrowFunctionNode(
    val parameters: ParameterList,
    val body: ASTNode, // BlockNode or ExpressionNode
) : NodeWithScope(parameters + body), ExpressionNode
