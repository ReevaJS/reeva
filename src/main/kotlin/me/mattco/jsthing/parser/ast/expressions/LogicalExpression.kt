package me.mattco.jsthing.parser.ast.expressions

import me.mattco.jsthing.lexer.TokenType
import me.mattco.jsthing.utils.stringBuilder

class LogicalExpression(val lhs: Expression, val rhs: Expression, val op: Operation) : Expression() {
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
        And("&&"),
        Or("||"),
        NullishCoalescing("??");

        companion object {
            fun fromTokenType(type: TokenType) = when (type) {
                TokenType.DoubleAmpersand -> And
                TokenType.DoublePipe -> Or
                TokenType.DoubleQuestion -> NullishCoalescing
                else -> null
            }
        }
    }
}
