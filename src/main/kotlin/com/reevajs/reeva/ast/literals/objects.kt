package com.reevajs.reeva.ast.literals

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.ast.expressions.SuperCallExpressionNode
import com.reevajs.reeva.ast.statements.BlockNode
import com.reevajs.reeva.parsing.Scope
import com.reevajs.reeva.runtime.AOs

class ObjectLiteralNode(val list: List<Property>) : AstNodeBase(list)

sealed class Property(children: List<AstNode>) : AstNodeBase(children)

class KeyValueProperty(
    val key: PropertyName,
    val value: AstNode,
) : Property(listOf(key, value))

class ShorthandProperty(val key: IdentifierReferenceNode) : Property(listOf(key))

class MethodProperty(val method: MethodDefinitionNode) : Property(listOf(method))

class SpreadProperty(val target: AstNode) : Property(listOf(target))

class PropertyName(
    val expression: AstNode,
    val type: Type,
) : AstNodeBase(listOf(expression)) {
    fun asString() = when (type) {
        Type.Identifier -> (expression as IdentifierNode).processedName
        Type.String -> (expression as StringLiteralNode).value
        Type.Number -> AOs.numberToString((expression as NumericLiteralNode).value)
        Type.Computed -> "[computed method name]"
    }

    enum class Type {
        Identifier, // expression is IdentifierNode
        String, // expression is StringLiteralNode
        Number, // expresion is NumericLiteralNode
        Computed, // expression is any ExpressionNode
    }
}

class MethodDefinitionNode(
    val propName: PropertyName,
    val parameters: ParameterList,
    val body: BlockNode,
    val kind: Kind
) : NodeWithScope(listOf(propName) + parameters + body) {
    // May be equal to body.scope if parameters.isSimple() == true
    lateinit var functionScope: Scope

    fun isConstructor(): Boolean {
        return propName.type == PropertyName.Type.Identifier && propName.expression.let {
            (it as IdentifierNode).processedName == "constructor"
        } && kind == Kind.Normal
    }

    fun containsSuperCall() = parameters.parameters.any { it.containsAny<SuperCallExpressionNode>() } ||
        body.containsAny<SuperCallExpressionNode>()

    enum class Kind(val isAsync: Boolean = false, val isGenerator: Boolean = false) {
        Normal,
        Getter,
        Setter,
        Async(isAsync = true),
        Generator(isGenerator = true),
        AsyncGenerator(isAsync = true, isGenerator = true);

        fun toFunctionKind() = when (this) {
            Generator -> AOs.FunctionKind.Generator
            Async -> AOs.FunctionKind.Async
            AsyncGenerator -> AOs.FunctionKind.AsyncGenerator
            else -> AOs.FunctionKind.Normal
        }
    }
}
