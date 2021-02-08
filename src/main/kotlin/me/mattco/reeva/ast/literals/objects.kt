package me.mattco.reeva.ast.literals

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.SuperCallExpressionNode
import me.mattco.reeva.ast.statements.ASTListNode
import me.mattco.reeva.ast.statements.BlockNode

typealias PropertyDefinitionList = ASTListNode<Property>

class ObjectLiteralNode(val list: PropertyDefinitionList) : ASTNodeBase(listOfNotNull(list)), ExpressionNode

sealed class Property(children: List<ASTNode>) : ASTNodeBase(children)

class KeyValueProperty(
    val key: PropertyName,
    val value: ExpressionNode,
) : Property(listOf(key, value))

class ShorthandProperty(val key: IdentifierNode) : Property(listOf(key))

class MethodProperty(val method: MethodDefinitionNode) : Property(listOf(method))

class SpreadProperty(val target: ExpressionNode) : Property(listOf(target))

class CoveredInitializerProperty(
    val target: IdentifierNode,
    val initializer: ExpressionNode,
) : Property(listOf(target, initializer))

class PropertyName(
    val expression: ExpressionNode,
    val isComputed: Boolean,
) : ASTNodeBase(listOf(expression))

class MethodDefinitionNode(
    val identifier: PropertyName,
    val parameters: ParameterList,
    val body: BlockNode,
    val type: Type
) : ASTNodeBase(listOf(identifier) + parameters + body) {
    fun isConstructor(): Boolean {
        return !identifier.isComputed && identifier.expression.let {
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
