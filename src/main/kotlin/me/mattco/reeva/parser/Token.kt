package me.mattco.reeva.parser

import me.mattco.reeva.utils.expect

data class Token(
    val type: TokenType,
    val start: TokenLocation,
    val end: TokenLocation,
    val afterNewline: Boolean,
    val literals: String,
    val rawLiterals: String,
) {
    fun doubleValue(): Double {
        expect(type == TokenType.NumericLiteral)

        if (literals[0] == '0' && literals.length >= 2) {
            when (literals[1]) {
                'x', 'X' -> return literals.substring(2).toLong(16).toDouble()
                'o', 'O' -> return literals.substring(2).toLong(8).toDouble()
                'b', 'B' -> return literals.substring(2).toLong(2).toDouble()
                else -> expect(!literals[1].isDigit()) // Handled in parser
            }
        }

        return literals.filterNot { it == '_' }.toDouble()
    }

    companion object {
        val INVALID = Token(TokenType.Invalid, TokenLocation.EMPTY, TokenLocation.EMPTY, false, "", "")
        val EOF = Token(TokenType.Eof, TokenLocation.EMPTY, TokenLocation.EMPTY, false, "", "")
    }
}

data class TokenLocation(val index: Int, val line: Int, val column: Int) {
    fun shiftColumn(n: Int) = TokenLocation(index, line, column + n)

    companion object {
        val EMPTY = TokenLocation(-1, -1, -1)
    }
}
