package me.mattco.jsthing.parser

import me.mattco.jsthing.lexer.Lexer
import me.mattco.jsthing.lexer.SourceLocation
import me.mattco.jsthing.lexer.Token
import me.mattco.jsthing.lexer.TokenType
import me.mattco.jsthing.parser.ast.ASTNode
import me.mattco.jsthing.parser.ast.Script
import me.mattco.jsthing.parser.ast.expressions.*
import me.mattco.jsthing.parser.ast.literals.*
import me.mattco.jsthing.parser.ast.statements.*
import me.mattco.jsthing.parser.ast.statements.flow.*

class Parser(private val source: String) {
    private val lexer = Lexer(source)

    private val tokens = lexer.toMutableList().also {
        it.add(Token(TokenType.Eof, "", "", SourceLocation(-1, -1), SourceLocation(-1, -1)))
    }

    private var state = ParserState()
    private val states = mutableListOf<ParserState>()

    private var cursor: Int
        get() = state.cursor
        set(value) {
            state.cursor = value
        }

    private val token: Token
        get() = tokens[cursor]

    private val tokenType: TokenType
        get() = token.type

    private val isDone: Boolean
        get() = tokenType == TokenType.Eof

    fun parse(): Script {
        val script = Script()

        withScope(ScopeType.Var, ScopeType.Let, ScopeType.Function) {
            var first = true
            state.useStrictState = ParserState.UseStrictState.Looking

            while (!isDone) {
                if (matchStatement()) {
                    script.addStatement(parseStatement())
                    if (first) {
                        if (state.useStrictState == ParserState.UseStrictState.Found) {
                            script.isStrict = true
                            state.strictMode = true
                        }
                        first = false
                        state.useStrictState = ParserState.UseStrictState.None
                    }
                } else {
                    TODO("Error")
                }
            }

            if (state.varScopes.size == 1) {
                script.addVariables(state.varScopes.last())
                script.addVariables(state.letScopes.last())
                script.addFunctions(state.functionScopes.last())
            } else {
                TODO("Error")
            }
        }

        return script
    }

    private fun parseStatement(): Statement {
        return when (tokenType) {
            TokenType.Class -> TODO()
            TokenType.Function -> parseFunctionNode<FunctionDeclaration>().also {
                state.functionScopes.last().add(it)
            }
            TokenType.OpenCurly -> parseBlockStatement().block
            TokenType.Return -> TODO()
            TokenType.Var, TokenType.Let, TokenType.Const -> parseVariableDeclaration()
            TokenType.For -> parseForStatement()
            TokenType.If -> parseIfStatement()
            TokenType.Throw -> TODO()
            TokenType.Try -> TODO()
            TokenType.Break -> TODO()
            TokenType.Continue -> TODO()
            TokenType.Switch -> TODO()
            TokenType.Do -> parseDoWhileStatement()
            TokenType.While -> parseWhileStatement()
            TokenType.Debugger -> TODO()
            TokenType.Semicolon -> {
                consume()
                EmptyStatement()
            }
            else -> {
                if (tokenType == TokenType.Identifier) {
                    // TODO: Labelled Statements
                }

                if (matchExpression()) {
                    ExpressionStatement(parseExpression(0)).also {
                        consumeOrInsertSemicolon()
                    }
                } else TODO("Error")
            }
        }
    }

    private fun parseWhileStatement(): Statement {
        consume(TokenType.While)
        consume(TokenType.OpenParen)
        val test = parseExpression(0)
        consume(TokenType.CloseParen)

        val prevBreak = state.inBreakContext
        val prevContinue = state.inContinueContext
        state.inBreakContext = true
        state.inContinueContext = true

        val body = parseStatement()

        state.inBreakContext = prevBreak
        state.inContinueContext = prevContinue

        return WhileStatement(test, body)
    }

    private fun parseDoWhileStatement(): Statement {
        consume(TokenType.Do)

        val prevBreak = state.inBreakContext
        val prevContinue = state.inContinueContext
        state.inBreakContext = true
        state.inContinueContext = true

        val body = parseStatement()

        state.inBreakContext = prevBreak
        state.inContinueContext = prevContinue

        consume(TokenType.While)
        consume(TokenType.OpenParen)

        val test = parseExpression(0)
        consume(TokenType.CloseParen)
        consumeOrInsertSemicolon()

        return DoWhileStatement(test, body)
    }

    private fun parseIfStatement(): Statement {
        consume(TokenType.If)
        consume(TokenType.OpenParen)
        val predicate = parseExpression(0)
        consume(TokenType.CloseParen)
        val consequent = parseStatement()
        val alternate = if (tokenType == TokenType.Else) {
            consume()
            parseStatement()
        } else null
        return IfStatement(predicate, consequent, alternate)
    }

    private fun parseForStatement(): Statement {
        fun matchForInOf() = tokenType == TokenType.In || (tokenType == TokenType.Identifier && token.value == "of")

        consume(TokenType.For)
        consume(TokenType.OpenParen)

        var inScope = false
        var initializer: ASTNode? = null
        while (tokenType != TokenType.Semicolon) {
            if (matchExpression()) {
                initializer = parseExpression(0, Associativity.Right, listOf(TokenType.In))
                if (matchForInOf())
                    return parseForInOfStatement(initializer)
            } else if (matchVariableDeclaration()) {
                if (tokenType != TokenType.Var) {
                    state.letScopes.add(mutableListOf())
                    inScope = true
                }
                initializer = parseVariableDeclaration(withSemicolon = false)
                if (matchForInOf())
                    return parseForInOfStatement(initializer)
            } else {
                TODO("Error")
            }
        }
        consume(TokenType.Semicolon)

        val test = if (tokenType != TokenType.Semicolon) parseExpression(0) else null
        consume(TokenType.Semicolon)

        val update = if (tokenType != TokenType.CloseParen) parseExpression(0) else null
        consume(TokenType.CloseParen)

        val prevBreak = state.inBreakContext
        val prevContinue = state.inContinueContext
        state.inBreakContext = true
        state.inContinueContext = true

        val body = parseStatement()

        state.inBreakContext = prevBreak
        state.inContinueContext = prevContinue

        if (inScope)
            state.letScopes.removeLast()

        return ForStatement(initializer, test, update, body)
    }

    private fun parseForInOfStatement(lhs: ASTNode): Statement {
        if (lhs is VariableDeclaration) {
            val declarations = lhs.declarations
            if (declarations.size > 1)
                TODO("Error")
            if (declarations.first().initializer != null)
                TODO("Error")
        }

        val type = tokenType
        consume()
        val rhs = parseExpression(0)
        consume(TokenType.CloseParen)

        val prevBreak = state.inBreakContext
        val prevContinue = state.inContinueContext
        state.inBreakContext = true
        state.inContinueContext = true

        val body = parseStatement()

        state.inBreakContext = prevBreak
        state.inContinueContext = prevContinue

        if (type == TokenType.In)
            return ForInStatement(lhs, rhs, body)
        return ForOfStatement(lhs, rhs, body)
    }

    private fun parseVariableDeclaration(withSemicolon: Boolean = true): VariableDeclaration {
        val type = when (tokenType) {
            TokenType.Var -> Declaration.Type.Var
            TokenType.Let -> Declaration.Type.Let
            TokenType.Const -> Declaration.Type.Const
            else -> TODO("Error")
        }

        consume()

        val declarations = mutableListOf<VariableDeclarator>()
        while (true) {
            val id = consume(TokenType.Identifier).value
            val initializer = if (tokenType == TokenType.Equals) {
                consume()
                parseExpression(2)
            } else null

            declarations.add(VariableDeclarator(Identifier(id), initializer))
            if (tokenType == TokenType.Comma) {
                consume()
                continue
            }
            break
        }
        if (withSemicolon)
            consumeOrInsertSemicolon()

        val declaration = VariableDeclaration(type, declarations)
        if (type == Declaration.Type.Var) {
            state.varScopes.last().add(declaration)
        } else {
            state.letScopes.last().add(declaration)
        }
        return declaration
    }

    private fun consumeOrInsertSemicolon() {
        if (tokenType == TokenType.Semicolon) {
            consume()
            return
        }

        if ('\n' in token.trivia)
            return

        if (tokenType == TokenType.CloseCurly)
            return

        if (tokenType == TokenType.Eof)
            return

        TODO("Error")
    }

    private fun parseExpression(minPrecedence: Int, associativity: Associativity = Associativity.Right, forbidden: List<TokenType> = emptyList()): Expression {
        var expression = parsePrimaryExpression()

        while (tokenType == TokenType.TemplateLiteralStart) {
            TODO()
        }

        while (matchSecondaryExpression(forbidden)) {
            val newPrecedence = tokenType.precedence()
            if (newPrecedence < minPrecedence)
                break
            if (newPrecedence == minPrecedence && associativity == Associativity.Left)
                break

            val newAssociativity = tokenType.associativity()
            expression = parseSecondaryExpression(expression, newPrecedence, newAssociativity)
            while (tokenType == TokenType.TemplateLiteralStart) {
                TODO()
            }
        }

        if (tokenType == TokenType.Comma && minPrecedence <= 1) {
            val expressions = mutableListOf(expression)
            while (tokenType == TokenType.Comma) {
                consume()
                expressions.add(parseExpression(2))
            }
            expression = CommaExpression(expressions)
        }

        return expression
    }

    private fun parsePrimaryExpression(): Expression {
        if (matchUnaryPrefixedExpression())
            return parseUnaryPrefixedExpression()

        return when (tokenType) {
            TokenType.OpenParen -> {
                consume()
                if (matchAny(TokenType.CloseParen, TokenType.Identifier, TokenType.TripleDot)) {
                    TODO()
                }
                parseExpression(0).also {
                    consume(TokenType.CloseParen)
                }
            }
            TokenType.This -> {
                consume()
                ThisExpression
            }
            TokenType.Class -> TODO()
            TokenType.Super -> TODO()
            TokenType.Identifier -> {
                // TODO: arrow function
                Identifier(token.value).also {
                    consume()
                }
            }
            TokenType.NumericLiteral -> NumericLiteral(consume().asDouble())
            TokenType.BigIntLiteral -> TODO()
            TokenType.BooleanLiteral -> BooleanLiteral(consume().asBoolean())
            TokenType.StringLiteral -> StringLiteral(consume().asString())
            TokenType.NullLiteral -> {
                consume()
                NullLiteral
            }
            TokenType.OpenCurly -> TODO()
            TokenType.Function -> TODO()
            TokenType.OpenBracket -> TODO()
            TokenType.RegexLiteral -> TODO()
            TokenType.TemplateLiteralStart -> TODO()
            TokenType.New -> TODO()
            else -> TODO()
        }
    }

    private fun parseSecondaryExpression(lhs: Expression, minPrecedence: Int, associativity: Associativity): Expression {
        BinaryExpression.Operation.fromTokenType(tokenType)?.let {
            consume()
            return BinaryExpression(lhs, parseExpression(minPrecedence, associativity), it)
        }

        AssignmentExpression.Operation.fromTokenType(tokenType)?.let {
            consume()

            when {
                lhs !is Identifier && lhs !is MemberExpression && lhs !is CallExpression -> TODO("Error")
                state.strictMode && lhs is Identifier -> {
                    val name = lhs.string
                    if (name == "eval" || name == "arguments")
                        TODO("Error")
                }
                state.strictMode && lhs is CallExpression -> TODO("error")
            }

            return AssignmentExpression(lhs, parseExpression(minPrecedence, associativity), it)
        }

        if (tokenType == TokenType.Period) {
            consume()
            if (!token.isIdentifierName)
                TODO("Error")
            return MemberExpression(lhs, Identifier(consume().value))
        }

        if (tokenType == TokenType.OpenParen)
            return parseCallExpression(lhs)

        if (tokenType == TokenType.OpenBracket) {
            consume()
            val expression = MemberExpression(lhs, parseExpression(0), computed = true)
            consume(TokenType.CloseBracket)
            return expression
        }

        LogicalExpression.Operation.fromTokenType(tokenType)?.let {
            consume()
            return LogicalExpression(lhs, parseExpression(minPrecedence, associativity), it)
        }

        if (matchAny(TokenType.PlusPlus, TokenType.MinusMinus)) {
            val increment = tokenType == TokenType.PlusPlus
            if (lhs !is Identifier && lhs !is MemberExpression)
                TODO("Error")
            consume()
            return UpdateExpression(lhs, increment, prefixed = false)
        }

        TODO("Error")
    }

    private fun parseCallExpression(lhs: Expression): CallExpression {
        consume(TokenType.OpenParen)
        val arguments = mutableListOf<CallExpression.Argument>()

        while (matchExpression() || tokenType == TokenType.TripleDot) {
            if (tokenType == TokenType.TripleDot) {
                consume()
                arguments.add(CallExpression.Argument(parseExpression(2), isSpread = true))
            } else {
                arguments.add(CallExpression.Argument(parseExpression(2), isSpread = false))
            }
            if (tokenType != TokenType.Comma)
                break
            consume()
        }

        consume(TokenType.CloseParen)
        return CallExpression(lhs, arguments)
    }

    private fun parseUnaryPrefixedExpression(): Expression {
        val precedence = tokenType.precedence()
        val associativity = tokenType.associativity()
        return when (tokenType) {
            TokenType.PlusPlus, TokenType.MinusMinus -> {
                val increment = tokenType == TokenType.PlusPlus
                consume()
                val rhs = parseExpression(precedence, associativity)
                if (rhs !is Identifier && rhs !is MemberExpression)
                    TODO("Error")
                UpdateExpression(rhs, increment = increment, prefixed = true)
            }
            TokenType.Exclamation -> {
                consume()
                UnaryExpression(parseExpression(precedence, associativity), UnaryExpression.Operation.Not)
            }
            TokenType.Tilde -> {
                consume()
                UnaryExpression(parseExpression(precedence, associativity), UnaryExpression.Operation.BitwiseNot)
            }
            TokenType.Plus -> {
                consume()
                UnaryExpression(parseExpression(precedence, associativity), UnaryExpression.Operation.Plus)
            }
            TokenType.Minus -> {
                consume()
                UnaryExpression(parseExpression(precedence, associativity), UnaryExpression.Operation.Minus)
            }
            TokenType.Typeof -> {
                consume()
                UnaryExpression(parseExpression(precedence, associativity), UnaryExpression.Operation.Typeof)
            }
            TokenType.Void -> {
                consume()
                UnaryExpression(parseExpression(precedence, associativity), UnaryExpression.Operation.Void)
            }
            TokenType.Delete -> {
                consume()
                UnaryExpression(parseExpression(precedence, associativity), UnaryExpression.Operation.Delete)
            }
            else -> {
                TODO("Error")
            }
        }
    }

    data class BlockStatementResult(val block: BlockStatement, val isStrict: Boolean)

    private fun parseBlockStatement(): BlockStatementResult {
        val block = BlockStatement()
        var isStrict = false

        withScope(ScopeType.Let) {
            consume(TokenType.OpenCurly)

            var first = true
            val initialStrictModeState = state.strictMode
            state.useStrictState = if (initialStrictModeState) {
                ParserState.UseStrictState.None
            } else ParserState.UseStrictState.Looking

            while (!isDone && tokenType != TokenType.CloseCurly) {
                if (tokenType == TokenType.Semicolon) {
                    consume()
                } else if (matchStatement()) {
                    block.addStatement(parseStatement())

                    if (first && !initialStrictModeState) {
                        if (state.useStrictState == ParserState.UseStrictState.Found) {
                            isStrict = true
                            state.strictMode = true
                        }
                        state.useStrictState = ParserState.UseStrictState.None
                    }
                } else {
                    TODO("Error")
                }

                first = false
            }

            consume(TokenType.CloseCurly)
            state.strictMode = initialStrictModeState
            block.addVariables(state.letScopes.last())
            block.addFunctions(state.functionScopes.last())
        }

        return BlockStatementResult(block, isStrict)
    }

    private fun matchStatement() = matchExpression() || matchAny(
        TokenType.Function,
        TokenType.Return,
        TokenType.Let,
        TokenType.Class,
        TokenType.Do,
        TokenType.If,
        TokenType.Throw,
        TokenType.Try,
        TokenType.While,
        TokenType.For,
        TokenType.Const,
        TokenType.OpenCurly,
        TokenType.Switch,
        TokenType.Break,
        TokenType.Continue,
        TokenType.Var,
        TokenType.Debugger,
        TokenType.Semicolon,
    )

    private fun matchExpression() = matchUnaryPrefixedExpression() || matchAny(
        TokenType.BooleanLiteral,
        TokenType.NumericLiteral,
        TokenType.BigIntLiteral,
        TokenType.StringLiteral,
        TokenType.TemplateLiteralStart,
        TokenType.NullLiteral,
        TokenType.Identifier,
        TokenType.New,
        TokenType.OpenCurly,
        TokenType.OpenBracket,
        TokenType.OpenParen,
        TokenType.Function,
        TokenType.This,
        TokenType.Super,
        TokenType.RegexLiteral,
    )

    private fun matchUnaryPrefixedExpression() = matchAny(
        TokenType.PlusPlus,
        TokenType.MinusMinus,
        TokenType.Exclamation,
        TokenType.Tilde,
        TokenType.Plus,
        TokenType.Minus,
        TokenType.Typeof,
        TokenType.Void,
        TokenType.Delete,
    )

    private fun matchVariableDeclaration() = matchAny(TokenType.Var, TokenType.Let, TokenType.Const)

    private fun matchSecondaryExpression(forbidden: List<TokenType>) = tokenType !in forbidden && matchAny(
        TokenType.Plus,
        TokenType.PlusEquals,
        TokenType.Minus,
        TokenType.MinusEquals,
        TokenType.Asterisk,
        TokenType.AsteriskEquals,
        TokenType.Slash,
        TokenType.SlashEquals,
        TokenType.Percent,
        TokenType.PercentEquals,
        TokenType.DoubleAsterisk,
        TokenType.DoubleAsteriskEquals,
        TokenType.Equals,
        TokenType.TripleEquals,
        TokenType.ExclamationDoubleEquals,
        TokenType.DoubleEquals,
        TokenType.ExclamationEquals,
        TokenType.GreaterThan,
        TokenType.GreaterThanEquals,
        TokenType.LessThan,
        TokenType.LessThanEquals,
        TokenType.OpenParen,
        TokenType.Period,
        TokenType.OpenBracket,
        TokenType.PlusPlus,
        TokenType.MinusMinus,
        TokenType.In,
        TokenType.Instanceof,
        TokenType.Question,
        TokenType.Ampersand,
        TokenType.AmpersandEquals,
        TokenType.Pipe,
        TokenType.PipeEquals,
        TokenType.Caret,
        TokenType.CaretEquals,
        TokenType.ShiftLeft,
        TokenType.ShiftLeftEquals,
        TokenType.ShiftRight,
        TokenType.ShiftRightEquals,
        TokenType.UnsignedShiftRight,
        TokenType.UnsignedShiftRightEquals,
        TokenType.DoubleAmpersand,
        TokenType.DoubleAmpersandEquals,
        TokenType.DoublePipe,
        TokenType.DoublePipeEquals,
        TokenType.DoubleQuestion,
        TokenType.DoubleQuestionEquals,
    )

    private inline fun <reified T> parseFunctionNode(): T {
        TODO()
    }

    private fun consume(): Token {
        return token.also {
            cursor++
        }
    }

    private fun consume(type: TokenType): Token {
        if (type != tokenType)
            TODO()
        return consume()
    }

    private fun has(n: Int) = cursor + n < tokens.size

    private fun peek(n: Int) = tokens[cursor + n]

    private fun withScope(vararg types: ScopeType, block: Parser.() -> Unit) {
        if (ScopeType.Var in types)
            state.varScopes.add(mutableListOf())
        if (ScopeType.Let in types)
            state.letScopes.add(mutableListOf())
        if (ScopeType.Function in types)
            state.functionScopes.add(mutableListOf())

        apply(block)

        if (ScopeType.Var in types)
            state.varScopes.removeLast()
        if (ScopeType.Let in types)
            state.letScopes.removeLast()
        if (ScopeType.Function in types)
            state.functionScopes.removeLast()
    }

    private fun saveState() {
        states.add(state.copy())
    }

    private fun loadState() {
        state = states.removeLast()
    }

    private fun match(type: TokenType) = tokenType == type

    private fun matchAny(vararg types: TokenType) = tokenType in types

    private fun <T> guard(final: () -> Unit, block: (disable: () -> Unit) -> T): T {
        var disabled = false
        val result = block { disabled = true }
        if (!disabled)
            final()
        return result
    }

    enum class ScopeType {
        Var,
        Let,
        Function
    }

    enum class Associativity {
        Left,
        Right
    }

    companion object {
        private val reservedWords = listOf(
            "await",
            "break",
            "case",
            "catch",
            "class",
            "const",
            "continue",
            "debugger",
            "default",
            "delete",
            "do",
            "else",
            "enum",
            "export",
            "extends",
            "false",
            "finally",
            "for",
            "function",
            "if",
            "import",
            "in",
            "instanceof",
            "new",
            "null",
            "return",
            "super",
            "switch",
            "this",
            "throw",
            "true",
            "try",
            "typeof",
            "var",
            "void",
            "while",
            "with",
            "yield",
        )

        private fun isReserved(identifier: String) = identifier in reservedWords

        fun TokenType.precedence() = when (this) {
            TokenType.Period -> 20
            TokenType.OpenBracket -> 20
            TokenType.OpenParen -> 20
            TokenType.QuestionPeriod -> 20
            TokenType.New -> 19
            TokenType.PlusPlus -> 18
            TokenType.MinusMinus -> 18
            TokenType.Exclamation -> 17
            TokenType.Tilde -> 17
            TokenType.Typeof -> 17
            TokenType.Void -> 17
            TokenType.Delete -> 17
            TokenType.Await -> 17
            TokenType.DoubleAsterisk -> 16
            TokenType.Asterisk -> 15
            TokenType.Slash -> 15
            TokenType.Percent -> 15
            TokenType.Plus -> 14
            TokenType.Minus -> 14
            TokenType.ShiftLeft -> 13
            TokenType.ShiftRight -> 13
            TokenType.UnsignedShiftRight -> 13
            TokenType.LessThan -> 12
            TokenType.LessThanEquals -> 12
            TokenType.GreaterThan -> 12
            TokenType.GreaterThanEquals -> 12
            TokenType.In -> 12
            TokenType.Instanceof -> 12
            TokenType.DoubleEquals -> 11
            TokenType.ExclamationEquals -> 11
            TokenType.TripleEquals -> 11
            TokenType.ExclamationDoubleEquals -> 11
            TokenType.Ampersand -> 10
            TokenType.Caret -> 9
            TokenType.Pipe -> 8
            TokenType.DoubleQuestion -> 7
            TokenType.DoubleAmpersand -> 6
            TokenType.DoublePipe -> 5
            TokenType.Question -> 4
            TokenType.Equals -> 3
            TokenType.PlusEquals -> 3
            TokenType.MinusEquals -> 3
            TokenType.DoubleAsteriskEquals -> 3
            TokenType.AsteriskEquals -> 3
            TokenType.SlashEquals -> 3
            TokenType.PercentEquals -> 3
            TokenType.ShiftLeftEquals -> 3
            TokenType.ShiftRightEquals -> 3
            TokenType.UnsignedShiftRightEquals -> 3
            TokenType.AmpersandEquals -> 3
            TokenType.CaretEquals -> 3
            TokenType.PipeEquals -> 3
            TokenType.DoubleAmpersandEquals -> 3
            TokenType.DoublePipeEquals -> 3
            TokenType.DoubleQuestionEquals -> 3
            TokenType.Yield -> 2
            TokenType.Comma -> 1
            else -> TODO("Error")
        }

        fun TokenType.associativity() = when (this) {
            TokenType.Period,
            TokenType.OpenBracket,
            TokenType.OpenParen,
            TokenType.QuestionPeriod,
            TokenType.Asterisk,
            TokenType.Slash,
            TokenType.Percent,
            TokenType.Plus,
            TokenType.Minus,
            TokenType.ShiftLeft,
            TokenType.ShiftRight,
            TokenType.UnsignedShiftRight,
            TokenType.LessThan,
            TokenType.LessThanEquals,
            TokenType.GreaterThan,
            TokenType.GreaterThanEquals,
            TokenType.In,
            TokenType.Instanceof,
            TokenType.DoubleEquals,
            TokenType.ExclamationEquals,
            TokenType.TripleEquals,
            TokenType.ExclamationDoubleEquals,
            TokenType.Typeof,
            TokenType.Void,
            TokenType.Delete,
            TokenType.Ampersand,
            TokenType.Caret,
            TokenType.Pipe,
            TokenType.DoubleQuestion,
            TokenType.DoubleAmpersand,
            TokenType.DoublePipe,
            TokenType.Comma -> Associativity.Left
            else -> Associativity.Right
        }
    }
}
