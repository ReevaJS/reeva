package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.AstNode.Companion.appendIndent
import com.reevajs.reeva.ast.statements.BlockNode
import com.reevajs.reeva.ast.statements.DeclarationNode
import com.reevajs.reeva.ast.statements.VariableSourceProvider
import com.reevajs.reeva.parsing.Scope
import com.reevajs.reeva.parsing.lexer.SourceLocation
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.utils.duplicates

class ParameterList(val parameters: List<Parameter>, override val sourceLocation: SourceLocation) : AstNode {
    override val children get() = parameters

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

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
    val expression: AstNode,
    val isSpread: Boolean,
    override val sourceLocation: SourceLocation,
) : AstNode {
    override val children get() = listOf(expression)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

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
    val initializer: AstNode?,
    sourceLocation: SourceLocation,
) : VariableSourceNode(sourceLocation), Parameter {
    override val children get() = listOfNotNull(identifier, initializer)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override val isSimple = initializer == null

    override fun name() = identifier.processedName
}

class RestParameter(
    val declaration: BindingDeclarationOrPattern,
    override val sourceLocation: SourceLocation,
) : AstNode, Parameter {
    override val children get() = listOf(declaration)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override val isSimple = false
}

class BindingParameter(
    val pattern: BindingPatternNode,
    val initializer: AstNode?,
    override val sourceLocation: SourceLocation,
) : AstNode, Parameter {
    override val children get() = listOfNotNull(pattern, initializer)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override val isSimple = false
}

class FunctionDeclarationNode(
    val identifier: IdentifierNode?, // can be omitted in default exports
    val parameters: ParameterList,
    val body: BlockNode,
    val kind: AOs.FunctionKind,
    sourceLocation: SourceLocation,
) : VariableSourceNode(sourceLocation), DeclarationNode, VariableSourceProvider {
    override val children get() = listOfNotNull(identifier, parameters, body)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

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
    sourceLocation: SourceLocation,
) : VariableSourceNode(sourceLocation) {
    override val children get() = listOfNotNull(identifier, parameters, body)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

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
    sourceLocation: SourceLocation,
) : NodeWithScope(sourceLocation) {
    override val children get() = listOf(parameters, body)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    // May be equal to body.scope if parameters.isSimple() == true
    lateinit var functionScope: Scope
}
