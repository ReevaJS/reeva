package me.mattco.jsthing.parser

import me.mattco.jsthing.ast.*
import me.mattco.jsthing.ast.expressions.*
import me.mattco.jsthing.ast.literals.NullNode
import me.mattco.jsthing.ast.literals.NumericLiteralNode
import me.mattco.jsthing.ast.literals.StringLiteralNode
import me.mattco.jsthing.ast.literals.ThisNode
import me.mattco.jsthing.ast.statements.*
import me.mattco.jsthing.lexer.Lexer
import me.mattco.jsthing.lexer.SourceLocation
import me.mattco.jsthing.lexer.Token
import me.mattco.jsthing.lexer.TokenType
import me.mattco.jsthing.utils.all
import me.mattco.jsthing.utils.unreachable

class Parser(text: String) {
    private val tokens = Lexer(text).toList() + Token(TokenType.Eof, "", "", SourceLocation(-1, -1), SourceLocation(-1, -1))

    private val syntaxErrors = mutableListOf<SyntaxError>()
    private lateinit var goalSymbol: GoalSymbol

    private var state = State(0)
    private val stateStack = mutableListOf(state)

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

    fun parseScript(): ScriptNode {
        goalSymbol = GoalSymbol.Script
        val statementList = parseStatementList(emptySet())
        if (!isDone)
            unexpected("token ${token.value}")
        return ScriptNode(statementList?.statements ?: emptyList())
    }

    fun parseModule() {
        goalSymbol = GoalSymbol.Module
        TODO()
    }

    private fun parseScriptBody(suffixes: Set<Suffix>): StatementListNode? {
        return parseStatementList(suffixes - Suffix.Yield - Suffix.Await - Suffix.Return)
    }

    private fun parseStatementList(suffixes: Set<Suffix>): StatementListNode? {
        val statements = mutableListOf<StatementListItem>()

        var statement = parseStatementListItem(suffixes) ?: return null
        statements.add(statement)

        while (true) {
            statement = parseStatementListItem(suffixes) ?: break
            statements.add(statement)
        }

        return StatementListNode(statements)
    }

    private fun parseStatementListItem(suffixes: Set<Suffix>): StatementListItem? {
        return (parseStatement(suffixes) ?: parseDeclaration(suffixes - Suffix.Return))?.let(::StatementListItem)
    }

    private fun parseStatement(suffixes: Set<Suffix>): StatementNode? {
        return parseBlockStatement(suffixes) ?:
                parseVariableStatement(suffixes - Suffix.Return) ?:
                parseEmptyStatement() ?:
                parseExpressionStatement(suffixes - Suffix.Return) ?:
                parseIfStatement(suffixes) ?:
                parseBreakableStatement(suffixes) ?:
                parseContinueStatement(suffixes - Suffix.Return) ?:
                parseBreakStatement(suffixes - Suffix.Return) ?:
                parseReturnStatement(suffixes + Suffix.Return) ?:
                parseWithStatement(suffixes) ?:
                parseLabelledStatement(suffixes) ?:
                parseThrowStatement(suffixes - Suffix.Return) ?:
                parseTryStatement(suffixes) ?:
                parseDebuggerStatement()
    }

    private fun parseBlockStatement(suffixes: Set<Suffix>): BlockStatementNode? {
        return parseBlock(suffixes)?.let(::BlockStatementNode)
    }

    private fun parseBlock(suffixes: Set<Suffix>): BlockNode? {
        if (tokenType != TokenType.OpenCurly)
            return null

        consume()
        val statements = parseStatementList(suffixes)
        consume(TokenType.CloseCurly)

        return statements?.let(::BlockNode)
    }

    private fun parseVariableStatement(suffixes: Set<Suffix>): VariableStatementNode? {
        if (tokenType != TokenType.Var)
            return null
        consume()

        val list = parseVariableDeclarationList(suffixes + Suffix.In) ?: run {
            expected("identifier")
            consume()
            return null
        }

        automaticSemicolonInsertion()

        return VariableStatementNode(list)
    }

    private fun parseVariableDeclarationList(suffixes: Set<Suffix>): VariableDeclarationList? {
        val declarations = mutableListOf<VariableDeclarationNode>()

        val declaration = parseVariableDeclaration(suffixes) ?: return null
        declarations.add(declaration)

        while (tokenType == TokenType.Comma) {
            saveState()
            consume()
            val declaration2 = parseVariableDeclaration(suffixes)
            if (declaration2 == null) {
                loadState()
                break
            }
            discardState()
            declarations.add(declaration)
        }

        return VariableDeclarationList(declarations)
    }

    private fun parseVariableDeclaration(suffixes: Set<Suffix>): VariableDeclarationNode? {
        // TODO: Attempt the BindingPattern branch
        val identifier = parseBindingIdentifier(suffixes - Suffix.In) ?: return null
        val initializer = parseInitializer(suffixes)
        return VariableDeclarationNode(identifier, initializer)
    }

    private fun parseInitializer(suffixes: Set<Suffix>): InitializerNode? {
        if (tokenType != TokenType.Equals)
            return null

        saveState()
        consume()
        val expr = parseAssignmentExpression(suffixes) ?: run {
            loadState()
            return null
        }
        return InitializerNode(expr)
    }

    private fun parseEmptyStatement(): StatementNode? {
        return if (tokenType == TokenType.Semicolon) {
            consume()
            EmptyStatementNode
        } else null
    }

    private fun parseExpressionStatement(suffixes: Set<Suffix>): StatementNode? {
        when (tokenType) {
            TokenType.OpenCurly,
            TokenType.Function,
            TokenType.Class -> return null
            TokenType.Async -> {
                if (peek(1).type == TokenType.Function && '\n' !in peek(1).trivia)
                    return null
            }
            TokenType.Let -> {
                if (peek(1).type == TokenType.OpenBracket)
                    return null
            }
            else -> {}
        }

        return parseExpression(suffixes + Suffix.In)?.let(::ExpressionStatementNode).also {
            automaticSemicolonInsertion()
        }
    }

    private fun parseExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val expressions = mutableListOf<ExpressionNode>()

        val expr1 = parseAssignmentExpression(suffixes) ?: return null
        expressions.add(expr1)

        fun returnVal() = if (expressions.size == 1) expressions[0] else CommaExpressionNode(expressions)

        while (tokenType == TokenType.Comma) {
            saveState()
            consume()
            val expr2 = parseAssignmentExpression(suffixes) ?: run {
                loadState()
                return returnVal()
            }
            expressions.add(expr2)
        }

        return returnVal()
    }

    private fun parseIfStatement(suffixes: Set<Suffix>): StatementNode? {
        if (tokenType != TokenType.If)
            return null

        consume()
        consume(TokenType.OpenParen)

        val condition = parseExpression(suffixes + Suffix.In) ?: run {
            expected("expression")
            consume()
            return null
        }

        consume(TokenType.CloseParen)
        val trueBlock = parseStatement(suffixes) ?: run {
            expected("statement")
            consume()
            return null
        }

        if (tokenType != TokenType.Else)
            return IfStatementNode(condition, trueBlock, null)

        consume()
        val falseBlock = parseStatement(suffixes) ?: run {
            expected("statement")
            consume()
            return null
        }

        return IfStatementNode(condition, trueBlock, falseBlock)
    }

    private fun parseBreakableStatement(suffixes: Set<Suffix>): StatementNode? {
        return parseIterationStatement(suffixes) ?: parseSwitchStatement(suffixes)
    }

    // @ECMA why is this nonterminal so big :(
    private fun parseIterationStatement(suffixes: Set<Suffix>): StatementNode? {
        when (tokenType) {
            TokenType.Do -> {
                consume()

                val statement = parseStatement(suffixes) ?: run {
                    expected("statement")
                    consume()
                    return null
                }

                consume(TokenType.While)
                consume(TokenType.OpenParen)
                val condition = parseExpression(suffixes + Suffix.In) ?: run {
                    expected("expression")
                    consume()
                    return null
                }

                consume(TokenType.CloseParen)
                automaticSemicolonInsertion()

                return DoWhileNode(condition, statement)
            }
            TokenType.While -> {
                consume()
                consume(TokenType.OpenParen)

                val expression = parseExpression(suffixes + Suffix.In) ?: run {
                    expected("expression")
                    consume()
                    return null
                }

                consume(TokenType.CloseParen)
                val statement = parseStatement(suffixes) ?: run {
                    expected("statement")
                    consume()
                    return null
                }

                return WhileNode(expression, statement)
            }
            TokenType.For -> {
                consume()

                if (tokenType == TokenType.Await) {
                    consume()
                    consume(TokenType.OpenParen)

                    return parseForStatementType10(suffixes) ?:
                        parseForStatementType11(suffixes) ?:
                        parseForStatementType12(suffixes) ?: run {
                            expected("initializer statement")
                            consume()
                            null
                        }
                }

                consume(TokenType.OpenParen)

                return parseForStatementType1(suffixes) ?:
                    parseForStatementType2(suffixes) ?:
                    parseForStatementType3(suffixes) ?:
                    parseForStatementType4(suffixes) ?:
                    parseForStatementType5(suffixes) ?:
                    parseForStatementType6(suffixes) ?:
                    parseForStatementType7(suffixes) ?:
                    parseForStatementType8(suffixes) ?:
                    parseForStatementType9(suffixes) ?: run {
                        expected("initializer statement")
                        consume()
                        null
                    }
            }
            else -> return null
        }
    }

    private fun parseForStatementType1(suffixes: Set<Suffix>): StatementNode? {
        saveState()
        if (tokenType == TokenType.Let && peek(1).type == TokenType.OpenBracket)
            return null
        val initializer = parseExpression(suffixes - Suffix.In)
        if (tokenType != TokenType.Semicolon) {
            loadState()
            return null
        }
        discardState()
        consume()
        val condition = parseExpression(suffixes + Suffix.In)
        consume(TokenType.Semicolon)
        consume()
        val incrementer = parseExpression(suffixes + Suffix.In)
        consume(TokenType.CloseParen)
        val body = parseStatement(suffixes) ?: run {
            expected("statement")
            consume()
            return null
        }

        return ForNode(initializer?.let(::ExpressionStatementNode), condition, incrementer, body)
    }

    private fun parseForStatementType2(suffixes: Set<Suffix>): StatementNode? {
        if (tokenType != TokenType.Var)
            return null
        saveState()
        consume()
        val declarations = parseVariableDeclarationList(suffixes - Suffix.In) ?: run {
            loadState()
            return null
        }
        if (tokenType != TokenType.Semicolon) {
            loadState()
            return null
        }
        consume()
        val condition = parseExpression(suffixes + Suffix.In)
        consume(TokenType.Semicolon)
        val incrementer = parseExpression(suffixes + Suffix.In)
        consume(TokenType.CloseParen)
        val body = parseStatement(suffixes) ?: run {
            expected("statement")
            consume()
            return null
        }

        return ForNode(VariableStatementNode(declarations), condition, incrementer, body)
    }

    private fun parseForStatementType3(suffixes: Set<Suffix>): StatementNode? {
        val declaration = parseLexicalDeclaration(suffixes - Suffix.In) ?: return null
        val condition = parseExpression(suffixes + Suffix.In)
        consume(TokenType.Semicolon)
        val incrementer = parseExpression(suffixes + Suffix.In)
        consume(TokenType.CloseParen)
        val body = parseStatement(suffixes) ?: run {
            expected("statement")
            consume()
            return null
        }

        return ForNode(declaration, condition, incrementer, body)
    }

    private fun parseForStatementType4(suffixes: Set<Suffix>): StatementNode? {
        if (tokenType == TokenType.Let && peek(1).type == TokenType.OpenBracket)
            return null
        saveState()
        val initializer = parseLeftHandSideExpression(suffixes - Suffix.In) ?: return null
        if (tokenType != TokenType.In) {
            loadState()
            return null
        }
        discardState()
        consume()
        val expression = parseExpression(suffixes + Suffix.In) ?: run {
            expected("expression")
            consume()
            return null
        }
        consume(TokenType.CloseParen)
        val body = parseStatement(suffixes) ?: run {
            expected("statement")
            consume()
            return null
        }
        return ForInNode(ExpressionStatementNode(initializer), expression)
    }

    private fun parseForStatementType5(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType6(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType7(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType8(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType9(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType10(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType11(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType12(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseLexicalDeclaration(suffixes: Set<Suffix>): LexicalDeclarationNode? {
        if (!matchAny(TokenType.Let, TokenType.Const))
            return null

        saveState()

        val isConst = when (consume().type) {
            TokenType.Let -> false
            TokenType.Const -> true
            else -> unreachable()
        }

        val list = parseBindingList(suffixes + Suffix.In) ?: run {
            expected("identifier")
            consume()
            return null
        }

        automaticSemicolonInsertion()
        discardState()

        return LexicalDeclarationNode(isConst, list)
    }

    private fun parseBindingList(suffixes: Set<Suffix>): BindingListNode? {
        val declarations = mutableListOf<LexicalBindingNode>()

        val declaration = parseLexicalBinding(suffixes) ?: return null
        declarations.add(declaration)

        while (tokenType == TokenType.Comma) {
            saveState()
            consume()
            val declaration2 = parseLexicalBinding(suffixes)
            if (declaration2 == null) {
                loadState()
                break
            }
            discardState()
            declarations.add(declaration)
        }

        return BindingListNode(declarations)
    }

    private fun parseLexicalBinding(suffixes: Set<Suffix>): LexicalBindingNode? {
        // TODO: Attempt the BindingPattern branch
        val identifier = parseBindingIdentifier(suffixes - Suffix.In) ?: return null
        val initializer = parseInitializer(suffixes)
        return LexicalBindingNode(identifier, initializer)
    }

    private fun parseSwitchStatement(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseContinueStatement(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseBreakStatement(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseReturnStatement(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseWithStatement(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseLabelledStatement(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseThrowStatement(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseTryStatement(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseDebuggerStatement(): StatementNode? {
        if (tokenType != TokenType.Debugger)
            return null
        consume()
        automaticSemicolonInsertion()
        return DebuggerNode
    }

    private fun parseDeclaration(suffixes: Set<Suffix>): StatementNode? {
        // TODO
        return null
    }

    private fun parseIdentifierReference(suffixes: Set<Suffix>): IdentifierReferenceNode? {
        parseIdentifier()?.let {
            return IdentifierReferenceNode(it.identifierName)
        }

        return when (tokenType) {
            TokenType.Await -> if (Suffix.Await in suffixes) {
                null
            } else {
                consume()
                IdentifierReferenceNode("yield")
            }
            TokenType.Yield -> if (Suffix.Yield in suffixes) {
                null
            } else {
                consume()
                IdentifierReferenceNode("await")
            }
            else -> null
        }
    }

    private fun parseBindingIdentifier(suffixes: Set<Suffix>): BindingIdentifierNode? {
        parseIdentifier()?.let {
            return BindingIdentifierNode(it.identifierName)
        }

        return when (tokenType) {
            TokenType.Await -> {
                consume()
                BindingIdentifierNode("await")
            }
            TokenType.Yield -> {
                consume()
                BindingIdentifierNode("yield")
            }
            else -> null
        }
    }

    private fun parseLabelIdentifier(suffixes: Set<Suffix>): LabelIdentifierNode? {
        return parseIdentifierReference(suffixes)?.let { LabelIdentifierNode(it.identifierName) }
    }

    private fun parseIdentifier(): IdentifierNode? {
        return when {
            tokenType != TokenType.Identifier -> null
            isReserved(token.value) -> null
            else -> IdentifierNode(consume().value)
        }
    }

    private fun parseIdentifierName(): IdentifierNode? {
        return if (tokenType != TokenType.Identifier) {
            null
        } else IdentifierNode(consume().value)
    }

    private fun parseYieldExpression(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseArrowFunction(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseAsyncArrowFunction(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseLeftHandSideExpression(suffixes: Set<Suffix>): ExpressionNode? {
        return parseCallExpression(suffixes) ?:
            parseNewExpression(suffixes) ?:
            parseOptionalExpression(suffixes)
    }

    private fun parseNewExpression(suffixes: Set<Suffix>): ExpressionNode? {
        if (tokenType != TokenType.New)
            return parseMemberExpression(suffixes)

        consume()
        val expr = parseNewExpression(suffixes) ?: run {
            expected("expression")
            consume()
            return null
        }
        return NewExpressionNode(expr)
    }

    private fun parseCallExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val initial = parseMemberExpression(suffixes) ?:
            parseSuperCall(suffixes) ?:
            parseImportCall(suffixes) ?:
            return null

        var callExpression: ExpressionNode? = null

        while (true) {
            val args = parseArguments(suffixes) ?: break
            callExpression = if (callExpression == null) {
                CallExpressionNode(initial, args)
            } else {
                CallExpressionNode(callExpression, args)
            }
        }

        return callExpression ?: initial
    }

    private fun parseSuperCall(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseImportCall(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseOptionalExpression(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseMemberExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val primaryExpression = parsePrimaryExpression(suffixes) ?: run {
            parseSuperProperty(suffixes)?.also { return it }
            parseMetaProperty()?.also { return it }
            if (tokenType == TokenType.New) {
                consume()
                val expr = parseMemberExpression(suffixes) ?: run {
                    discardState()
                    expected("expression")
                    return null
                }
                val args = parseArguments(suffixes) ?: run {
                    discardState()
                    expected("parenthesized arguments")
                    return null
                }
                return MemberExpressionNode(expr, args, MemberExpressionNode.Type.New)
            }
            return null
        }
        var memberExpression: MemberExpressionNode? = null

        while (true) {
            if (tokenType == TokenType.OpenBracket) {
                consume()
                val expression = parseExpression(suffixes + Suffix.In) ?: run {
                    expected("expression")
                    consume()
                    return null
                }
                consume(TokenType.CloseBracket)
                memberExpression = if (memberExpression == null) {
                    MemberExpressionNode(primaryExpression, expression, MemberExpressionNode.Type.Computed)
                } else {
                    MemberExpressionNode(memberExpression, expression, MemberExpressionNode.Type.Computed)
                }
            } else if (tokenType == TokenType.Period) {
                consume()
                val identifier = parseIdentifierName() ?: run {
                    expected("identifier")
                    consume()
                    return null
                }
                memberExpression = if (memberExpression == null) {
                    MemberExpressionNode(primaryExpression, identifier, MemberExpressionNode.Type.NonComputed)
                } else {
                    MemberExpressionNode(memberExpression, identifier, MemberExpressionNode.Type.NonComputed)
                }
            } else if (tokenType == TokenType.TemplateLiteralStart) {
                TODO()
            } else break
        }

        return memberExpression ?: primaryExpression
    }

    private fun parseMetaProperty(): MetaPropertyNode? {
        return when (tokenType) {
            TokenType.New -> {
                // TODO: Should we save? Or should we error if we don't find a period
                saveState()
                consume()
                if (tokenType != TokenType.Period) {
                    loadState()
                    return null
                }
                discardState()
                consume()
                if (tokenType != TokenType.Identifier && token.value != "target") {
                    expected("new.target property reference")
                    return null
                }
                consume()
                MetaPropertyNode(NewTargetNode)
            }
            TokenType.Import -> {
                // TODO: Should we save? Or should we error if we don't find a period
                saveState()
                if (tokenType != TokenType.Period) {
                    loadState()
                    return null
                }
                discardState()
                consume()
                if (tokenType != TokenType.Identifier && token.value != "meta") {
                    expected("import.meta property reference")
                    return null
                }
                consume()
                MetaPropertyNode(ImportMetaNode)
            }
            else -> null
        }
    }

    private fun parseSuperProperty(suffixes: Set<Suffix>): ExpressionNode? {
        if (tokenType != TokenType.Super)
            return null
        consume()
        return when (tokenType) {
            TokenType.OpenBracket -> {
                consume()
                val expression = parseExpression(suffixes + Suffix.In) ?: run {
                    expected("expression")
                    consume()
                    return null
                }
                consume(TokenType.CloseBracket)
                return SuperPropertyNode(expression, true)
            }
            TokenType.Period -> {
                consume()
                val identifier = parseIdentifierName() ?: run {
                    expected("identifier")
                    consume()
                    return null
                }
                return SuperPropertyNode(identifier, false)
            }
            else -> {
                expected("super property access")
                null
            }
        }
    }

    enum class CCEAARHContext {
        CallExpression,
    }

    private fun parseCoverCallExpressionAndAsyncArrowHead(suffixes: Set<Suffix>, context: CCEAARHContext): ExpressionNode? {
        return when (context) {
            CCEAARHContext.CallExpression -> {
                val memberExpr = parseMemberExpression(suffixes) ?: return null
                saveState()
                val args = parseArguments(suffixes) ?: run {
                    loadState()
                    return null
                }
                return CallExpressionNode(memberExpr, args)
            }
        }
    }

    private fun parseArguments(suffixes: Set<Suffix>): ArgumentsNode? {
        if (tokenType != TokenType.OpenParen)
            return null
        consume()

        val argumentsList = parseArgumentsList(suffixes)
        if (argumentsList != null && tokenType == TokenType.Comma) {
            consume()
        }
        consume(TokenType.CloseParen)
        return argumentsList?.let(::ArgumentsNode)
    }

    private fun parseArgumentsList(suffixes: Set<Suffix>): ArgumentsListNode? {
        val arguments = mutableListOf<ArgumentListEntry>()
        var first = true

        do {
            saveState()

            if (!first) {
                if (tokenType != TokenType.Comma)
                    break
                consume()
            }

            if (tokenType == TokenType.TripleDot) {
                consume()
                val expr = parseAssignmentExpression(suffixes + Suffix.In) ?: run {
                    expected("expression")
                    consume()
                    return null
                }
                arguments.add(ArgumentListEntry(expr, true))
            } else {
                val expr = parseAssignmentExpression(suffixes + Suffix.In) ?: run {
                    loadState()
                    return null
                }
                arguments.add(ArgumentListEntry(expr, false))
            }

            first = false
        } while (true)

        return ArgumentsListNode(arguments)
    }

    private fun parsePrimaryExpression(suffixes: Set<Suffix>): PrimaryExpressionNode? {
        if (tokenType == TokenType.This) {
            consume()
            return PrimaryExpressionNode(ThisNode)
        }

        val expression = parseIdentifierReference(suffixes) ?:
            parseLiteral() ?:
            parseArrayLiteral(suffixes) ?:
            parseObjectLiteral(suffixes) ?:
            parseFunctionExpression(suffixes) ?:
            parseClassExpression(suffixes) ?:
            parseGeneratorExpression(suffixes) ?:
            parseAsyncFunctionExpression(suffixes) ?:
            parseAsyncGeneratorExpression(suffixes) ?:
            parseRegularExpressionLiteral(suffixes) ?:
            parseTemplateLiteral(suffixes) ?:
            parseCoverParenthesizedExpressionAndArrowParameterList(suffixes, CPEAAPLContext.PrimaryExpression) ?:
            return null

        return PrimaryExpressionNode(expression)
    }

    private fun parseLiteral(): ExpressionNode? {
        return parseNullLiteral() ?:
            parseBooleanLiteral() ?:
            parseNumericLiteral() ?:
            parseStringLiteral()
    }

    private fun parseNullLiteral(): ExpressionNode? {
        return if (tokenType == TokenType.NullLiteral) NullNode else null
    }

    private fun parseBooleanLiteral(): ExpressionNode? {
        return when (tokenType) {
            TokenType.True -> TrueNode
            TokenType.False -> FalseNode
            else -> null
        }?.also {
            consume()
        }
    }

    private fun parseNumericLiteral(): ExpressionNode? {
        return if (tokenType == TokenType.NumericLiteral) {
            NumericLiteralNode(consume().asDouble())
        } else null
    }

    private fun parseStringLiteral(): ExpressionNode? {
        return if (tokenType == TokenType.StringLiteral) {
            StringLiteralNode(consume().asString())
        } else null
    }

    private fun parseArrayLiteral(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseObjectLiteral(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseFunctionExpression(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseClassExpression(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseGeneratorExpression(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseAsyncFunctionExpression(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseAsyncGeneratorExpression(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseRegularExpressionLiteral(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseTemplateLiteral(suffixes: Set<Suffix>): ExpressionNode? {
        // TODO
        return null
    }

    enum class CPEAAPLContext {
        PrimaryExpression,
    }

    private fun parseCoverParenthesizedExpressionAndArrowParameterList(
        suffixes: Set<Suffix>,
        context: CPEAAPLContext
    ): CPEAAPL? {
        // TODO
        return null
    }

    private fun parseAssignmentExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val expr: ExpressionNode? = parseConditionalExpression(suffixes) ?:
            if (Suffix.Yield in suffixes) {
                parseYieldExpression(suffixes + Suffix.Yield)
            } else null ?:
            parseArrowFunction(suffixes) ?:
            parseAsyncArrowFunction(suffixes)

        if (expr != null)
            return expr

        saveState()
        val lhs = parseLeftHandSideExpression(suffixes - Suffix.In)
        if (lhs == null) {
            discardState()
            return null
        }

        val op = when (tokenType) {
            TokenType.Equals -> AssignmentExpressionNode.Operator.Equals
            TokenType.PlusEquals -> AssignmentExpressionNode.Operator.Plus
            TokenType.MinusEquals -> AssignmentExpressionNode.Operator.Minus
            TokenType.AsteriskEquals -> AssignmentExpressionNode.Operator.Multiply
            TokenType.SlashEquals -> AssignmentExpressionNode.Operator.Divide
            TokenType.PercentEquals -> AssignmentExpressionNode.Operator.Mod
            TokenType.DoubleAsteriskEquals -> AssignmentExpressionNode.Operator.Power
            TokenType.ShiftLeftEquals -> AssignmentExpressionNode.Operator.ShiftLeft
            TokenType.ShiftRightEquals -> AssignmentExpressionNode.Operator.ShiftRight
            TokenType.UnsignedShiftRightEquals -> AssignmentExpressionNode.Operator.UnsignedShiftRight
            TokenType.AmpersandEquals -> AssignmentExpressionNode.Operator.BitwiseAnd
            TokenType.PipeEquals -> AssignmentExpressionNode.Operator.BitwiseOr
            TokenType.CaretEquals -> AssignmentExpressionNode.Operator.BitwiseXor
            TokenType.DoubleAmpersandEquals -> AssignmentExpressionNode.Operator.And
            TokenType.DoublePipeEquals -> AssignmentExpressionNode.Operator.Or
            TokenType.DoubleQuestionEquals -> AssignmentExpressionNode.Operator.Nullish
            else -> {
                loadState()
                return null
            }
        }

        val rhs = parseAssignmentExpression(suffixes)
        return if (rhs == null) {
            expected("expression")
            consume()
            discardState()
            null
        } else AssignmentExpressionNode(lhs, rhs, op)
    }

    private fun parseConditionalExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val lhs = parseShortCircuitExpression(suffixes) ?: return null
        if (tokenType != TokenType.Question)
            return lhs

        consume()
        val middle = parseAssignmentExpression(suffixes + Suffix.In)
        if (middle == null) {
            consume()
            expected("expression")
            return null
        }

        consume(TokenType.Colon)
        val rhs = parseAssignmentExpression(suffixes + Suffix.In)
        return if (rhs == null) {
            consume()
            expected("expression")
            null
        } else ConditionalExpressionNode(lhs, middle, rhs)
    }

    private fun parseShortCircuitExpression(suffixes: Set<Suffix>): ExpressionNode? {
        return parseLogicalORExpression(suffixes) ?: parseCoalesceExpresion(suffixes)
    }

    private fun parseCoalesceExpresion(suffixes: Set<Suffix>): ExpressionNode? {
        saveState()
        val head = parseBitwiseORExpression(suffixes) ?: return null
        if (tokenType != TokenType.DoubleQuestion) {
            loadState()
            return null
        }
        consume()

        val expr = parseCoalesceExpresion(suffixes)
        return if (expr == null) {
            expected("expression")
            consume()
            null
        } else CoalesceExpressionNode(head, expr)
    }

    private fun parseLogicalORExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val lhs = parseLogicalANDExpression(suffixes) ?: return null
        if (tokenType != TokenType.DoublePipe)
            return lhs
        consume()

        val rhs = parseLogicalORExpression(suffixes)
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else LogicalORExpressionNode(lhs, rhs)
    }

    private fun parseLogicalANDExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val lhs = parseBitwiseORExpression(suffixes) ?: return null
        if (tokenType != TokenType.DoubleAmpersand)
            return lhs
        consume()

        val rhs = parseLogicalANDExpression(suffixes)
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else LogicalORExpressionNode(lhs, rhs)
    }

    private fun parseBitwiseORExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val lhs = parseBitwiseXORExpression(suffixes) ?: return null
        if (tokenType != TokenType.Pipe)
            return lhs
        consume()

        val rhs = parseBitwiseORExpression(suffixes)
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else LogicalORExpressionNode(lhs, rhs)
    }

    private fun parseBitwiseXORExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val lhs = parseBitwiseANDExpression(suffixes) ?: return null
        if (tokenType != TokenType.Caret)
            return lhs
        consume()

        val rhs = parseBitwiseXORExpression(suffixes)
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else LogicalORExpressionNode(lhs, rhs)
    }

    private fun parseBitwiseANDExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val lhs = parseEqualityExpression(suffixes) ?: return null
        if (tokenType != TokenType.Ampersand)
            return lhs
        consume()

        val rhs = parseBitwiseANDExpression(suffixes)
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else LogicalORExpressionNode(lhs, rhs)
    }

    private fun parseEqualityExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val lhs = parseRelationalExpression(suffixes) ?: return null
        val op = when (tokenType) {
            TokenType.DoubleEquals -> EqualityExpressionNode.Operator.NonstrictEquality
            TokenType.ExclamationEquals -> EqualityExpressionNode.Operator.NonstrictInequality
            TokenType.TripleEquals -> EqualityExpressionNode.Operator.StrictEquality
            TokenType.ExclamationDoubleEquals -> EqualityExpressionNode.Operator.StrictInequality
            else -> return lhs
        }
        consume()


        val rhs = parseEqualityExpression(suffixes)
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else EqualityExpressionNode(lhs, rhs, op)
    }

    private fun parseRelationalExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val lhs = parseShiftExpression(suffixes - Suffix.In) ?: return null
        val op = when (tokenType) {
            TokenType.LessThan -> RelationalExpressionNode.Operator.LessThan
            TokenType.GreaterThan -> RelationalExpressionNode.Operator.GreaterThan
            TokenType.LessThanEquals -> RelationalExpressionNode.Operator.LessThanEquals
            TokenType.GreaterThanEquals -> RelationalExpressionNode.Operator.GreaterThanEquals
            TokenType.Instanceof -> RelationalExpressionNode.Operator.Instanceof
            TokenType.In -> if (Suffix.In in suffixes) RelationalExpressionNode.Operator.In else return lhs
            else -> return lhs
        }
        consume()


        val nextSuffixes = if (op == RelationalExpressionNode.Operator.In) suffixes + Suffix.In else suffixes
        val rhs = parseRelationalExpression(nextSuffixes)
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else RelationalExpressionNode(lhs, rhs, op)
    }

    private fun parseShiftExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val lhs = parseAdditiveExpression(suffixes) ?: return null
        val op = when (tokenType) {
            TokenType.ShiftLeft -> ShiftExpressionNode.Operator.ShiftLeft
            TokenType.ShiftRight -> ShiftExpressionNode.Operator.ShiftRight
            TokenType.UnsignedShiftRight -> ShiftExpressionNode.Operator.UnsignedShiftRight
            else -> return lhs
        }
        consume()

        val rhs = parseShiftExpression(suffixes)
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else ShiftExpressionNode(lhs, rhs, op)
    }

    private fun parseAdditiveExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val lhs = parseMultiplicativeExpression(suffixes) ?: return null
        val isSubtraction = when (tokenType) {
            TokenType.Plus -> false
            TokenType.Minus -> true
            else -> return lhs
        }
        consume()

        val rhs = parseAdditiveExpression(suffixes)
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else AdditiveExpressionNode(lhs, rhs, isSubtraction)
    }

    private fun parseMultiplicativeExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val lhs = parseExponentiationExpression(suffixes) ?: return null
        val op = when (tokenType) {
            TokenType.Asterisk -> MultiplicativeExpressionNode.Operator.Multiply
            TokenType.Slash -> MultiplicativeExpressionNode.Operator.Divide
            TokenType.Percent -> MultiplicativeExpressionNode.Operator.Modulo
            else -> return lhs
        }
        consume()

        val rhs = parseMultiplicativeExpression(suffixes)
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else MultiplicativeExpressionNode(lhs, rhs, op)
    }

    private fun parseExponentiationExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val lhs = parseUnaryExpression(suffixes) ?: return null
        if (tokenType != TokenType.DoubleAsterisk)
            return lhs
        consume()

        val rhs = parseExponentiationExpression(suffixes)
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else ExponentiationExpressionNode(lhs, rhs)
    }

    private fun parseUnaryExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val op = when (tokenType) {
            TokenType.Delete -> UnaryExpressionNode.Operator.Delete
            TokenType.Void -> UnaryExpressionNode.Operator.Void
            TokenType.Typeof -> UnaryExpressionNode.Operator.Typeof
            TokenType.Plus -> UnaryExpressionNode.Operator.Plus
            TokenType.Minus -> UnaryExpressionNode.Operator.Minus
            TokenType.Tilde -> UnaryExpressionNode.Operator.BitwiseNot
            TokenType.Exclamation -> UnaryExpressionNode.Operator.Not
            else -> return parseUpdateExpression(suffixes) ?: run {
                if (Suffix.Await in suffixes) {
                    parseAwaitExpression(suffixes + Suffix.Await)
                } else null
            }
        }
        consume()

        val expr = parseUnaryExpression(suffixes)
        return if (expr == null) {
            expected("expression")
            consume()
            null
        } else UnaryExpressionNode(expr, op)
    }

    private fun parseUpdateExpression(suffixes: Set<Suffix>): ExpressionNode? {
        val expr: ExpressionNode
        val isIncrement: Boolean
        val isPostfix: Boolean

        if (matchAny(TokenType.PlusPlus, TokenType.MinusMinus)) {
            isIncrement = consume().type == TokenType.PlusPlus
            isPostfix = false
            expr = parseUnaryExpression(suffixes).let {
                if (it == null) {
                    expected("expression")
                    consume()
                    return null
                } else it
            }
        } else {
            expr = parseLeftHandSideExpression(suffixes) ?: return null
            if (!matchAny(TokenType.PlusPlus, TokenType.MinusMinus))
                return expr
            if ('\n' in token.trivia)
                return expr
            isPostfix = true
            isIncrement = consume().type == TokenType.PlusPlus
        }

        return UpdateExpressionNode(expr, isIncrement, isPostfix)
    }

    private fun parseAwaitExpression(suffixes: Set<Suffix>): ExpressionNode? {
        if (tokenType != TokenType.Await)
            return null
        consume()

        val expr = parseUnaryExpression(suffixes + Suffix.Await)
        return if (expr == null) {
            expected("expression")
            consume()
            return null
        } else AwaitExpressionNode(expr)
    }

    private fun saveState() {
        stateStack.add(state.copy())
    }

    private fun loadState() {
        state = stateStack.removeLast()
    }

    private fun discardState() {
        stateStack.removeLast()
    }

    private fun consume() = token.also { cursor++ }

    private fun automaticSemicolonInsertion(): Token {
        if (tokenType == TokenType.Semicolon)
            return consume()
        if ('\n' in token.trivia)
            return token
        if (matchAny(TokenType.CloseCurly, TokenType.Eof))
            return token

        expected("semicolon");
        return token
    }

    private fun consume(type: TokenType): Token {
        if (type != tokenType)
            expected(type.meta ?: type.name)
        return consume()
    }

    private fun syntaxError(message: String = "TODO") {
        syntaxErrors.add(SyntaxError(
            token.valueStart.lineNumber + 1,
            token.valueStart.columnNumber + 1,
            message
        ))
    }

    private fun has(n: Int) = cursor + n < tokens.size

    private fun peek(n: Int) = tokens[cursor + n]

    private fun matchSequence(vararg types: TokenType) = has(types.size) && types.mapIndexed { i, t -> peek(i).type == t }.all()

    private fun matchAny(vararg types: TokenType) = !isDone && tokenType in types

    private fun expected(expected: String, found: String = token.value) {
        syntaxError("Expected: $expected, found: $found")
    }

    private fun unexpected(unexpected: String) {
        syntaxError("Unexpected $unexpected")
    }

    private data class State(var cursor: Int)

    private data class SyntaxError(
        val lineNumber: Int,
        val columnNumber: Int,
        val message: String
    )

    enum class GoalSymbol {
        Module,
        Script,
    }

    enum class Suffix {
        Yield,
        Await,
        In,
        Return,
        Tagged,
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

        fun isReserved(identifier: String) = identifier in reservedWords
    }
}
