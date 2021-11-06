package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.ASTNode.Companion.appendIndent
import com.reevajs.reeva.ast.statements.ASTListNode
import com.reevajs.reeva.ast.statements.BlockNode
import com.reevajs.reeva.parsing.Scope
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.utils.duplicates

typealias ArgumentList = ASTListNode<ArgumentNode>

class ParameterList(parameters: List<Parameter> = emptyList()) : ASTListNode<Parameter>(parameters) {
    fun isSimple(): Boolean {
        // TODO: Eventually check for destructuring patterns
        return all { it.isSimple }
    }

    fun containsDuplicates(): Boolean {
        return filterIsInstance<SimpleParameter>()
            .map { it.identifier.processedName }
            .duplicates()
            .isNotEmpty()
    }

    fun expectedArgumentCount(): Int {
        for ((index, param) in withIndex()) {
            if (!param.isSimple)
                return index
        }

        return size
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

    override fun name() = identifier.processedName
}

class RestParameter(val declaration: BindingDeclarationOrPattern) : ASTNodeBase(), Parameter {
    override val isSimple = false
}

class BindingParameter(
    val pattern: BindingPatternNode,
    val initializer: ExpressionNode?,
) : ASTNodeBase(listOfNotNull(pattern, initializer)), Parameter {
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

    override fun name() = identifier.processedName
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
