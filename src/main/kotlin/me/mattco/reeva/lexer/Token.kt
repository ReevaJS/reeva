package me.mattco.reeva.lexer

data class Token(
    val type: TokenType,
    val trivia: String,
    val value: String,
    val triviaStart: SourceLocation,
    val valueStart: SourceLocation
) {
    val string = trivia + value

    val isIdentifierName = type == TokenType.Identifier
        || type == TokenType.Await
        || type == TokenType.BooleanLiteral
        || type == TokenType.Break
        || type == TokenType.Case
        || type == TokenType.Catch
        || type == TokenType.Class
        || type == TokenType.Const
        || type == TokenType.Continue
        || type == TokenType.Default
        || type == TokenType.Delete
        || type == TokenType.Do
        || type == TokenType.Else
        || type == TokenType.Enum
        || type == TokenType.Export
        || type == TokenType.Extends
        || type == TokenType.Finally
        || type == TokenType.For
        || type == TokenType.Function
        || type == TokenType.If
        || type == TokenType.Import
        || type == TokenType.In
        || type == TokenType.Instanceof
        || type == TokenType.Interface
        || type == TokenType.Let
        || type == TokenType.New
        || type == TokenType.NullLiteral
        || type == TokenType.Return
        || type == TokenType.Super
        || type == TokenType.Switch
        || type == TokenType.This
        || type == TokenType.Throw
        || type == TokenType.Try
        || type == TokenType.Typeof
        || type == TokenType.Var
        || type == TokenType.Void
        || type == TokenType.While
        || type == TokenType.Yield;

    fun asDouble(): Double {
        if (type != TokenType.NumericLiteral)
            throw IllegalStateException("asDouble called on non-NumericLiteral")

        if (value[0] == '0' && value.length >= 2) {
            if (value[1] == 'x' || value[1] == 'X')
                return value.substring(2).toInt(16).toDouble()
            if (value[1] == 'o' || value[1] == 'O')
                return value.substring(2).toInt(8).toDouble()
            if (value[1] == 'b' || value[1] == 'B')
                return value.substring(2).toInt(2).toDouble()
            if (value[1].isDigit()) {
                // TODO: Syntax error in strict mode
                return value.substring(1).toInt(8).toDouble()
            }
        }
        return value.toDouble()
    }

    fun asString(): String {
        // TODO: Template literal support
        if (type != TokenType.StringLiteral)
            throw IllegalStateException("asString called on non-StringLiteral")

        return StringBuilder().apply {
            var i = 1
            while (i < value.length) {
                if (value[i] == '\\' && i + 2 < value.length) {
                    i++
                    when (value[i]) {
                        'n' -> append('\n')
                        't' -> append('\t')
                        'r' -> append('\r')
                        'b' -> append('\b')
                        'f' -> append('\u000c')
                        'v' -> append('\u000b')
                        '0' -> append('\u0000')
                        '\\' -> append('\\')
                        '"' -> append('"')
                        '\'' -> append('\'')
                        'x' -> TODO()
                        'u' -> TODO()
                        else -> TODO()
                    }
                } else {
                    append(value[i])
                }
                i++
            }
        }.toString()
    }

    fun asBoolean(): Boolean {
        if (type != TokenType.BooleanLiteral)
            throw IllegalStateException("asBoolean called on non-BooleanLiteral")
        return value == "true"
    }
}
