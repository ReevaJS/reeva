package me.mattco.reeva.ast.literals

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.SuperCallExpressionNode
import me.mattco.reeva.ast.statements.ASTListNode
import me.mattco.reeva.ast.statements.BlockNode
import me.mattco.reeva.parser.Scope

typealias PropertyDefinitionList = ASTListNode<Property>

class ObjectLiteralNode(val list: PropertyDefinitionList) : ASTNodeBase(listOfNotNull(list)), ExpressionNode

sealed class Property(children: List<ASTNode>) : ASTNodeBase(children)

class KeyValueProperty(
    val key: PropertyName,
    val value: ExpressionNode,
) : Property(listOf(key, value))

class ShorthandProperty(val key: IdentifierReferenceNode) : Property(listOf(key))

class MethodProperty(val method: MethodDefinitionNode) : Property(listOf(method))

class SpreadProperty(val target: ExpressionNode) : Property(listOf(target))

class PropertyName(
    val expression: ExpressionNode,
    val type: Type,
) : ASTNodeBase(listOf(expression)) {
    enum class Type {
        Identifier, // expression is IdentifierNode
        String,     // expression is StringLiteralNode
        Number,     // expresion is NumericLiteralNode
        Computed,   // expression is any ExpressionNode
    }
}

class MethodDefinitionNode(
    val propName: PropertyName,
    val parameters: ParameterList,
    val body: BlockNode,
    val functionScope: Scope,
    val bodyScope: Scope,
    val type: Type
) : NodeWithScope(listOf(propName) + parameters + body) {
    fun isConstructor(): Boolean {
        return propName.type == PropertyName.Type.Identifier && propName.expression.let {
            (it as IdentifierNode).identifierName == "constructor"
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
