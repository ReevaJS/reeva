package me.mattco.reeva.parser

import me.mattco.reeva.utils.isHexDigit
import me.mattco.reeva.utils.isIdContinue
import me.mattco.reeva.utils.isIdStart
import java.io.File

fun main() {
//    val source = File("./demo/test262.js").readText()
//    val lexer = Lexer(source)
//    runBlocking {
//        val p = lexer.tokens()
//
//        p.collect { println(it) }
//    }

//     222.2
    val source = File("./demo/test262.js").readText()
    simpleMeasureTest(30, 20, 10) {

    }
}

class Lexer(private val source: String) {
    private var lineNum = 0
    private var columnNum = 0
    private var cursor = 0
    private var lastToken = Token.INVALID
    private val templateStates = mutableListOf<TemplateState>()
    private var regexIsInCharClass = false

    var isDone = cursor >= source.length
    private var char = if (isDone) 0.toChar() else source[cursor]

    fun getTokens(): List<Token> {
        val list = mutableListOf<Token>()
        while (!isDone)
            list.add(nextToken())
        return list
    }

    fun nextToken(): Token {
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

        val afterNewline = '\n' in source.substring(triviaStartCursor, cursor)

        val valueStartCursor = cursor
        val valueStartLocation = TokenLocation(cursor, lineNum, columnNum)
        var tokenType = TokenType.Invalid

        if (isDone) {
            tokenType = TokenType.Eof
        } else if (lastToken.type == TokenType.RegexLiteral && !isDone && char.isLetter() && !afterNewline) {
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
            while (char != stopChar && char != '\n' && !isDone) {
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
            tokenType = TokenType.RegexLiteral

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
            var matched = false

            // Only four length token
            if (has(4) && match(shrEquals)) {
                consume(4)
                tokenType = TokenType.UShrEquals
                matched = true
            }

            if (!matched && has(3)) {
                matched = TokenType.threeCharTokens.firstOrNull {
                    match(it.string)
                }?.also {
                    tokenType = it
                    consume(3)
                } != null
            }

            if (!matched && has(2)) {
                matched = TokenType.twoCharTokens.firstOrNull {
                    match(it.string)
                }?.also {
                    tokenType = it
                    consume(2)
                } != null
            }

            if (!matched && has(1)) {
                matched = TokenType.oneCharTokens.firstOrNull {
                    match(it.string)
                }?.also {
                    tokenType = it
                    consume()
                } != null
            }

            if (!matched) {
                consume()
                tokenType = TokenType.Invalid
            }
        }

        if (templateStates.isNotEmpty() && templateStates.last().inExpr) {
            if (tokenType == TokenType.OpenCurly) {
                templateStates.last().openBracketCount++
            } else if (tokenType == TokenType.CloseCurly) {
                templateStates.last().openBracketCount--
            }
        }

        val value = source.substring(valueStartCursor, cursor)
        lastToken = Token(
            tokenType,
            valueStartLocation,
            TokenLocation(cursor, lineNum, columnNum),
            afterNewline,
            value,
            value, // TODO: Raw strings
        )

        return lastToken
    }

    private fun isIdentStart() = char.isIdStart() || char == '_' || char == '$' || (has(2) && match(charArrayOf('\\', 'u')))

    private fun consumeIdentChar() {
        if (has(2) && match(charArrayOf('\\', 'u'))) {
            consume(2)
            if (has(1) && match(charArrayOf('{'))) {
                consume()
                for (i in 0 until 5) {
                    if (!has(1))
                        TODO()
                    if (char == '}') {
                        if (i == 0)
                            TODO()
                        consume()
                        return
                    }
                    if (!char.isHexDigit())
                        TODO()
                    consume()
                }
            } else {
                for (i in 0 until 4) {
                    if (!char.isHexDigit())
                        TODO()
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
            TokenType.RegexLiteral,
            TokenType.StringLiteral,
            TokenType.TemplateLiteralEnd,
            TokenType.This,
            TokenType.True,
        )

        val lineTerminators = listOf('\n', '\u000d', '\u2028', '\u2029')
    }
}
