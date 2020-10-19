package me.mattco.jsthing.ast.expressions

import me.mattco.jsthing.utils.stringBuilder

open class BinaryExpression(val lhs: ExpressionNode, val rhs: ExpressionNode) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        append(lhs.dump(indent + 1))
        append(rhs.dump(indent + 1))
    }
}

class AdditiveExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode, val isSubtraction: Boolean) : BinaryExpression(lhs, rhs)

class BitwiseANDExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs)

class BitwiseORExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs)

class BitwiseXORExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs)

class CoalesceExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs)

class EqualityExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode, val op: Operator) : BinaryExpression(lhs, rhs) {
    enum class Operator {
        StrictEquality,
        StrictInequality,
        NonstrictEquality,
        NonstrictInequality,
    }
}

class ExponentiationExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs)

class LogicalANDExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs)

class LogicalORExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs)

class MultiplicativeExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode, val op: Operator) : BinaryExpression(lhs, rhs) {
    enum class Operator {
        Multiply,
        Divide,
        Modulo,
    }
}

class RelationalExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode, val op: Operator) : BinaryExpression(lhs, rhs) {
    enum class Operator {
        LessThan,
        GreaterThan,
        LessThanEquals,
        GreaterThanEquals,
        Instanceof,
        In,
    }
}

class ShiftExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode, val op: Operator) : BinaryExpression(lhs, rhs) {
    enum class Operator {
        ShiftLeft,
        ShiftRight,
        UnsignedShiftRight
    }
}
