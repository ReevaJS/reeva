package com.reevajs.reeva.parsing.lexer

import com.reevajs.reeva.utils.isHexDigit
import com.reevajs.reeva.utils.isIdContinue
import com.reevajs.reeva.utils.isIdStart
import com.reevajs.reeva.utils.isLineSeparator

class Lexer(private val source: String) {
    private var lineNum = 0
    private var columnNum = 0
    private var cursor = 0
    private var lastToken = Token.INVALID
    private val templateStates = mutableListOf<TemplateState>()
    private var regexIsInCharClass = false

    private var isDone = cursor >= source.length
    private var char = if (isDone) 0.toChar() else source[cursor]

    fun getTokens(): List<Token> {
        val list = mutableListOf<Token>()
        while (true) {
            val next = nextToken()
            list.add(next)
            if (next.type == TokenType.Eof)
                break
        }
        return list
    }

    private fun nextToken(): Token {
        try {
            val triviaStartCursor = cursor
            val inTemplate = templateStates.isNotEmpty()

            if (!inTemplate || templateStates.last().inExpr) {
                // Consume whitespace and comments
                while (!isDone) {
                    if (char.isWhitespace()) {
                        do {
                            consume()
                        } while (!isDone && char.isWhitespace())
                    } else if (isCommentStart()) {
                        consume()
                        do {
                            consume()
                        } while (!isDone && char != '\n')
                    } else if (has(2) && match(multilineCommentStart)) {
                        consume()
                        do {
                            consume()
                        } while (has(2) && !match(multilineCommentEnd))

                        if (!isDone)
                            consume(2)
                    } else break
                }
            }

            val afterNewline = source.substring(triviaStartCursor, cursor).any { it.isLineSeparator() }

            val valueStartCursor = cursor
            val valueStartLocation = TokenLocation(cursor, lineNum, columnNum)
            var tokenType: TokenType

            if (isDone) {
                tokenType = TokenType.Eof
            } else if (lastToken.type == TokenType.RegExpLiteral && !isDone && char.isLetter() && !afterNewline) {
                tokenType = TokenType.RegexFlags
                while (!isDone && char.isLetter())
                    consume()
            } else if (char == '`') {
                consume()

                if (!inTemplate) {
                    tokenType = TokenType.TemplateLiteralStart
                    templateStates.add(TemplateState())
                } else {
                    tokenType = if (templateStates.last().inExpr) {
                        templateStates.add(TemplateState())
                        TokenType.TemplateLiteralStart
                    } else {
                        templateStates.removeLast()
                        TokenType.TemplateLiteralEnd
                    }
                }
            } else if (inTemplate && templateStates.last().let { it.inExpr && it.openBracketCount == 0 } && char == '}') {
                consume()
                tokenType = TokenType.TemplateLiteralExprEnd
                templateStates.last().inExpr = false
            } else if (inTemplate && !templateStates.last().inExpr) {
                when {
                    isDone -> {
                        tokenType = TokenType.UnterminatedTemplateLiteral
                        templateStates.removeLast()
                    }
                    has(2) && match(templateExprStart) -> {
                        tokenType = TokenType.TemplateLiteralExprStart
                        consume(2)
                        templateStates.last().inExpr = true
                    }
                    else -> {
                        while (has(2) && !match(templateExprStart) && char != '`') {
                            if (match(escapedDollar) || match(escapedGrave))
                                consume()
                            consume()
                        }
                        tokenType = TokenType.TemplateLiteralString
                    }
                }
            } else if (isIdentStart()) {
                do {
                    consumeIdentChar()
                } while (isIdentMiddle())

                tokenType = keywordFromStr(source.substring(valueStartCursor, cursor))
            } else if (isNumberLiteralStart()) {
                tokenType = TokenType.NumericLiteral
                if (char == '0') {
                    consume()
                    when (char) {
                        '.' -> {
                            consume()
                            while (char.isDigit())
                                consume()
                            if (char == 'e' || char == 'E')
                                consumeExponent()
                        }
                        'e', 'E' -> consumeExponent()
                        'o', 'O' -> {
                            consume()
                            while (char in '0'..'7')
                                consume()
                            if (char == 'n') {
                                consume()
                                tokenType = TokenType.BigIntLiteral
                            }
                        }
                        'b', 'B' -> {
                            consume()
                            while (char == '0' || char == '1')
                                consume()
                            if (char == 'n') {
                                consume()
                                tokenType = TokenType.BigIntLiteral
                            }
                        }
                        'x', 'X' -> {
                            consume()
                            while (char.isHexDigit())
                                consume()
                            if (char == 'n') {
                                consume()
                                tokenType = TokenType.BigIntLiteral
                            }
                        }
                        'n' -> {
                            consume()
                            tokenType = TokenType.BigIntLiteral
                        }
                        else -> {
                            if (char.isDigit()) {
                                // Octal without 'O' prefix
                                // TODO: Prevent in strict mode
                                do {
                                    consume()
                                } while (char in '0'..'7')
                            }
                        }
                    }
                } else {
                    while (char.isDigit() || char == '_')
                        consume()
                    if (char == 'n') {
                        consume()
                        tokenType = TokenType.BigIntLiteral
                    } else {
                        if (char == '.') {
                            consume()
                            while (char.isDigit() || char == '_')
                                consume()
                        }
                        if (char == 'e' || char == 'E')
                            consumeExponent()
                    }
                }
            } else if (char == '"' || char == '\'') {
                val stopChar = char
                consume()

                // Note: Line breaks are invalid in string literals, but this is
                // handled with a nice error message in the parser, as well as
                // string unescaping
                while (char != stopChar && !isDone) {
                    if (char == '\\')
                        consume()
                    consume()
                }
                tokenType = if (char != stopChar) {
                    TokenType.UnterminatedStringLiteral
                } else {
                    consume()
                    TokenType.StringLiteral
                }
            } else if (char == '/' && lastToken.type !in slashMeansDivision) {
                consume()
                tokenType = TokenType.RegExpLiteral

                while (!isDone) {
                    if (char == '[') {
                        regexIsInCharClass = true
                    } else if (char == ']') {
                        regexIsInCharClass = false
                    } else if (!regexIsInCharClass && char == '/') {
                        break
                    }

                    if (has(2) && (match(escapedSlash) || match(escapedOpenBracket) ||
                            match(escapedBackslash) || (regexIsInCharClass && match(escapedCloseBracket)))
                    ) {
                        consume()
                    }
                    consume()
                }

                if (isDone) {
                    tokenType = TokenType.UnterminatedRegexLiteral
                } else {
                    consume()
                }
            } else {
                val type = matchSymbolicToken()
                tokenType = type

                if (type == TokenType.Invalid) {
                    consume()
                } else {
                    consume(type.string.length)
                }
            }

            if (templateStates.isNotEmpty() && templateStates.last().inExpr) {
                if (tokenType == TokenType.OpenCurly) {
                    templateStates.last().openBracketCount++
                } else if (tokenType == TokenType.CloseCurly) {
                    templateStates.last().openBracketCount--
                }
            }

            val value = if (tokenType == TokenType.StringLiteral) {
                source.substring(valueStartCursor + 1, cursor - 1)
            } else source.substring(valueStartCursor, cursor)

            lastToken = Token(
                tokenType,
                valueStartLocation,
                TokenLocation(cursor, lineNum, columnNum),
                afterNewline,
                value,
                value, // TODO: Raw strings
            )

            return lastToken
        } catch (e: Throwable) {
            return Token(
                TokenType.Invalid,
                TokenLocation(cursor, lineNum, columnNum),
                TokenLocation(cursor, lineNum, columnNum),
                false,
                e.message!!,
                "",
            )
        }
    }

    private fun isIdentStart() = char.isIdStart() || char == '_' || char == '$' || (has(2) && match(charArrayOf('\\', 'u')))

    private fun consumeIdentChar() {
        if (has(2) && match(charArrayOf('\\', 'u'))) {
            consume(2)
            if (has(1) && match(charArrayOf('{'))) {
                consume()
                for (i in 0 until 5) {
                    if (!has(1))
                        throw Error("Unexpected EOF in unicode escape sequence")
                    if (char == '}') {
                        if (i == 0)
                            throw Error("Brace-enclosed unicode escape sequence cannot be empty")
                        consume()
                        return
                    }
                    if (!char.isHexDigit())
                        throw Error("Unexpected char '$char' in brace-enclosed unicode escape sequence")
                    consume()
                }
            } else {
                for (i in 0 until 4) {
                    if (!char.isHexDigit())
                        throw Error("Unexpected char '$char' in unicode escape sequence")
                    consume()
                }
            }
        } else {
            consume()
        }
    }

    private fun keywordFromStr(str: String): TokenType {
        val maybeKeyword: TokenType = when (str[0]) {
            'a' -> if (str.length > 1 && str[1] == 's') TokenType.Async else TokenType.Await
            'b' -> TokenType.Break
            'c' -> when (str.length) {
                4 -> TokenType.Case
                5 -> when (str[1]) {
                    'a' -> TokenType.Catch
                    'l' -> TokenType.Class
                    'o' -> TokenType.Const
                    else -> return TokenType.Identifier
                }
                8 -> TokenType.Continue
                else -> return TokenType.Identifier
            }
            'd' -> when (str.length) {
                2 -> TokenType.Do
                6 -> TokenType.Delete
                7 -> TokenType.Default
                8 -> TokenType.Debugger
                else -> return TokenType.Identifier
            }
            'e' -> when (str.length) {
                4 -> if (str[1] == 'l') TokenType.Else else TokenType.Enum
                6 -> TokenType.Export
                7 -> TokenType.Extends
                else -> return TokenType.Identifier
            }
            'f' -> when (str.length) {
                3 -> TokenType.For
                5 -> TokenType.False
                7 -> TokenType.Finally
                8 -> TokenType.Function
                else -> return TokenType.Identifier
            }
            'i' -> when (str.length) {
                2 -> when (str[1]) {
                    'f' -> TokenType.If
                    'n' -> TokenType.In
                    else -> TokenType.Identifier
                }
                6 -> TokenType.Import
                9 -> TokenType.Interface
                10 -> if (str[1] == 'm') TokenType.Implements else TokenType.Instanceof
                else -> TokenType.Identifier
            }
            'l' -> TokenType.Let
            'n' -> when (str.length) {
                3 -> TokenType.New
                4 -> TokenType.NullLiteral
                else -> return TokenType.Identifier
            }
            'p' -> when (str.length) {
                6 -> TokenType.Public
                7 -> when (str[2]) {
                    'c' -> TokenType.Package
                    'i' -> TokenType.Private
                    else -> return TokenType.Identifier
                }
                9 -> TokenType.Protected
                else -> return TokenType.Identifier
            }
            'r' -> TokenType.Return
            's' -> when (str.length) {
                5 -> TokenType.Super
                6 -> if (str[1] == 't') TokenType.Static else TokenType.Switch
                else -> return TokenType.Identifier
            }
            't' -> when (str.length) {
                3 -> TokenType.Try
                4 -> if (str[1] == 'h') TokenType.This else TokenType.True
                5 -> TokenType.Throw
                6 -> TokenType.Typeof
                else -> return TokenType.Identifier
            }
            'v' -> when (str.length) {
                3 -> TokenType.Var
                4 -> TokenType.Void
                else -> return TokenType.Identifier
            }
            'w' -> when (str.length) {
                4 -> TokenType.With
                5 -> TokenType.While
                else -> return TokenType.Identifier
            }
            'y' -> TokenType.Yield
            else -> return TokenType.Identifier
        }

        return if (maybeKeyword.string == str) maybeKeyword else TokenType.Identifier
    }

    private fun matchSymbolicToken(): TokenType {
        return when (char) {
            ']' -> TokenType.CloseBracket
            '}' -> TokenType.CloseCurly
            ')' -> TokenType.CloseParen
            ':' -> TokenType.Colon
            ',' -> TokenType.Comma
            '[' -> TokenType.OpenBracket
            '{' -> TokenType.OpenCurly
            '(' -> TokenType.OpenParen
            ';' -> TokenType.Semicolon
            '+' -> if (has(1)) {
                when (peek(1)) {
                    '=' -> TokenType.AddEquals
                    '+' -> TokenType.Inc
                    else -> return TokenType.Add
                }
            } else TokenType.Add
            '-' -> if (has(1)) {
                when (peek(1)) {
                    '=' -> TokenType.SubEquals
                    '-' -> TokenType.Dec
                    else -> return TokenType.Sub
                }
            } else TokenType.Sub
            '*' -> if (has(1)) {
                when (peek(1)) {
                    '=' -> TokenType.MulEquals
                    '*' -> if (has(2) && peek(2) == '=') TokenType.ExpEquals else TokenType.Exp
                    else -> TokenType.Mul
                }
            } else TokenType.Mul
            // we've already checked for regex before this function
            '/' -> if (has(1) && peek(1) == '=') TokenType.DivEquals else TokenType.Div
            '%' -> if (has(1) && peek(1) == '=') TokenType.ModEquals else TokenType.Mod
            '&' -> if (has(1)) {
                when (peek(1)) {
                    '=' -> TokenType.BitwiseAndEquals
                    '&' -> if (has(2) && peek(2) == '=') TokenType.AndEquals else TokenType.And
                    else -> TokenType.BitwiseAnd
                }
            } else TokenType.BitwiseAnd
            '|' -> if (has(1)) {
                when (peek(1)) {
                    '=' -> TokenType.BitwiseOrEquals
                    '|' -> if (has(2) && peek(2) == '=') TokenType.OrEquals else TokenType.Or
                    else -> TokenType.BitwiseOr
                }
            } else TokenType.BitwiseOr
            '^' -> if (has(1) && peek(1) == '=') TokenType.BitwiseXorEquals else TokenType.BitwiseXor
            '~' -> TokenType.BitwiseNot
            '!' -> if (has(1) && peek(1) == '=') {
                if (has(2) && peek(2) == '=') TokenType.StrictNotEquals else TokenType.SloppyNotEquals
            } else TokenType.Not
            '=' -> if (has(1)) {
                when (peek(1)) {
                    '>' -> TokenType.Arrow
                    '=' -> if (has(2) && peek(2) == '=') TokenType.StrictEquals else TokenType.SloppyEquals
                    else -> TokenType.Equals
                }
            } else TokenType.Equals
            '<' -> if (has(1)) {
                when (peek(1)) {
                    '=' -> TokenType.LessThanEquals
                    '<' -> if (has(2) && peek(2) == '=') TokenType.ShlEquals else TokenType.Shl
                    else -> TokenType.LessThan
                }
            } else TokenType.LessThan
            '>' -> if (has(1)) {
                when (peek(1)) {
                    '=' -> TokenType.GreaterThanEquals
                    '>' -> if (has(2)) {
                        when (peek(2)) {
                            '=' -> TokenType.ShrEquals
                            '>' -> if (has(3) && peek(3) == '=') TokenType.UShrEquals else TokenType.UShr
                            else -> TokenType.Shr
                        }
                    } else TokenType.Shr
                    else -> TokenType.GreaterThan
                }
            } else TokenType.GreaterThan
            '.' -> if (has(2) && peek(1) == '.' && peek(2) == '.') {
                TokenType.TriplePeriod
            } else TokenType.Period
            '?' -> if (has(1)) {
                when (peek(1)) {
                    '.' -> if (has(2) && !peek(2).isDigit()) {
                        TokenType.OptionalChain
                    } else TokenType.QuestionMark
                    '?' -> if (has(2) && peek(2) == '=') TokenType.CoalesceEquals else TokenType.Coalesce
                    else -> TokenType.QuestionMark
                }
            } else TokenType.QuestionMark
            '#' -> TokenType.Hash
            else -> TokenType.Invalid
        }
    }

    private fun isIdentMiddle() = isIdentStart() || char.isIdContinue()

    private fun isCommentStart() =
        (has(2) && match(charArrayOf('/', '/'))) ||
        (has(4) && match(charArrayOf('<', '!', '-', '-')))

    private fun isNumberLiteralStart() =
        char.isDigit() || (has(1) && char == '.' && peek(1).isDigit())

    private fun peek(n: Int): Char {
        if (cursor + n >= source.length)
            throw IllegalArgumentException("Attempt to peek out of string bounds")

        return source[cursor + n]
    }

    private fun has(n: Int): Boolean = cursor + n < source.length

    private fun match(chars: CharArray): Boolean {
        for ((i, char) in chars.withIndex()) {
            if (source[cursor + i] != char)
                return false
        }

        return true
    }

    private fun match(string: String): Boolean {
        for ((index, char) in string.withIndex()) {
            if (source[cursor + index] != char)
                return false
        }

        return true
    }

    private fun consume(times: Int = 1) {
        if (cursor + times > source.length)
            throw IllegalStateException("Attempt to consume character from exhausted Lexer")

        repeat(times) {
            if (source[cursor] == '\n') {
                lineNum++
                columnNum = 0
            } else {
                columnNum++
            }

            cursor++
        }

        isDone = cursor >= source.length
        char = if (isDone) 0.toChar() else source[cursor]
    }

    private fun consumeExponent() {
        consume()
        if (char == '-' || char == '+')
            consume()
        while (char.isDigit())
            consume()
    }

    data class TemplateState(var inExpr: Boolean = false, var openBracketCount: Int = 0)

    companion object {
        private val multilineCommentStart = charArrayOf('/', '*')
        private val multilineCommentEnd = charArrayOf('*', '/')
        private val templateExprStart = charArrayOf('$', '{')
        private val escapedDollar = charArrayOf('\\', '$')
        private val escapedGrave = charArrayOf('\\', '`')
        private val escapedSlash = charArrayOf('\\', '/')
        private val escapedBackslash = charArrayOf('\\', '\\')
        private val escapedOpenBracket = charArrayOf('\\', '[')
        private val escapedCloseBracket = charArrayOf('\\', ']')
        private val shrEquals = charArrayOf('>', '>', '>', '=')

        private val slashMeansDivision = listOf(
            TokenType.BigIntLiteral,
            TokenType.CloseBracket,
            TokenType.CloseCurly,
            TokenType.CloseParen,
            TokenType.False,
            TokenType.Identifier,
            TokenType.NullLiteral,
            TokenType.NumericLiteral,
            TokenType.RegExpLiteral,
            TokenType.StringLiteral,
            TokenType.TemplateLiteralEnd,
            TokenType.This,
            TokenType.True,
        )

        val lineTerminators = listOf('\n', '\u000d', '\u2028', '\u2029')
    }
}
