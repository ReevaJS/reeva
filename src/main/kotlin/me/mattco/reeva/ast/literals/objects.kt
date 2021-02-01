package me.mattco.reeva.ast.literals

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.expressions.SuperCallExpressionNode
import me.mattco.reeva.ast.statements.StatementList

class ObjectLiteralNode(val list: PropertyDefinitionListNode?) : ASTNodeBase(listOfNotNull(list)), ExpressionNode

class PropertyDefinitionListNode(val properties: List<PropertyDefinitionNode>) : ASTNodeBase(properties)

// "first" and "second" have different interpretations based on the type
class PropertyDefinitionNode(
    val first: ASTNode,
    val second: ExpressionNode?,
    val type: Type
) : ASTNodeBase(listOfNotNull(first, second)) {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (type=")
        append(type.name)
        append(")\n")
        append(first.dump(indent + 1))
        second?.dump(indent + 1)?.also(::append)
    }

    enum class Type {
        KeyValue,
        Shorthand,
        Method,
        Spread,
        Initializer,
    }
}

class PropertyNameNode(
    val expr: ExpressionNode,
    val isComputed: Boolean,
) : ASTNodeBase(listOf(expr))

class MethodDefinitionNode(
    val identifier: PropertyNameNode,
    val parameters: ParameterList,
    val body: StatementList,
    val type: Type
) : ASTNodeBase(listOf(identifier) + parameters + body) {
    fun isConstructor(): Boolean {
        return !identifier.isComputed && identifier.expr.let {
            it is BindingIdentifierNode && it.identifierName == "constructor"
        }
    }

    fun containsSuperCall() = parameters.any { it.containsAny<SuperCallExpressionNode>() } ||
        body.containsAny<SuperCallExpressionNode>()

    enum class Type {
        Normal,
        Getter,
        Setter,
        Generator,
        Async,
        AsyncGenerator,
    }
}
