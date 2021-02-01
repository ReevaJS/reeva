package me.mattco.reeva.ast.expressions

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.ASTNodeBase
import me.mattco.reeva.ast.Argument
import me.mattco.reeva.ast.ArgumentList
import me.mattco.reeva.ast.ExpressionNode

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
class CallExpressionNode(
    val target: ExpressionNode,
    val arguments: ArgumentList,
) : ASTNodeBase(listOf(target) + arguments), ExpressionNode

// Note that this name deviates from the spec because I think this is
// a much better name. It is not clear from the name "ExpressionNode"
// that the inner expression are separated by comma operators, and only
// the last one should be returned.
class CommaExpressionNode(val expressions: List<ExpressionNode>) : ASTNodeBase(expressions), ExpressionNode

class ConditionalExpressionNode(
    val predicate: ExpressionNode,
    val ifTrue: ExpressionNode,
    val ifFalse: ExpressionNode
) : ASTNodeBase(listOf(predicate, ifTrue, ifFalse)), ExpressionNode

class MemberExpressionNode(
    val lhs: ExpressionNode,
    val rhs: ExpressionNode,
    val type: Type,
) : ASTNodeBase(listOf(lhs, rhs)), ExpressionNode {
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
    val arguments: ArgumentList,
) : ASTNodeBase(listOfNotNull(target) + arguments), ExpressionNode

class OptionalExpressionNode : ASTNodeBase(), ExpressionNode

class SuperPropertyExpressionNode(
    val target: ExpressionNode,
    val computed: Boolean,
) : ASTNodeBase(listOf(target)), ExpressionNode {
    override val isInvalidAssignmentTarget: Boolean = false

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (computed=")
        append(computed)
        append(")\n")
        append(target.dump(indent + 1))
    }
}

class SuperCallExpressionNode(val arguments: ArgumentList) : ASTNodeBase(arguments), ExpressionNode

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

class ParenthesizedExpressionNode(val expression: ExpressionNode) : ASTNodeBase(listOf(expression)), ExpressionNode

class TemplateLiteralNode(val parts: List<ExpressionNode>) : ASTNodeBase(parts), ExpressionNode

class RegExpLiteralNode(val source: String, val flags: String) : ASTNodeBase(listOf()), ExpressionNode
