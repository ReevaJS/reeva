package me.mattco.reeva.ast

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.statements.ASTListNode
import me.mattco.reeva.ast.statements.BlockNode
import me.mattco.reeva.parser.Scope
import me.mattco.reeva.runtime.Operations

typealias ArgumentList = ASTListNode<ArgumentNode>

class ParameterList(parameters: List<Parameter> = emptyList()) : ASTListNode<Parameter>(parameters) {
    fun isSimple(): Boolean {
        // TODO: Eventually check for destructuring patterns
        return all { it.initializer == null && !it.isRest }
    }

    fun containsDuplicates(): Boolean {
        return distinctBy { it.identifier.identifierName }.size != size
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
    override var variable by identifier::variable

    init {
        if (isRest && initializer != null)
            throw IllegalArgumentException()
    }
}

class FunctionDeclarationNode(
    val identifier: BindingIdentifierNode,
    val parameters: ParameterList,
    val body: BlockNode,
    val kind: Operations.FunctionKind,
) : VariableSourceNode(listOfNotNull(identifier) + parameters + body), StatementNode {
    override var variable by identifier::variable

    // May be equal to body.scope if parameters.isSimple() == true
    lateinit var functionScope: Scope
}

class FunctionExpressionNode(
    val identifier: BindingIdentifierNode?,
    val parameters: ParameterList,
    val body: BlockNode,
    val kind: Operations.FunctionKind,
) : NodeWithScope(listOfNotNull(identifier) + parameters + body), ExpressionNode {
    // May be equal to body.scope if parameters.isSimple() == true
    lateinit var functionScope: Scope
}

class ArrowFunctionNode(
    val parameters: ParameterList,
    val body: ASTNode, // BlockNode or ExpressionNode
    val kind: Operations.FunctionKind,
) : NodeWithScope(parameters + body), ExpressionNode {
    // May be equal to body.scope if parameters.isSimple() == true
    lateinit var functionScope: Scope
}
