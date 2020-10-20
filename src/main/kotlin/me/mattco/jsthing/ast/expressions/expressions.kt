package me.mattco.jsthing.ast.expressions

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.ArgumentsNode
import me.mattco.jsthing.ast.CPEAAPLNode
import me.mattco.jsthing.utils.expect
import me.mattco.jsthing.utils.stringBuilder

class PrimaryExpressionNode(val expression: ExpressionNode) : ExpressionNode(listOf(expression)) {
    override fun assignmentTargetType(): AssignmentTargetType {
        if (expression !is CPEAAPLNode)
            return AssignmentTargetType.Invalid
        expect(expression.node is ParenthesizedExpression)
        return expression.node.target.assignmentTargetType()
    }
}

class AssignmentExpressionNode(val lhs: ExpressionNode, val rhs: ExpressionNode, val op: Operator) : ExpressionNode(listOf(lhs, rhs)) {
    override fun assignmentTargetType(): AssignmentTargetType {
        return AssignmentTargetType.Invalid
    }

    enum class Operator(val string: String) {
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

    override fun dump(indent: Int) = stringBuilder {
        makeIndent(indent)
        appendName()
        append(" (")
        append(op.string)
        append(")\n")
        append(lhs.dump(indent + 1))
        append(rhs.dump(indent + 1))
    }
}

class AwaitExpressionNode(val expression: ExpressionNode) : ExpressionNode(listOf(expression))

// TODO: This isn't exactly to spec
class CallExpressionNode(val target: ExpressionNode, val arguments: ArgumentsNode) : ExpressionNode(listOf(target, arguments)) {
    override fun assignmentTargetType(): AssignmentTargetType {
        return AssignmentTargetType.Invalid
    }
}

// Note that this name deviates from the spec because I think this is
// a much better name. It is not clear from the name "ExpressionNode"
// that the inner expression are separated by comma operators, and only
// the last one should be returned.
class CommaExpressionNode(val expressions: List<ExpressionNode>) : ExpressionNode(expressions) {
    override fun assignmentTargetType(): AssignmentTargetType {
        return AssignmentTargetType.Invalid
    }
}

class ConditionalExpressionNode(
    val predicate: ExpressionNode,
    val ifTrue: ExpressionNode,
    val ifFalse: ExpressionNode
) : ExpressionNode(listOf(predicate, ifTrue, ifFalse)) {
    override fun assignmentTargetType(): AssignmentTargetType {
        return AssignmentTargetType.Invalid
    }
}

class LeftHandSideExpressionNode(val expression: ExpressionNode) : ExpressionNode(listOf(expression)) {
    override fun assignmentTargetType(): AssignmentTargetType {
        if (expression is OptionalExpressionNode)
            return AssignmentTargetType.Invalid
        TODO()
    }
}

class MemberExpressionNode(val lhs: ExpressionNode, val rhs: ASTNode, val type: Type) : ExpressionNode(listOf(lhs, rhs)) {
    override fun assignmentTargetType(): AssignmentTargetType {
        return when (type) {
            Type.Computed, Type.NonComputed -> AssignmentTargetType.Simple
            else -> AssignmentTargetType.Invalid
        }
    }

    override fun contains(nodeName: String): Boolean {
        return lhs.contains(nodeName)
    }

    override fun dump(indent: Int) = stringBuilder {
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
        New,
        Tagged,
        Call,
    }
}

class NewExpressionNode(val target: ExpressionNode) : ExpressionNode(listOf(target)) {
    override fun assignmentTargetType(): AssignmentTargetType {
        return AssignmentTargetType.Invalid
    }
}

class OptionalExpressionNode : ExpressionNode()

class SuperPropertyNode(val target: ASTNode, val computed: Boolean) : ExpressionNode(listOf(target)) {
    override fun assignmentTargetType(): AssignmentTargetType {
        return AssignmentTargetType.Simple
    }

    override fun contains(nodeName: String): Boolean {
        return nodeName == "super"
    }

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (computed=")
        append(computed)
        append(")\n")
        append(target.dump(indent + 1))
    }
}

class SuperCallNode(val arguments: ArgumentsNode) : ExpressionNode(listOf(arguments)) {
    override fun assignmentTargetType(): AssignmentTargetType {
        return AssignmentTargetType.Invalid
    }
}

class ImportCallNode(val expression: ExpressionNode) : ExpressionNode(listOf(expression)) {
    override fun assignmentTargetType(): AssignmentTargetType {
        return AssignmentTargetType.Invalid
    }
}

class YieldExpressionNode(
    val target: ExpressionNode?,
    val generatorYield: Boolean
) : ExpressionNode(listOfNotNull(target)) {
    init {
        if (target == null && generatorYield)
            throw IllegalArgumentException("Cannot have a generatorYield expression without a target expression")
    }

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (generatorYield=")
        append(generatorYield)
        append(")\n")
        target?.dump(indent + 1)?.also(::append)
    }
}

class ParenthesizedExpression(val target: ExpressionNode) : ExpressionNode(listOf(target))
