package me.mattco.reeva.ast.expressions

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.ASTNode.Companion.appendIndent

class AssignmentExpressionNode(val lhs: ExpressionNode, val rhs: ExpressionNode, val op: Operator) : ASTNodeBase(listOf(lhs, rhs)), ExpressionNode {
    enum class Operator(val symbol: String) {
        Equals("="),
        Multiply("*="),
        Divide("/="),
        Mod("%="),
        Plus("+="),
        Minus("-="),
        ShiftLeft("<<="),
        ShiftRight(">>="),
        UnsignedShiftRight(">>>="),
        BitwiseAnd("&="),
        BitwiseOr("|="),
        BitwiseXor("^="),
        Power("**="),
        And("&&="),
        Or("||="),
        Nullish("??=")
    }

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (")
        append(op.symbol)
        append(")\n")
        append(lhs.dump(indent + 1))
        append(rhs.dump(indent + 1))
    }
}

class AwaitExpressionNode(val expression: ExpressionNode) : ASTNodeBase(listOf(expression)), ExpressionNode

// TODO: This isn't exactly to spec
class CallExpressionNode(val target: ExpressionNode, val arguments: ArgumentsNode) : ASTNodeBase(listOf(target, arguments)), LeftHandSideExpressionNode {
    override fun assignmentTargetType(): ASTNode.AssignmentTargetType {
        return ASTNode.AssignmentTargetType.Invalid
    }
}

// Note that this name deviates from the spec because I think this is
// a much better name. It is not clear from the name "ExpressionNode"
// that the inner expression are separated by comma operators, and only
// the last one should be returned.
class CommaExpressionNode(val expressions: List<ExpressionNode>) : ASTNodeBase(expressions), ExpressionNode

class ConditionalExpressionNode(
    val predicate: ExpressionNode,
    val ifTrue: ExpressionNode,
    val ifFalse: ExpressionNode
) : ASTNodeBase(listOf(predicate, ifTrue, ifFalse)), ExpressionNode {
    override fun assignmentTargetType(): ASTNode.AssignmentTargetType {
        return ASTNode.AssignmentTargetType.Invalid
    }
}

class MemberExpressionNode(val lhs: ExpressionNode, val rhs: ExpressionNode, val type: Type) : ASTNodeBase(listOf(lhs, rhs)), LeftHandSideExpressionNode {
    override fun assignmentTargetType() = when (type) {
        Type.Computed, Type.NonComputed -> ASTNode.AssignmentTargetType.Simple
        else -> ASTNode.AssignmentTargetType.Invalid
    }

    override fun contains(nodeName: String) = if (type == Type.NonComputed) {
        lhs.contains(nodeName)
    } else super<ASTNodeBase>.contains(nodeName)

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
    val arguments: ArgumentsNode?
) : ASTNodeBase(listOfNotNull(target, arguments)), LeftHandSideExpressionNode

class OptionalExpressionNode : ASTNodeBase(), LeftHandSideExpressionNode

class SuperPropertyExpressionNode(val target: ExpressionNode, val computed: Boolean) : ASTNodeBase(listOf(target)), LeftHandSideExpressionNode {
    override fun assignmentTargetType() = ASTNode.AssignmentTargetType.Simple

    override fun contains(nodeName: String) = nodeName == "super"

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (computed=")
        append(computed)
        append(")\n")
        append(target.dump(indent + 1))
    }
}

class SuperCallExpressionNode(val arguments: ArgumentsNode) : ASTNodeBase(listOf(arguments)), ExpressionNode

class ImportCallExpressionNode(val expression: ExpressionNode) : ASTNodeBase(listOf(expression)), ExpressionNode

class YieldExpressionNode(
    val expression: ExpressionNode?,
    val generatorYield: Boolean
) : ASTNodeBase(listOfNotNull(expression)), ExpressionNode {
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

class ParenthesizedExpressionNode(val expression: ExpressionNode) : ASTNodeBase(listOf(expression)), PrimaryExpressionNode

class TemplateLiteralNode(val parts: List<ExpressionNode>) : ASTNodeBase(parts), PrimaryExpressionNode

class RegExpLiteralNode(val source: String, val flags: String) : ASTNodeBase(listOf()), PrimaryExpressionNode
