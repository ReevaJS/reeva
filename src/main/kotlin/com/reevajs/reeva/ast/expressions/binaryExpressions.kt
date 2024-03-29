package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.AstNode
import com.reevajs.reeva.ast.AstNode.Companion.appendIndent
import com.reevajs.reeva.ast.AstVisitor
import com.reevajs.reeva.parsing.lexer.SourceLocation

class BinaryExpressionNode(
    val lhs: AstNode,
    val rhs: AstNode,
    val operator: BinaryOperator,
    override val sourceLocation: SourceLocation,
) : AstNode {
    override val children get() = listOf(lhs, rhs)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (")
        append(operator.symbol)
        append(")\n")
        append(lhs.dump(indent + 1))
        append(rhs.dump(indent + 1))
    }
}

enum class BinaryOperator(val symbol: String, val isAssignable: Boolean = true) {
    Add("+"),
    Sub("-"),
    Div("/"),
    Mul("*"),
    Exp("**"),
    Mod("%"),
    And("&&"),
    Or("||"),
    Coalesce("??"),
    BitwiseAnd("&"),
    BitwiseOr("|"),
    BitwiseXor("^"),
    Shl("<<"),
    Shr(">>"),
    UShr(">>>"),
    StrictEquals("===", isAssignable = false),
    StrictNotEquals("!==", isAssignable = false),
    SloppyEquals("==", isAssignable = false),
    SloppyNotEquals("!=", isAssignable = false),
    LessThan("<", isAssignable = false),
    GreaterThan(">", isAssignable = false),
    LessThanEquals("<=", isAssignable = false),
    GreaterThanEquals(">=", isAssignable = false),
    Instanceof("instanceof", isAssignable = false),
    In("in", isAssignable = false),
}
