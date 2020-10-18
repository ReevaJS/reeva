package me.mattco.jsthing.parser.ast.expressions

import me.mattco.jsthing.lexer.TokenType
import me.mattco.jsthing.utils.stringBuilder

class AssignmentExpression(val lhs: Expression, val rhs: Expression, val op: Operation) : Expression() {
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
        Assignment("="),
        AdditionAssignment("+="),
        SubtractionAssignment("-="),
        MultiplicationAssignment("*="),
        DivisionAssignment("/="),
        ModuloAssignment("%="),
        ExponentiationAssignment("**="),
        BitwiseAndAssignment("&="),
        BitwiseOrAssignment("|="),
        BitwiseXorAssignment("^="),
        ShiftLeftAssignment("<<="),
        ShiftRightAssignment(">>="),
        UnsignedShiftRightAssignment(">>>="),
        AndAssignment("&&="),
        OrAssignment("||="),
        NullishAssignment("??=");

        companion object {
            fun fromTokenType(type: TokenType) = when (type) {
                TokenType.Equals -> Assignment
                TokenType.PlusEquals -> AdditionAssignment
                TokenType.MinusEquals -> SubtractionAssignment
                TokenType.AsteriskEquals -> MultiplicationAssignment
                TokenType.SlashEquals -> DivisionAssignment
                TokenType.PercentEquals -> ModuloAssignment
                TokenType.DoubleAsteriskEquals -> ExponentiationAssignment
                TokenType.AmpersandEquals -> BitwiseAndAssignment
                TokenType.PipeEquals -> BitwiseOrAssignment
                TokenType.CaretEquals -> BitwiseXorAssignment
                TokenType.ShiftLeftEquals -> ShiftLeftAssignment
                TokenType.ShiftRightEquals -> ShiftRightAssignment
                TokenType.UnsignedShiftRightEquals -> UnsignedShiftRightAssignment
                TokenType.DoubleAmpersandEquals -> AndAssignment
                TokenType.DoublePipeEquals -> OrAssignment
                TokenType.DoubleQuestionEquals -> NullishAssignment
                else -> null
            }
        }
    }
}
