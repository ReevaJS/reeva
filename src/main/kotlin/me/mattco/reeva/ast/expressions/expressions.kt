package me.mattco.reeva.ast.expressions

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.ASTNode.Companion.appendIndent

class AssignmentExpressionNode(val lhs: ExpressionNode, val rhs: ExpressionNode, val op: Operator) : NodeBase(listOf(lhs, rhs)), ExpressionNode {
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

class AwaitExpressionNode(val expression: ExpressionNode) : NodeBase(listOf(expression)), ExpressionNode

// TODO: This isn't exactly to spec
class CallExpressionNode(val target: ExpressionNode, val arguments: ArgumentsNode) : NodeBase(listOf(target, arguments)), LeftHandSideExpressionNode {
    override fun assignmentTargetType(): ASTNode.AssignmentTargetType {
        return ASTNode.AssignmentTargetType.Invalid
    }
}

// Note that this name deviates from the spec because I think this is
// a much better name. It is not clear from the name "ExpressionNode"
// that the inner expression are separated by comma operators, and only
// the last one should be returned.
class CommaExpressionNode(val expressions: List<ExpressionNode>) : NodeBase(expressions), ExpressionNode

class ConditionalExpressionNode(
    val predicate: ExpressionNode,
    val ifTrue: ExpressionNode,
    val ifFalse: ExpressionNode
) : NodeBase(listOf(predicate, ifTrue, ifFalse)), ExpressionNode {
    override fun assignmentTargetType(): ASTNode.AssignmentTargetType {
        return ASTNode.AssignmentTargetType.Invalid
    }
}

class MemberExpressionNode(val lhs: ExpressionNode, val rhs: ExpressionNode, val type: Type) : NodeBase(listOf(lhs, rhs)), LeftHandSideExpressionNode {
    override fun assignmentTargetType() = when (type) {
        Type.Computed, Type.NonComputed -> ASTNode.AssignmentTargetType.Simple
        else -> ASTNode.AssignmentTargetType.Invalid
    }

    override fun contains(nodeName: String) = if (type == Type.NonComputed) {
        lhs.contains(nodeName)
    } else super<NodeBase>.contains(nodeName)

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
) : NodeBase(listOfNotNull(target, arguments)), LeftHandSideExpressionNode

class OptionalExpressionNode : NodeBase(), LeftHandSideExpressionNode

class SuperPropertyNode(val target: ExpressionNode, val computed: Boolean) : NodeBase(listOf(target)), LeftHandSideExpressionNode {
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

class SuperCallNode(val arguments: ArgumentsNode) : NodeBase(listOf(arguments)), ExpressionNode

class ImportCallNode(val expression: ExpressionNode) : NodeBase(listOf(expression)), ExpressionNode

class YieldExpressionNode(
    val target: ExpressionNode?,
    val generatorYield: Boolean
) : NodeBase(listOfNotNull(target)), ExpressionNode {
    init {
        if (target == null && generatorYield)
            throw IllegalArgumentException("Cannot have a generatorYield expression without a target expression")
    }

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (generatorYield=")
        append(generatorYield)
        append(")\n")
        target?.dump(indent + 1)?.also(::append)
    }
}

class ParenthesizedExpressionNode(val target: ExpressionNode) : NodeBase(listOf(target)), PrimaryExpressionNode
