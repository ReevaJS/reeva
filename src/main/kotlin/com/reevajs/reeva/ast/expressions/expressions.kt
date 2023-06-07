package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.AstNode.Companion.appendIndent
import com.reevajs.reeva.ast.AstNodeBase
import com.reevajs.reeva.ast.ArgumentNode
import com.reevajs.reeva.ast.ExpressionNode
import com.reevajs.regexp.RegExp

class AssignmentExpressionNode(
    val lhs: ExpressionNode,
    val rhs: ExpressionNode,
    val op: BinaryOperator?, // Null indicates regular assignment
) : AstNodeBase(listOf(lhs, rhs)), ExpressionNode {
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

class AwaitExpressionNode(val expression: ExpressionNode) : AstNodeBase(listOf(expression)), ExpressionNode

// TODO: This isn't exactly to spec
class CallExpressionNode(
    val target: ExpressionNode,
    val arguments: List<ArgumentNode>,
    val isOptional: Boolean,
) : AstNodeBase(arguments + target), ExpressionNode

// Note that this name deviates from the spec because I think this is
// a much better name. It is not clear from the name "ExpressionNode"
// that the inner expression are separated by comma operators, and only
// the last one should be returned.
class CommaExpressionNode(val expressions: List<ExpressionNode>) : AstNodeBase(expressions), ExpressionNode

class ConditionalExpressionNode(
    val predicate: ExpressionNode,
    val ifTrue: ExpressionNode,
    val ifFalse: ExpressionNode
) : AstNodeBase(listOf(predicate, ifTrue, ifFalse)), ExpressionNode

class MemberExpressionNode(
    val lhs: ExpressionNode,
    val rhs: ExpressionNode,
    val type: Type,
) : AstNodeBase(listOf(lhs, rhs)), ExpressionNode {
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
    val target: ExpressionNode,
    val arguments: List<ArgumentNode>,
) : AstNodeBase(arguments + target), ExpressionNode

class SuperPropertyExpressionNode(
    val target: ExpressionNode,
    val isComputed: Boolean,
) : AstNodeBase(listOf(target)), ExpressionNode {
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

class SuperCallExpressionNode(val arguments: List<ArgumentNode>) : AstNodeBase(arguments), ExpressionNode

class ImportCallExpressionNode(val expression: ExpressionNode) : AstNodeBase(listOf(expression)), ExpressionNode

class YieldExpressionNode(
    val expression: ExpressionNode?,
    val generatorYield: Boolean
) : AstNodeBase(listOfNotNull(expression)), ExpressionNode {
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

class ParenthesizedExpressionNode(val expression: ExpressionNode) : AstNodeBase(listOf(expression)), ExpressionNode

class TemplateLiteralNode(val parts: List<ExpressionNode>) : AstNodeBase(parts), ExpressionNode

class RegExpLiteralNode(
    val source: String,
    val flags: String,
    val regexp: RegExp,
) : AstNodeBase(listOf()), ExpressionNode
