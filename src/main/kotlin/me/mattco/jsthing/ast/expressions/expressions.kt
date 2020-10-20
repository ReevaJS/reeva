package me.mattco.jsthing.ast.expressions

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.ArgumentsNode
import me.mattco.jsthing.utils.stringBuilder

class PrimaryExpressionNode(val expression: ExpressionNode) : ExpressionNode()

class AssignmentExpressionNode(val lhs: ExpressionNode, val rhs: ExpressionNode, val op: Operator) : ExpressionNode() {
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

class AwaitExpressionNode(val expression: ExpressionNode) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(expression.dump(indent + 1))
    }
}

class CallExpressionNode(val target: ExpressionNode, val arguments: ArgumentsNode) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(target.dump(indent + 1))
        append(arguments.dump(indent + 1))
    }
}

// Note that this name deviates from the spec because I think this is
// a much better name. It is not clear from the name "ExpressionNode"
// that the inner expression are separated by comma operators, and only
// the last one should be returned.
class CommaExpressionNode(val expressions: List<ExpressionNode>) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        expressions.forEach {
            append(it.dump(indent + 1))
        }
    }
}

class ConditionalExpressionNode(val predicate: ExpressionNode, val ifTrue: ExpressionNode, val ifFalse: ExpressionNode) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(predicate.dump(indent + 1))
        append(ifTrue.dump(indent + 1))
        append(ifFalse.dump(indent + 1))
    }
}

class MemberExpressionNode(val lhs: ExpressionNode, val rhs: ASTNode, val type: Type) : ExpressionNode() {
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

class NewExpressionNode(val target: ExpressionNode) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(target.dump(indent + 1))
    }
}

class OptionalExpressionNode : ExpressionNode() {
    override fun dump(indent: Int): String {
        TODO()
    }
}

class SuperPropertyNode(val target: ASTNode, val computed: Boolean) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (computed=")
        append(computed)
        append(")\n")
        append(target.dump(indent + 1))
    }
}

class YieldExpressionNode(val target: ExpressionNode?, val generatorYield: Boolean) : ExpressionNode() {
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

class ParenthesizedExpression(val target: ExpressionNode) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(target.dump(indent + 1))
    }
}
