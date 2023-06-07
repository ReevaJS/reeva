package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.AstNode.Companion.appendIndent
import com.reevajs.reeva.ast.statements.BlockNode
import com.reevajs.reeva.ast.statements.DeclarationNode
import com.reevajs.reeva.ast.statements.VariableSourceProvider
import com.reevajs.reeva.parsing.Scope
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.utils.duplicates

class ParameterList(
    val parameters: List<Parameter> = emptyList(),
) : AstNodeBase(parameters) {
    fun isSimple(): Boolean {
        // TODO: Eventually check for destructuring patterns
        return parameters.all { it.isSimple }
    }

    fun containsDuplicates(): Boolean {
        return parameters.filterIsInstance<SimpleParameter>()
            .map { it.identifier.processedName }
            .duplicates()
            .isNotEmpty()
    }

    fun expectedArgumentCount(): Int {
        for ((index, param) in parameters.withIndex()) {
            if (!param.isSimple)
                return index
        }

        return parameters.size
    }
}

class ArgumentNode(
    val expression: ExpressionNode,
    val isSpread: Boolean
) : AstNodeBase(listOf(expression)), ExpressionNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (isSpread=")
        append(isSpread)
        append(")\n")
        append(expression.dump(indent + 1))
    }
}

sealed interface Parameter : AstNode {
    val isSimple: Boolean
}

class SimpleParameter(
    val identifier: IdentifierNode,
    val initializer: ExpressionNode?,
) : VariableSourceNode(listOfNotNull(identifier, initializer)), Parameter {
    override val isSimple = initializer == null

    override fun name() = identifier.processedName
}

class RestParameter(val declaration: BindingDeclarationOrPattern) : AstNodeBase(), Parameter {
    override val isSimple = false
}

class BindingParameter(
    val pattern: BindingPatternNode,
    val initializer: ExpressionNode?,
) : AstNodeBase(listOfNotNull(pattern, initializer)), Parameter {
    override val isSimple = false
}

class FunctionDeclarationNode(
    val identifier: IdentifierNode?, // can be omitted in default exports
    val parameters: ParameterList,
    val body: BlockNode,
    val kind: AOs.FunctionKind,
) : VariableSourceNode(listOfNotNull(identifier) + parameters + body), DeclarationNode, VariableSourceProvider {
    // May be equal to body.scope if parameters.isSimple() == true
    lateinit var functionScope: Scope

    override fun name() = identifier?.processedName ?: TODO()

    override val declarations = listOf(this)

    override fun sources() = listOf(this)
}

class FunctionExpressionNode(
    val identifier: IdentifierNode?,
    val parameters: ParameterList,
    val body: BlockNode,
    val kind: AOs.FunctionKind,
) : VariableSourceNode(listOfNotNull(identifier) + parameters + body), ExpressionNode {
    // May be equal to body.scope if parameters.isSimple() == true
    lateinit var functionScope: Scope

    // This expression node only functions as a VariableSourceNode if it has an
    // identifier. If not, then this function will never be called
    override fun name() = identifier!!.processedName
}

class ArrowFunctionNode(
    val parameters: ParameterList,
    val body: AstNode, // BlockNode or ExpressionNode
    val kind: AOs.FunctionKind,
) : NodeWithScope(listOf(parameters, body)), ExpressionNode {
    // May be equal to body.scope if parameters.isSimple() == true
    lateinit var functionScope: Scope
}
