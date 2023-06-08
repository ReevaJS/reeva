package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.AstNode.Companion.appendIndent
import com.reevajs.reeva.ast.AstNodeBase
import com.reevajs.reeva.ast.ArgumentNode
import com.reevajs.reeva.ast.AstNode
import com.reevajs.regexp.RegExp

class AssignmentExpressionNode(
    val lhs: AstNode,
    val rhs: AstNode,
    val op: BinaryOperator?, // Null indicates regular assignment
) : AstNodeBase(listOf(lhs, rhs)) {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (")
        append("${op?.symbol ?: ""}=")
        append(")\n")
        append(lhs.dump(indent + 1))
        append(rhs.dump(indent + 1))
    }
}

class AwaitExpressionNode(val expression: AstNode) : AstNodeBase(listOf(expression))

// TODO: This isn't exactly to spec
class CallExpressionNode(
    val target: AstNode,
    val arguments: List<ArgumentNode>,
    val isOptional: Boolean,
) : AstNodeBase(arguments + target)

// Note that this name deviates from the spec because I think this is
// a much better name. It is not clear from the name "ExpressionNode"
// that the inner expression are separated by comma operators, and only
// the last one should be returned.
class CommaExpressionNode(val expressions: List<AstNode>) : AstNodeBase(expressions)

class ConditionalExpressionNode(
    val predicate: AstNode,
    val ifTrue: AstNode,
    val ifFalse: AstNode
) : AstNodeBase(listOf(predicate, ifTrue, ifFalse))

class MemberExpressionNode(
    val lhs: AstNode,
    val rhs: AstNode,
    val type: Type,
) : AstNodeBase(listOf(lhs, rhs)) {
    override val isInvalidAssignmentTarget = false

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (type=")
        append(type.name)
        append(")\n")
        append(lhs.dump(indent + 1))
        append(rhs.dump(indent + 1))
    }

    enum class Type {
        Computed,
        NonComputed,
        Tagged,
    }
}

class NewExpressionNode(
    val target: AstNode,
    val arguments: List<ArgumentNode>,
) : AstNodeBase(arguments + target)

class SuperPropertyExpressionNode(
    val target: AstNode,
    val isComputed: Boolean,
) : AstNodeBase(listOf(target)) {
    override val isInvalidAssignmentTarget: Boolean = false

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (computed=")
        append(isComputed)
        append(")\n")
        append(target.dump(indent + 1))
    }
}

class SuperCallExpressionNode(val arguments: List<ArgumentNode>) : AstNodeBase(arguments)

class ImportCallExpressionNode(val expression: AstNode) : AstNodeBase(listOf(expression))

class YieldExpressionNode(
    val expression: AstNode?,
    val generatorYield: Boolean
) : AstNodeBase(listOfNotNull(expression)) {
    init {
        if (expression == null && generatorYield)
            throw IllegalArgumentException("Cannot have a generatorYield expression without a target expression")
    }

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (generatorYield=")
        append(generatorYield)
        append(")\n")
        expression?.dump(indent + 1)?.also(::append)
    }
}

class ParenthesizedExpressionNode(val expression: AstNode) : AstNodeBase(listOf(expression))

class TemplateLiteralNode(val parts: List<AstNode>) : AstNodeBase(parts)

class RegExpLiteralNode(
    val source: String,
    val flags: String,
    val regexp: RegExp,
) : AstNodeBase(listOf())
