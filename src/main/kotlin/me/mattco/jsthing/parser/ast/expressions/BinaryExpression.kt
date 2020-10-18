package me.mattco.jsthing.parser.ast.expressions

import me.mattco.jsthing.lexer.TokenType

class BinaryExpression(val lhs: Expression, val rhs: Expression, val op: Operation) : Expression() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (")
        append(op.string)
        append(")\n")
        append(lhs.dump(indent + 1))
        append(rhs.dump(indent + 1))
    }

    enum class Operation(val string: String) {
        Addition("+"),
        Subtraction("-"),
        Multiplication("*"),
        Division("/"),
        Modulo("%"),
        Exponentiation("**"),
        StrictEquals("==="),
        StrictInequals("!=="),
        NonstrictEquals("=="),
        NonstrictInequals("!="),
        GreaterThan(">"),
        GreaterThanEquals(">="),
        LessThan("<"),
        LessThanEquals("<="),
        BitwiseAnd("&"),
        BitwiseOr("|"),
        BitwiseXor("^"),
        ShiftLeft("<<"),
        ShiftRight(">>"),
        UnsignedShiftRight(">>>"),
        In("in"),
        Instanceof("instanceof");

        companion object {
            fun fromTokenType(type: TokenType) = when (type) {
                TokenType.Plus -> Addition
                TokenType.Minus -> Subtraction
                TokenType.Asterisk -> Multiplication
                TokenType.Slash -> Division
                TokenType.Percent -> Modulo
                TokenType.DoubleAsterisk -> Exponentiation
                TokenType.TripleEquals -> StrictEquals
                TokenType.ExclamationDoubleEquals -> StrictInequals
                TokenType.DoubleEquals -> NonstrictEquals
                TokenType.ExclamationEquals -> NonstrictInequals
                TokenType.GreaterThan -> GreaterThan
                TokenType.GreaterThanEquals -> GreaterThanEquals
                TokenType.LessThan -> LessThan
                TokenType.LessThanEquals -> LessThanEquals
                TokenType.Ampersand -> BitwiseAnd
                TokenType.Pipe -> BitwiseOr
                TokenType.Caret -> BitwiseXor
                TokenType.ShiftLeft -> ShiftLeft
                TokenType.ShiftRight -> ShiftRight
                TokenType.UnsignedShiftRight -> UnsignedShiftRight
                TokenType.In -> In
                TokenType.Instanceof -> Instanceof
                else -> null
            }
        }
    }
}
