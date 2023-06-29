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

    fun containsExpressions() = parameters.any(Parameter::containsExpression)

    fun boundNames() = parameters.flatMap {
        when (it) {
            is BindingParameter -> it.names()
            is RestParameter -> it.names()
            is SimpleParameter -> listOf(it.name())
        }
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
    val containsExpression: Boolean
}

class SimpleParameter(
    val identifier: IdentifierNode,
    val initializer: AstNode?,
    sourceLocation: SourceLocation,
) : VariableSourceNode(sourceLocation), Parameter {
    override val children get() = listOfNotNull(identifier, initializer)

    override val isSimple = initializer == null
    override val containsExpression get() = initializer != null

    override fun name() = identifier.processedName

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class RestParameter(
    val declaration: BindingDeclarationOrPattern,
    override val sourceLocation: SourceLocation,
) : AstNode, Parameter, VariableSourceProvider by declaration {
    override val children get() = listOf(declaration)

    override val isSimple = false
    override val containsExpression get() = false

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class BindingParameter(
    val pattern: BindingPatternNode,
    val initializer: AstNode?,
    override val sourceLocation: SourceLocation,
) : AstNode, Parameter, VariableSourceProvider by pattern {
    override val children get() = listOfNotNull(pattern, initializer)

    override val isSimple = false
    override val containsExpression get() = initializer != null

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

interface GenericFunctionNode {
    val parameters: ParameterList
    val body: AstNode
    val kind: AOs.FunctionKind
    val scope: Scope
    var functionScope: Scope

    fun isLexical() = false
    fun name(): String
}

class FunctionDeclarationNode(
    val identifier: IdentifierNode?, // can be omitted in default exports
    override val parameters: ParameterList,
    override val body: BlockNode,
    override val kind: AOs.FunctionKind,
    sourceLocation: SourceLocation,
) : VariableSourceNode(sourceLocation), DeclarationNode, VariableSourceProvider, GenericFunctionNode {
    override val children get() = listOfNotNull(identifier, parameters, body)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    // May be equal to body.scope if parameters.isSimple() == true
    override lateinit var functionScope: Scope

    override fun name() = identifier?.processedName ?: TODO()

    override val declarations = listOf(this)

    override fun sources() = listOf(this)
}

class FunctionExpressionNode(
    val identifier: IdentifierNode?,
    override val parameters: ParameterList,
    override val body: BlockNode,
    override val kind: AOs.FunctionKind,
    sourceLocation: SourceLocation,
) : VariableSourceNode(sourceLocation), GenericFunctionNode {
    override val children get() = listOfNotNull(identifier, parameters, body)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    // May be equal to body.scope if parameters.isSimple() == true
    override lateinit var functionScope: Scope

    // This expression node only functions as a VariableSourceNode if it has an
    // identifier. If not, then this function will never be called
    override fun name() = identifier!!.processedName
}

class ArrowFunctionNode(
    override val parameters: ParameterList,
    override val body: AstNode, // BlockNode or ExpressionNode
    override val kind: AOs.FunctionKind,
    sourceLocation: SourceLocation,
) : NodeWithScope(sourceLocation), GenericFunctionNode {
    override val children get() = listOf(parameters, body)

    // May be equal to body.scope if parameters.isSimple() == true
    override lateinit var functionScope: Scope

    override fun name() = ""

    override fun isLexical() = true

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}
