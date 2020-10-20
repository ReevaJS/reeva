package me.mattco.jsthing.ast.expressions

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.ArgumentsNode
import me.mattco.jsthing.utils.stringBuilder

class PrimaryExpressionNode(val expression: ExpressionNode) : ExpressionNode(listOf(expression))

class AssignmentExpressionNode(val lhs: ExpressionNode, val rhs: ExpressionNode, val op: Operator) : ExpressionNode(listOf(lhs, rhs)) {
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

class CallExpressionNode(val target: ExpressionNode, val arguments: ArgumentsNode) : ExpressionNode(listOf(target, arguments))

// Note that this name deviates from the spec because I think this is
// a much better name. It is not clear from the name "ExpressionNode"
// that the inner expression are separated by comma operators, and only
// the last one should be returned.
class CommaExpressionNode(val expressions: List<ExpressionNode>) : ExpressionNode(expressions)

class ConditionalExpressionNode(
    val predicate: ExpressionNode,
    val ifTrue: ExpressionNode,
    val ifFalse: ExpressionNode
) : ExpressionNode(listOf(predicate, ifTrue, ifFalse))

class MemberExpressionNode(val lhs: ExpressionNode, val rhs: ASTNode, val type: Type) : ExpressionNode(listOf(lhs, rhs)) {
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

class NewExpressionNode(val target: ExpressionNode) : ExpressionNode(listOf(target))

class OptionalExpressionNode : ExpressionNode()

class SuperPropertyNode(val target: ASTNode, val computed: Boolean) : ExpressionNode(listOf(target)) {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (computed=")
        append(computed)
        append(")\n")
        append(target.dump(indent + 1))
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
