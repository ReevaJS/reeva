package me.mattco.reeva.ast

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.statements.ASTListNode
import me.mattco.reeva.ast.statements.BlockNode
import me.mattco.reeva.parsing.Scope
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.utils.duplicates

typealias ArgumentList = ASTListNode<ArgumentNode>

class ParameterList(parameters: List<Parameter> = emptyList()) : ASTListNode<Parameter>(parameters) {
    fun isSimple(): Boolean {
        // TODO: Eventually check for destructuring patterns
        return all { it.isSimple }
    }

    fun containsDuplicates(): Boolean {
        return filterIsInstance<SimpleParameter>()
            .map { it.identifier.name }
            .duplicates()
            .isNotEmpty()
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

sealed interface Parameter : ASTNode {
    val isSimple: Boolean
}

class SimpleParameter(
    val identifier: IdentifierNode,
    val initializer: ExpressionNode?,
) : VariableSourceNode(listOfNotNull(identifier, initializer)), Parameter {
    override val isSimple = initializer == null

    override fun name() = identifier.name
}

class RestParameter(val declaration: BindingDeclarationOrPattern) : ASTNodeBase(), Parameter {
    override val isSimple = false
}

class BindingParameter(
    val pattern: BindingPatternNode,
    val initializer: ExpressionNode?,
) : ASTNodeBase(), Parameter {
    override val isSimple = false
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
