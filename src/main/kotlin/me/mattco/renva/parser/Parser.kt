package me.mattco.renva.parser

import me.mattco.renva.ast.*
import me.mattco.renva.ast.expressions.*
import me.mattco.renva.ast.literals.NullNode
import me.mattco.renva.ast.literals.NumericLiteralNode
import me.mattco.renva.ast.literals.StringLiteralNode
import me.mattco.renva.ast.literals.ThisNode
import me.mattco.renva.ast.statements.*
import me.mattco.renva.lexer.Lexer
import me.mattco.renva.lexer.SourceLocation
import me.mattco.renva.lexer.Token
import me.mattco.renva.lexer.TokenType
import me.mattco.renva.utils.all
import me.mattco.renva.utils.unreachable

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
        val statementList = parseStatementList(Suffixes()) ?: StatementListNode(emptyList())
        if (!isDone)
            unexpected("token ${token.value}")
        return ScriptNode(statementList)
    }

    fun parseModule() {
        goalSymbol = GoalSymbol.Module
        TODO()
    }

    private fun parseScriptBody(suffixes: Suffixes): StatementListNode? {
        if (!suffixes.run { hasYield || hasAwait || hasReturn })
            return parseStatementList(suffixes)
        return null
    }

    private fun parseStatementList(suffixes: Suffixes): StatementListNode? {
        val statements = mutableListOf<StatementListItemNode>()

        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)
        var statement = parseStatementListItem(newSuffixes) ?: return null
        statements.add(statement)

        while (true) {
            statement = parseStatementListItem(newSuffixes) ?: break
            statements.add(statement)
        }

        return StatementListNode(statements)
    }

    private fun parseStatementListItem(suffixes: Suffixes): StatementListItemNode? {
        return parseStatement(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)) ?:
            parseDeclaration(suffixes.filter(Sfx.Yield, Sfx.Await))
    }

    private fun parseStatement(suffixes: Suffixes): StatementNode? {
        val tripleSuffix = suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)
        val doubleSuffix = suffixes.filter(Sfx.Yield, Sfx.Await)

        return parseBlockStatement(tripleSuffix) ?:
                parseVariableStatement(doubleSuffix) ?:
                parseEmptyStatement() ?:
                parseExpressionStatement(doubleSuffix) ?:
                parseIfStatement(tripleSuffix) ?:
                parseBreakableStatement(tripleSuffix) ?:
                parseContinueStatement(doubleSuffix) ?:
                parseBreakStatement(doubleSuffix) ?:
                if (suffixes.hasReturn) parseReturnStatement(tripleSuffix) else null ?:
                parseWithStatement(tripleSuffix) ?:
                parseLabelledStatement(tripleSuffix) ?:
                parseThrowStatement(doubleSuffix) ?:
                parseTryStatement(tripleSuffix) ?:
                parseDebuggerStatement()
    }

    private fun parseBlockStatement(suffixes: Suffixes): BlockStatementNode? {
        return parseBlock(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return))?.let(::BlockStatementNode)
    }

    private fun parseBlock(suffixes: Suffixes): BlockNode? {
        if (tokenType != TokenType.OpenCurly)
            return null

        consume()
        val statements = parseStatementList(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return))
        consume(TokenType.CloseCurly)

        return BlockNode(statements)
    }

    private fun parseVariableStatement(suffixes: Suffixes): VariableStatementNode? {
        if (tokenType != TokenType.Var)
            return null
        consume()

        val list = parseVariableDeclarationList(suffixes.filter(Sfx.Yield, Sfx.Await).withIn) ?: run {
            expected("identifier")
            consume()
            return null
        }

        automaticSemicolonInsertion()

        return VariableStatementNode(list)
    }

    private fun parseVariableDeclarationList(suffixes: Suffixes): VariableDeclarationList? {
        val declarations = mutableListOf<VariableDeclarationNode>()
        val newSuffixes = suffixes.filter(Sfx.In, Sfx.Yield, Sfx.Await)

        val declaration = parseVariableDeclaration(newSuffixes) ?: return null
        declarations.add(declaration)

        while (tokenType == TokenType.Comma) {
            saveState()
            consume()
            val declaration2 = parseVariableDeclaration(newSuffixes)
            if (declaration2 == null) {
                loadState()
                break
            }
            discardState()
            declarations.add(declaration)
        }

        return VariableDeclarationList(declarations)
    }

    private fun parseVariableDeclaration(suffixes: Suffixes): VariableDeclarationNode? {
        // TODO: Attempt the BindingPattern branch
        val identifier = parseBindingIdentifier() ?: return null
        val initializer = parseInitializer(suffixes.filter(Sfx.In, Sfx.Yield, Sfx.Await))
        return VariableDeclarationNode(identifier, initializer)
    }

    private fun parseInitializer(suffixes: Suffixes): InitializerNode? {
        if (tokenType != TokenType.Equals)
            return null

        saveState()
        consume()
        val expr = parseAssignmentExpression(suffixes.filter(Sfx.In, Sfx.Yield, Sfx.Await)) ?: run {
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

    private fun parseExpressionStatement(suffixes: Suffixes): StatementNode? {
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

        return parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn)?.let(::ExpressionStatementNode).also {
            automaticSemicolonInsertion()
        }
    }

    private fun parseExpression(suffixes: Suffixes): ExpressionNode? {
        val expressions = mutableListOf<ExpressionNode>()
        val newSuffixes = suffixes.filter(Sfx.In, Sfx.Yield, Sfx.Await)

        val expr1 = parseAssignmentExpression(newSuffixes) ?: return null
        expressions.add(expr1)

        fun returnVal() = if (expressions.size == 1) expressions[0] else CommaExpressionNode(expressions)

        while (tokenType == TokenType.Comma) {
            saveState()
            consume()
            val expr2 = parseAssignmentExpression(newSuffixes) ?: run {
                loadState()
                return returnVal()
            }
            expressions.add(expr2)
        }

        return returnVal()
    }

    private fun parseIfStatement(suffixes: Suffixes): StatementNode? {
        if (tokenType != TokenType.If)
            return null

        consume()
        consume(TokenType.OpenParen)

        val condition = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn) ?: run {
            expected("expression")
            consume()
            return null
        }

        consume(TokenType.CloseParen)
        val trueBlock = parseStatement(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)) ?: run {
            expected("statement")
            consume()
            return null
        }

        if (tokenType != TokenType.Else)
            return IfStatementNode(condition, trueBlock, null)

        consume()
        val falseBlock = parseStatement(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)) ?: run {
            expected("statement")
            consume()
            return null
        }

        return IfStatementNode(condition, trueBlock, falseBlock)
    }

    private fun parseBreakableStatement(suffixes: Suffixes): StatementNode? {
        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)
        return parseIterationStatement(newSuffixes) ?: parseSwitchStatement(newSuffixes)
    }

    // @ECMA why is this nonterminal so big :(
    private fun parseIterationStatement(suffixes: Suffixes): StatementNode? {
        val tripleSuffix = suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)
        val withIn = suffixes.filter(Sfx.Yield, Sfx.Await).withIn

        when (tokenType) {
            TokenType.Do -> {
                consume()

                val statement = parseStatement(tripleSuffix) ?: run {
                    expected("statement")
                    consume()
                    return null
                }

                consume(TokenType.While)
                consume(TokenType.OpenParen)
                val condition = parseExpression(withIn) ?: run {
                    expected("expression")
                    consume()
                    return null
                }

                consume(TokenType.CloseParen)
                automaticSemicolonInsertion()

                return DoWhileStatementNode(condition, statement)
            }
            TokenType.While -> {
                consume()
                consume(TokenType.OpenParen)

                val expression = parseExpression(withIn) ?: run {
                    expected("expression")
                    consume()
                    return null
                }

                consume(TokenType.CloseParen)
                val statement = parseStatement(tripleSuffix) ?: run {
                    expected("statement")
                    consume()
                    return null
                }

                return WhileStatementNode(expression, statement)
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

    private fun parseForStatementType1(suffixes: Suffixes): StatementNode? {
        saveState()
        if (tokenType == TokenType.Let && peek(1).type == TokenType.OpenBracket)
            return null
        val initializer = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await))
        if (tokenType != TokenType.Semicolon) {
            loadState()
            return null
        }
        discardState()
        consume()
        val condition = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn)
        consume(TokenType.Semicolon)
        consume()
        val incrementer = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn)
        consume(TokenType.CloseParen)
        val body = parseStatement(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)) ?: run {
            expected("statement")
            consume()
            return null
        }

        return ForNode(initializer?.let(::ExpressionStatementNode), condition, incrementer, body)
    }

    private fun parseForStatementType2(suffixes: Suffixes): StatementNode? {
        if (tokenType != TokenType.Var)
            return null
        saveState()
        consume()
        val declarations = parseVariableDeclarationList(suffixes.filter(Sfx.Yield, Sfx.Await)) ?: run {
            loadState()
            return null
        }
        if (tokenType != TokenType.Semicolon) {
            loadState()
            return null
        }
        consume()
        val condition = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await))
        consume(TokenType.Semicolon)
        val incrementer = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await))
        consume(TokenType.CloseParen)
        val body = parseStatement(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)) ?: run {
            expected("statement")
            consume()
            return null
        }

        return ForNode(VariableStatementNode(declarations), condition, incrementer, body)
    }

    private fun parseForStatementType3(suffixes: Suffixes): StatementNode? {
        val baseSuffix = suffixes.filter(Sfx.Yield, Sfx.Await)
        val declaration = parseLexicalDeclaration(baseSuffix) ?: return null
        val condition = parseExpression(baseSuffix.withIn)
        consume(TokenType.Semicolon)
        val incrementer = parseExpression(baseSuffix.withIn)
        consume(TokenType.CloseParen)
        val body = parseStatement(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)) ?: run {
            expected("statement")
            consume()
            return null
        }

        return ForNode(declaration, condition, incrementer, body)
    }

    private fun parseForStatementType4(suffixes: Suffixes): StatementNode? {
        if (tokenType == TokenType.Let && peek(1).type == TokenType.OpenBracket)
            return null
        saveState()
        val initializer = parseLeftHandSideExpression(suffixes.filter(Sfx.Yield, Sfx.Await)) ?: return null
        if (tokenType != TokenType.In) {
            loadState()
            return null
        }
        discardState()
        consume()
        val expression = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn) ?: run {
            expected("expression")
            consume()
            return null
        }
        consume(TokenType.CloseParen)
        val body = parseStatement(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)) ?: run {
            expected("statement")
            consume()
            return null
        }
        return ForInNode(ExpressionStatementNode(initializer), expression, body)
    }

    private fun parseForStatementType5(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType6(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType7(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType8(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType9(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType10(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType11(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseForStatementType12(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseBindingList(suffixes: Suffixes): BindingListNode? {
        val declarations = mutableListOf<LexicalBindingNode>()
        val newSuffixes = suffixes.filter(Sfx.In, Sfx.Yield, Sfx.Await)

        val declaration = parseLexicalBinding(newSuffixes) ?: return null
        declarations.add(declaration)

        while (tokenType == TokenType.Comma) {
            saveState()
            consume()
            val declaration2 = parseLexicalBinding(newSuffixes)
            if (declaration2 == null) {
                loadState()
                break
            }
            discardState()
            declarations.add(declaration)
        }

        return BindingListNode(declarations)
    }

    private fun parseLexicalBinding(suffixes: Suffixes): LexicalBindingNode? {
        // TODO: Attempt the BindingPattern branch
        val identifier = parseBindingIdentifier() ?: return null
        val initializer = parseInitializer(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.In))
        return LexicalBindingNode(identifier, initializer)
    }

    private fun parseSwitchStatement(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseContinueStatement(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseBreakStatement(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseReturnStatement(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseWithStatement(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseLabelledStatement(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseThrowStatement(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseTryStatement(suffixes: Suffixes): StatementNode? {
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

    private fun parseDeclaration(suffixes: Suffixes): StatementNode? {
        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await)
        return parseHoistableDeclaration(newSuffixes) ?:
            parseClassDeclaration(newSuffixes) ?:
            parseLexicalDeclaration(newSuffixes.withIn)
    }

    private fun parseHoistableDeclaration(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseClassDeclaration(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseLexicalDeclaration(suffixes: Suffixes): StatementNode? {
        if (!matchAny(TokenType.Let, TokenType.Const))
            return null

        saveState()

        val isConst = when (consume().type) {
            TokenType.Let -> false
            TokenType.Const -> true
            else -> unreachable()
        }

        val list = parseBindingList(suffixes.filter(Sfx.In, Sfx.Yield, Sfx.Await)) ?: run {
            expected("identifier")
            consume()
            return null
        }

        automaticSemicolonInsertion()
        discardState()

        return LexicalDeclarationNode(isConst, list)
    }

    private fun parseIdentifierReference(suffixes: Suffixes): IdentifierReferenceNode? {
        parseIdentifier()?.let {
            return IdentifierReferenceNode(it.identifierName)
        }

        return when (tokenType) {
            TokenType.Await -> if (suffixes.hasAwait) {
                null
            } else {
                consume()
                IdentifierReferenceNode("yield")
            }
            TokenType.Yield -> if (suffixes.hasYield) {
                null
            } else {
                consume()
                IdentifierReferenceNode("await")
            }
            else -> null
        }
    }

    private fun parseBindingIdentifier(): BindingIdentifierNode? {
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

    private fun parseLabelIdentifier(suffixes: Suffixes): LabelIdentifierNode? {
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

    private fun parseYieldExpression(suffixes: Suffixes): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseArrowFunction(suffixes: Suffixes): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseAsyncArrowFunction(suffixes: Suffixes): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseLeftHandSideExpression(suffixes: Suffixes): ExpressionNode? {
        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await)
        return parseCallExpression(newSuffixes) ?:
            parseNewExpression(newSuffixes) ?:
            parseOptionalExpression(newSuffixes)
    }

    private fun parseNewExpression(suffixes: Suffixes): ExpressionNode? {
        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await)
        if (tokenType != TokenType.New)
            return parseMemberExpression(newSuffixes)

        consume()
        val expr = parseNewExpression(newSuffixes) ?: run {
            expected("expression")
            consume()
            return null
        }
        return NewExpressionNode(expr, null)
    }

    private fun parseCallExpression(suffixes: Suffixes): ExpressionNode? {
        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await)
        val initial = parseMemberExpression(newSuffixes) ?:
            parseSuperCall(newSuffixes) ?:
            parseImportCall(newSuffixes) ?:
            return null

        var callExpression: ExpressionNode? = null

        while (true) {
            if (tokenType == TokenType.TemplateLiteralStart)
                TODO()

            val args = parseArguments(suffixes) ?: break
            callExpression = if (callExpression == null) {
                CallExpressionNode(initial, args)
            } else {
                CallExpressionNode(callExpression, args)
            }
        }

        return callExpression ?: initial
    }

    private fun parseSuperCall(suffixes: Suffixes): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseImportCall(suffixes: Suffixes): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseOptionalExpression(suffixes: Suffixes): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseMemberExpression(suffixes: Suffixes): ExpressionNode? {
        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await)
        val primaryExpression = parsePrimaryExpression(newSuffixes) ?: run {
            parseSuperProperty(newSuffixes)?.also { return it }
            parseMetaProperty()?.also { return it }
            if (tokenType == TokenType.New) {
                consume()
                val expr = parseMemberExpression(newSuffixes) ?: run {
                    discardState()
                    expected("expression")
                    return null
                }
                val args = parseArguments(newSuffixes) ?: run {
                    discardState()
                    expected("parenthesized arguments")
                    return null
                }
                // Spec deviation: MemberExpression does not have any alternatives
                // that involve arguments or new, as it makes the AST tree way more
                // confusing
                return NewExpressionNode(expr, args)
            }
            return null
        }
        var memberExpression: MemberExpressionNode? = null

        while (true) {
            if (tokenType == TokenType.OpenBracket) {
                consume()
                val expression = parseExpression(newSuffixes.withIn) ?: run {
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
                NewTargetNode
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
                ImportMetaNode
            }
            else -> null
        }
    }

    private fun parseSuperProperty(suffixes: Suffixes): ExpressionNode? {
        if (tokenType != TokenType.Super)
            return null
        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await)
        consume()
        return when (tokenType) {
            TokenType.OpenBracket -> {
                consume()
                val expression = parseExpression(newSuffixes.withIn) ?: run {
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

    private fun parseCCEAARH(suffixes: Suffixes, context: CCEAARHContext): CCEAAAHNode? {
        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await)
        return when (context) {
            CCEAARHContext.CallExpression -> {
                val memberExpr = parseMemberExpression(newSuffixes) ?: return null
                saveState()
                val args = parseArguments(newSuffixes) ?: run {
                    loadState()
                    return null
                }
                return CCEAAAHNode(CallExpressionNode(memberExpr, args))
            }
        }
    }

    private fun parseArguments(suffixes: Suffixes): ArgumentsNode? {
        if (tokenType != TokenType.OpenParen)
            return null
        consume()

        val argumentsList = parseArgumentsList(suffixes.filter(Sfx.Yield, Sfx.Await))
        if (argumentsList != null && tokenType == TokenType.Comma) {
            consume()
        }
        consume(TokenType.CloseParen)
        return argumentsList?.let(::ArgumentsNode)
    }

    private fun parseArgumentsList(suffixes: Suffixes): ArgumentsListNode? {
        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await).withIn
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
                val expr = parseAssignmentExpression(newSuffixes) ?: run {
                    expected("expression")
                    consume()
                    return null
                }
                arguments.add(ArgumentListEntry(expr, true))
            } else {
                val expr = parseAssignmentExpression(newSuffixes) ?: run {
                    loadState()
                    return null
                }
                arguments.add(ArgumentListEntry(expr, false))
            }

            first = false
        } while (true)

        return ArgumentsListNode(arguments)
    }

    private fun parsePrimaryExpression(suffixes: Suffixes): PrimaryExpressionNode? {
        if (tokenType == TokenType.This) {
            consume()
            return ThisNode
        }

        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await)

        return parseIdentifierReference(newSuffixes) ?:
            parseLiteral() ?:
            parseArrayLiteral(newSuffixes) ?:
            parseObjectLiteral(newSuffixes) ?:
            parseFunctionExpression(newSuffixes) ?:
            parseClassExpression(newSuffixes) ?:
            parseGeneratorExpression(newSuffixes) ?:
            parseAsyncFunctionExpression(newSuffixes) ?:
            parseAsyncGeneratorExpression(newSuffixes) ?:
            parseRegularExpressionLiteral(newSuffixes) ?:
            parseTemplateLiteral(newSuffixes) ?:
            parseCPEAAPL(newSuffixes, CPEAAPLContext.PrimaryExpression)
    }

    private fun parseLiteral(): LiteralNode? {
        return parseNullLiteral() ?:
            parseBooleanLiteral() ?:
            parseNumericLiteral() ?:
            parseStringLiteral()
    }

    private fun parseNullLiteral(): LiteralNode? {
        return if (tokenType == TokenType.NullLiteral) NullNode else null
    }

    private fun parseBooleanLiteral(): LiteralNode? {
        return when (tokenType) {
            TokenType.True -> TrueNode
            TokenType.False -> FalseNode
            else -> null
        }?.also {
            consume()
        }
    }

    private fun parseNumericLiteral(): LiteralNode? {
        return if (tokenType == TokenType.NumericLiteral) {
            NumericLiteralNode(consume().asDouble())
        } else null
    }

    private fun parseStringLiteral(): LiteralNode? {
        return if (tokenType == TokenType.StringLiteral) {
            StringLiteralNode(consume().asString())
        } else null
    }

    private fun parseArrayLiteral(suffixes: Suffixes): PrimaryExpressionNode? {
        // TODO
        return null
    }

    private fun parseObjectLiteral(suffixes: Suffixes): PrimaryExpressionNode? {
        // TODO
        return null
    }

    private fun parseFunctionExpression(suffixes: Suffixes): PrimaryExpressionNode? {
        // TODO
        return null
    }

    private fun parseClassExpression(suffixes: Suffixes): PrimaryExpressionNode? {
        // TODO
        return null
    }

    private fun parseGeneratorExpression(suffixes: Suffixes): PrimaryExpressionNode? {
        // TODO
        return null
    }

    private fun parseAsyncFunctionExpression(suffixes: Suffixes): PrimaryExpressionNode? {
        // TODO
        return null
    }

    private fun parseAsyncGeneratorExpression(suffixes: Suffixes): PrimaryExpressionNode? {
        // TODO
        return null
    }

    private fun parseRegularExpressionLiteral(suffixes: Suffixes): PrimaryExpressionNode? {
        // TODO
        return null
    }

    private fun parseTemplateLiteral(suffixes: Suffixes): PrimaryExpressionNode? {
        // TODO
        return null
    }

    enum class CPEAAPLContext {
        PrimaryExpression,
    }

    private fun parseCPEAAPL(suffixes: Suffixes, context: CPEAAPLContext): CPEAAPLNode? {
        return when (context) {
            CPEAAPLContext.PrimaryExpression -> {
                if (tokenType != TokenType.OpenParen)
                    return null
                saveState()
                consume()
                val expr = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn) ?: run {
                    loadState()
                    return null
                }
                consume(TokenType.CloseParen)
                discardState()
                return CPEAAPLNode(expr, CPEAAPLContext.PrimaryExpression)
            }
        }
    }

    private fun parseAssignmentExpression(suffixes: Suffixes): ExpressionNode? {
        val newSuffixes = suffixes.filter(Sfx.In, Sfx.Yield, Sfx.Await)

        fun doMatch(): ExpressionNode? {
            return parseConditionalExpression(newSuffixes) ?:
                if (suffixes.hasYield) parseYieldExpression(newSuffixes) else null ?:
                parseArrowFunction(newSuffixes) ?:
                parseAsyncArrowFunction(newSuffixes)
        }

        var expr = doMatch() ?: return null
        var node: AssignmentExpressionNode? = null

        while (true) {
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
                else -> return node ?: expr
            }
            consume()

            val rhs = parseAssignmentExpression(newSuffixes) ?: run {
                expected("expression")
                consume()
                loadState()
                return null
            }

            node = if (node == null) {
                AssignmentExpressionNode(expr, rhs, op)
            } else {
                AssignmentExpressionNode(node, rhs, op)
            }
        }
    }

    private fun parseConditionalExpression(suffixes: Suffixes): ExpressionNode? {
        val newSuffixes = suffixes.filter(Sfx.In, Sfx.Yield, Sfx.Await)
        val lhs = parseShortCircuitExpression(newSuffixes) ?: return null
        if (tokenType != TokenType.Question)
            return lhs

        consume()
        val middle = parseAssignmentExpression(newSuffixes.withIn)
        if (middle == null) {
            consume()
            expected("expression")
            return null
        }

        consume(TokenType.Colon)
        val rhs = parseAssignmentExpression(newSuffixes.withIn)
        return if (rhs == null) {
            consume()
            expected("expression")
            null
        } else ConditionalExpressionNode(lhs, middle, rhs)
    }

    private fun parseShortCircuitExpression(suffixes: Suffixes): ExpressionNode? {
        return parseLogicalORExpression(suffixes) ?: parseCoalesceExpresion(suffixes)
    }

    private fun parseCoalesceExpresion(suffixes: Suffixes): ExpressionNode? {
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

    private fun parseLogicalORExpression(suffixes: Suffixes): ExpressionNode? {
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

    private fun parseLogicalANDExpression(suffixes: Suffixes): ExpressionNode? {
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

    private fun parseBitwiseORExpression(suffixes: Suffixes): ExpressionNode? {
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

    private fun parseBitwiseXORExpression(suffixes: Suffixes): ExpressionNode? {
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

    private fun parseBitwiseANDExpression(suffixes: Suffixes): ExpressionNode? {
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

    private fun parseEqualityExpression(suffixes: Suffixes): ExpressionNode? {
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

    private fun parseRelationalExpression(suffixes: Suffixes): ExpressionNode? {
        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await)
        val lhs = parseShiftExpression(newSuffixes) ?: return null
        val op = when (tokenType) {
            TokenType.LessThan -> RelationalExpressionNode.Operator.LessThan
            TokenType.GreaterThan -> RelationalExpressionNode.Operator.GreaterThan
            TokenType.LessThanEquals -> RelationalExpressionNode.Operator.LessThanEquals
            TokenType.GreaterThanEquals -> RelationalExpressionNode.Operator.GreaterThanEquals
            TokenType.Instanceof -> RelationalExpressionNode.Operator.Instanceof
            TokenType.In -> if (suffixes.hasIn) RelationalExpressionNode.Operator.In else return lhs
            else -> return lhs
        }
        consume()

        val rhs = parseRelationalExpression(newSuffixes)
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else RelationalExpressionNode(lhs, rhs, op)
    }

    private fun parseShiftExpression(suffixes: Suffixes): ExpressionNode? {
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

    private fun parseAdditiveExpression(suffixes: Suffixes): ExpressionNode? {
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

    private fun parseMultiplicativeExpression(suffixes: Suffixes): ExpressionNode? {
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

    private fun parseExponentiationExpression(suffixes: Suffixes): ExpressionNode? {
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

    private fun parseUnaryExpression(suffixes: Suffixes): ExpressionNode? {
        val op = when (tokenType) {
            TokenType.Delete -> UnaryExpressionNode.Operator.Delete
            TokenType.Void -> UnaryExpressionNode.Operator.Void
            TokenType.Typeof -> UnaryExpressionNode.Operator.Typeof
            TokenType.Plus -> UnaryExpressionNode.Operator.Plus
            TokenType.Minus -> UnaryExpressionNode.Operator.Minus
            TokenType.Tilde -> UnaryExpressionNode.Operator.BitwiseNot
            TokenType.Exclamation -> UnaryExpressionNode.Operator.Not
            else -> return parseUpdateExpression(suffixes) ?: run {
                if (suffixes.hasAwait) {
                    parseAwaitExpression(suffixes)
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

    private fun parseUpdateExpression(suffixes: Suffixes): ExpressionNode? {
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

    private fun parseAwaitExpression(suffixes: Suffixes): ExpressionNode? {
        if (tokenType != TokenType.Await)
            return null
        consume()

        val expr = parseUnaryExpression(suffixes.filter(Sfx.Yield).withAwait)
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

    private class Suffixes private constructor(private val types: Set<Sfx>) {
        constructor() : this(emptySet())

        val hasYield: Boolean get() = Sfx.Yield in types
        val hasAwait: Boolean get() = Sfx.Await in types
        val hasIn: Boolean get() = Sfx.In in types
        val hasReturn: Boolean get() = Sfx.Return in types
        val hasDefault: Boolean get() = Sfx.Default in types
        val hasTagged: Boolean get() = Sfx.Tagged in types

        val withYield: Suffixes get() = Suffixes(types + Sfx.Yield)
        val withAwait: Suffixes get() = Suffixes(types + Sfx.Await)
        val withIn: Suffixes get() = Suffixes(types + Sfx.In)
        val withReturn: Suffixes get() = Suffixes(types + Sfx.Return)
        val withDefault: Suffixes get() = Suffixes(types + Sfx.Default)
        val withTagged: Suffixes get() = Suffixes(types + Sfx.Tagged)

        val withoutYield: Suffixes get() = Suffixes(types - Sfx.Yield)
        val withoutAwait: Suffixes get() = Suffixes(types - Sfx.Await)
        val withoutIn: Suffixes get() = Suffixes(types - Sfx.In)
        val withoutReturn: Suffixes get() = Suffixes(types - Sfx.Return)
        val withoutDefault: Suffixes get() = Suffixes(types - Sfx.Default)
        val withoutTagged: Suffixes get() = Suffixes(types - Sfx.Tagged)

        fun with(vararg types: Sfx) = Suffixes(this.types + types)
        fun without(vararg types: Sfx) = Suffixes(this.types - types)
        fun filter(vararg types: Sfx) = Suffixes(this.types.filter { it in types }.toSet())
    }

    enum class Sfx {
        Yield,
        Await,
        In,
        Return,
        Default,
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
