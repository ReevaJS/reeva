package me.mattco.reeva.ast

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.statements.ASTListNode
import me.mattco.reeva.ast.statements.BlockNode
import me.mattco.reeva.parsing.Scope
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.utils.expect

typealias ArgumentList = ASTListNode<ArgumentNode>

class ParameterList(parameters: List<Parameter> = emptyList()) : ASTListNode<Parameter>(parameters) {
    fun isSimple(): Boolean {
        // TODO: Eventually check for destructuring patterns
        return all { it.initializer == null && !it.isRest }
    }

    fun containsDuplicates(): Boolean {
        return distinctBy { it.identifier.name }.size != size
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
    val identifier: IdentifierNode,
    val initializer: ExpressionNode?,
    val isRest: Boolean
) : VariableSourceNode(listOfNotNull(identifier, initializer)) {
    fun isSimple() = !isRest && initializer == null

    override fun name() = identifier.name

    init {
        if (isRest)
            expect(initializer == null)
    }
}

class FunctionDeclarationNode(
    val identifier: IdentifierNode,
    val parameters: ParameterList,
    val body: BlockNode,
    val kind: Operations.FunctionKind,
) : VariableSourceNode(listOfNotNull(identifier) + parameters + body), StatementNode {
    // May be equal to body.scope if parameters.isSimple() == true
    lateinit var functionScope: Scope

    override fun name() = identifier.name
}

class FunctionExpressionNode(
    val identifier: IdentifierNode?,
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
