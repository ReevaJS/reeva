package me.mattco.reeva.ast.literals

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.utils.unreachable

class ObjectLiteralNode(val list: PropertyDefinitionListNode?) : ASTNodeBase(listOfNotNull(list)), PrimaryExpressionNode

class PropertyDefinitionListNode(val properties: List<PropertyDefinitionNode>) : ASTNodeBase(properties) {
    override fun propertyNameList(): List<String> {
        return properties.mapNotNull(ASTNode::propName)
    }
}

// "first" and "second" have different interpretations based on the type
class PropertyDefinitionNode(
    val first: ASTNode,
    val second: ExpressionNode?,
    val type: Type
) : ASTNodeBase(listOfNotNull(first, second)) {
    override fun contains(nodeName: String): Boolean {
        if (nodeName == "MethodDefinitionNode") {
            if (type == Type.Method)
                return true
            return first.computedPropertyContains(nodeName)
        }
        return super.contains(nodeName)
    }

    override fun propName(): String? {
        return when (type) {
            Type.Shorthand -> (first as IdentifierNode).identifierName
            Type.KeyValue -> first.propName()
            Type.Method -> super.propName()
            Type.Spread -> null
            Type.Initializer -> unreachable()
        }
    }

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

class PropertyNameNode(val expr: ExpressionNode, val isComputed: Boolean) : ASTNodeBase(listOf(expr)) {
    override fun computedPropertyContains(nodeName: String): Boolean {
        if (!isComputed)
            return false
        return expr.contains(nodeName)
    }

    override fun contains(nodeName: String): Boolean {
        if (expr is IdentifierNode)
            return false
        return super.contains(nodeName)
    }

    override fun isComputedPropertyKey() = isComputed

    override fun propName() = when {
        isComputed -> null
        expr is IdentifierNode -> expr.identifierName
        expr is StringLiteralNode -> expr.value
        // This is kinda scuffed
        expr is NumericLiteralNode -> expr.value.toString()
        else -> unreachable()
    }
}

class MethodDefinitionNode(
    val identifier: PropertyNameNode,
    val parameters: FormalParametersNode,
    val body: FunctionStatementList,
    val type: Type
) : ASTNodeBase(listOfNotNull(identifier, parameters, body)) {
    override fun computedPropertyContains(nodeName: String) = identifier.computedPropertyContains(nodeName)

    override fun hasDirectSuper(): Boolean {
        return when (type) {
            Type.Normal, Type.Setter -> parameters.contains("SuperCallExpressionNode") || body.contains("SuperCallExpressionNode")
            Type.Getter -> body.contains("SuperCallExpressionNode")
            Type.Generator, Type.Async, Type.AsyncGenerator -> TODO()
        }
    }

    override fun propName() = identifier.propName()

    enum class Type {
        Normal,
        Getter,
        Setter,
        Generator,
        Async,
        AsyncGenerator,
    }
}
