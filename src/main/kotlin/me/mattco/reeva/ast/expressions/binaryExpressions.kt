package me.mattco.reeva.ast.expressions

import me.mattco.reeva.ast.ExpressionNode
import me.mattco.reeva.ast.ASTNodeBase
import me.mattco.reeva.ast.ShortCircuitExpressionNode

open class BinaryExpression(val lhs: ExpressionNode, val rhs: ExpressionNode) : ASTNodeBase(listOf(lhs, rhs)), ExpressionNode

class AdditiveExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode, val isSubtraction: Boolean) : BinaryExpression(lhs, rhs)

class BitwiseANDExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs)

class BitwiseORExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs)

class BitwiseXORExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs)

class CoalesceExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs), ShortCircuitExpressionNode

class EqualityExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode, val op: Operator) : BinaryExpression(lhs, rhs) {
    enum class Operator(val symbol: String) {
        StrictEquality("==="),
        StrictInequality("!=="),
        NonstrictEquality("=="),
        NonstrictInequality("!="),
    }
}

class ExponentiationExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs)

class LogicalANDExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs)

class LogicalORExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode) : BinaryExpression(lhs, rhs), ShortCircuitExpressionNode

class MultiplicativeExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode, val op: Operator) : BinaryExpression(lhs, rhs) {
    enum class Operator(val symbol: String) {
        Multiply("*"),
        Divide("/"),
        Modulo("%"),
    }
}

class RelationalExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode, val op: Operator) : BinaryExpression(lhs, rhs) {
    enum class Operator(val symbol: String) {
        LessThan("<"),
        GreaterThan(">"),
        LessThanEquals("<="),
        GreaterThanEquals(">="),
        Instanceof("instanceof"),
        In("in"),
    }
}

class ShiftExpressionNode(lhs: ExpressionNode, rhs: ExpressionNode, val op: Operator) : BinaryExpression(lhs, rhs) {
    enum class Operator(val symbol: String) {
        ShiftLeft("<<"),
        ShiftRight(">>"),
        UnsignedShiftRight(">>>")
    }
}
