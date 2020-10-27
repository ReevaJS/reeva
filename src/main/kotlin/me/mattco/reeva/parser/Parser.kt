package me.mattco.reeva.parser

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.literals.PropertyNameNode
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.lexer.Lexer
import me.mattco.reeva.lexer.SourceLocation
import me.mattco.reeva.lexer.Token
import me.mattco.reeva.lexer.TokenType
import me.mattco.reeva.utils.all
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable
import kotlin.properties.Delegates

class Parser(text: String) {
    private val tokens = Lexer(text).toList() + Token(TokenType.Eof, "", "", SourceLocation(-1, -1), SourceLocation(-1, -1))

    val syntaxErrors = mutableListOf<SyntaxError>()
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
                (if (suffixes.hasReturn) parseReturnStatement(tripleSuffix) else null) ?:
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

        return parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn)?.let(::ExpressionStatementNode)?.also {
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
                return parseForStatement(suffixes)
            }
            else -> return null
        }
    }

    private fun parseForStatement(suffixes: Suffixes): StatementNode? {
        if (tokenType != TokenType.For)
            return null
        consume()
        if (tokenType == TokenType.Await)
            TODO()
        consume(TokenType.OpenParen)
        return parseNormalForStatement(suffixes) ?: parseForInOfStatement(suffixes)
    }

    private fun parseNormalForStatement(suffixes: Suffixes): StatementNode? {
        val initializer = when (tokenType) {
            TokenType.Semicolon -> {
                consume()
                null
            }
            TokenType.Var -> {
                saveState()
                consume()
                val declList = parseVariableDeclarationList(suffixes.filter(Sfx.Yield, Sfx.Await))
                if (declList == null || tokenType != TokenType.Semicolon) {
                    loadState()
                    return null
                } else {
                    consume()
                    discardState()
                }
                VariableStatementNode(declList)
            }
            TokenType.Let, TokenType.Const -> parseLexicalDeclaration(suffixes.filter(Sfx.Yield, Sfx.Await), forceSemi = true) ?: return null
            else -> {
                saveState()
                val expr = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await))
                if (expr == null || tokenType != TokenType.Semicolon) {
                    loadState()
                    return null
                } else {
                    consume()
                    discardState()
                }
                expr
            }
        }

        val condition = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn)
        consume(TokenType.Semicolon)
        val incrementer = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn)
        consume(TokenType.CloseParen)
        val body = parseStatement(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)) ?: run {
            expected("statement")
            consume()
            return null
        }

        return ForStatementNode(initializer, condition, incrementer, body)
    }

    private fun parseForInOfStatement(suffixes: Suffixes): StatementNode? {
        val initializer = when (tokenType) {
            TokenType.Var -> {
                consume()
                parseBindingIdentifier()?.let(::ForBindingNode) ?: run {
                    expected("identifier")
                    consume()
                    return null
                }
            }
            TokenType.Let, TokenType.Const -> {
                val isConst = consume().type == TokenType.Const
                val identifier = parseBindingIdentifier() ?: run {
                    expected("identifier")
                    consume()
                    return null
                }
                ForDeclarationNode(isConst, ForBindingNode(identifier))
            }
            else -> {
                saveState()
                parseLeftHandSideExpression(suffixes.filter(Sfx.Yield, Sfx.Await))?.also { discardState() } ?: run {
                    loadState()
                    return null
                }
            }
        }

        val isIn = when (tokenType) {
            TokenType.In -> {
                consume()
                true
            }
            TokenType.Of -> {
                consume()
                false
            }
            else -> {
                expected("'in' or 'of'")
                consume()
                return null
            }
        }

        val expression = if (isIn) {
            parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn) ?: run {
                expected("expression")
                consume()
                return null
            }
        } else {
            parseAssignmentExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn) ?: run {
                expected("expression")
                consume()
                return null
            }
        }

        consume(TokenType.CloseParen)
        val body = parseStatement(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)) ?: run {
            expected("statement")
            consume()
            return null
        }

        return if (isIn) {
            ForInNode(initializer, expression, body)
        } else {
            ForOfNode(initializer, expression, body)
        }
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
        if (tokenType != TokenType.Break)
            return null
        consume()
        if ('\n' in token.trivia) {
            automaticSemicolonInsertion()
            return BreakStatementNode(null)
        }
        val expr = parseLabelIdentifier(suffixes.filter(Sfx.Yield, Sfx.Await))
        automaticSemicolonInsertion()
        return BreakStatementNode(expr)
    }

    private fun parseReturnStatement(suffixes: Suffixes): StatementNode? {
        if (tokenType != TokenType.Return)
            return null
        consume()
        if ('\n' in token.trivia) {
            automaticSemicolonInsertion()
            return ReturnStatementNode(null)
        }
        val expr = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn)
        automaticSemicolonInsertion()
        return ReturnStatementNode(expr)
    }

    private fun parseWithStatement(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseLabelledStatement(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseThrowStatement(suffixes: Suffixes): ThrowStatementNode? {
        if (tokenType != TokenType.Throw)
            return null

        consume()

        if ('\n' in token.trivia) {
            unexpected("newline, expected expression")
            consume()
            return null
        }

        val expr = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await)) ?: run {
            expected("expression")
            consume()
            return null
        }

        automaticSemicolonInsertion()
        return ThrowStatementNode(expr)
    }

    private fun parseTryStatement(suffixes: Suffixes): TryStatementNode? {
        if (tokenType != TokenType.Try)
            return null
        consume()

        val tryBlock = parseBlock(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)) ?: run {
            expected("block")
            consume()
            return null
        }

        val catchBlock = parseCatch(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)) ?: run {
            expected("catch keyword")
            consume()
            return null
        }

        return TryStatementNode(tryBlock, catchBlock)
    }

    private fun parseCatch(suffixes: Suffixes): CatchNode? {
        if (tokenType != TokenType.Catch)
            return null
        consume()

        val parameter = if (tokenType == TokenType.OpenParen) {
            consume()
            parseBindingIdentifier()?.also {
                consume(TokenType.CloseParen)
            } ?: run {
                expected("identifier")
                consume()
                return null
            }
        } else null

        val block = parseBlock(suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Return)) ?: run {
            expected("block")
            consume()
            return null
        }

        return CatchNode(parameter, block)
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
        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await, Sfx.Default)
        return parseFunctionDeclaration(newSuffixes) ?:
            parseGeneratorDeclaration(newSuffixes) ?:
            parseAsyncFunctionDeclaration(newSuffixes) ?:
            parseAsyncGeneratorDeclaration(newSuffixes)
    }

    private fun parseFunctionDeclaration(suffixes: Suffixes): StatementNode? {
        if (tokenType != TokenType.Function)
            return null

        consume()
        val identifier = if (tokenType == TokenType.OpenParen) {
            consume()
            null
        } else {
            parseBindingIdentifier().also { consume(TokenType.OpenParen) } ?: run {
                expected("identifier")
                consume()
                return null
            }
        }

        // Null return value indicates an error
        val parameters = parseFormalParameters(Suffixes()) ?: return null

        consume(TokenType.CloseParen)
        consume(TokenType.OpenCurly)
        val body = parseFunctionBody(Suffixes())
        consume(TokenType.CloseCurly)

        return FunctionDeclarationNode(identifier, parameters, FunctionStatementList(body))
    }

    private fun parseFormalParameters(suffixes: Suffixes): FormalParametersNode? {
        if (tokenType == TokenType.CloseParen)
            return FormalParametersNode(FormalParameterListNode(emptyList()), null)

        val parameters = mutableListOf<FormalParameterNode>()

        parseFunctionRestParameter(suffixes.filter(Sfx.Yield, Sfx.Await))?.also {
            return FormalParametersNode(FormalParameterListNode(emptyList()), it)
        }

        var restNode: FormalRestParameterNode? = null

        var identifier = parseBindingIdentifier() ?: return null
        var initializer = parseInitializer(suffixes.withIn)
        parameters.add(FormalParameterNode(BindingElementNode(SingleNameBindingNode(identifier, initializer))))

        while (tokenType == TokenType.Comma) {
            consume()
            identifier = parseBindingIdentifier() ?: break
            initializer = parseInitializer(suffixes.withIn)
            parameters.add(FormalParameterNode(BindingElementNode(SingleNameBindingNode(identifier, initializer))))
        }

        if (tokenType == TokenType.TripleDot)
            restNode = parseFunctionRestParameter(suffixes.filter(Sfx.Yield, Sfx.Await))

        return FormalParametersNode(FormalParameterListNode(parameters), restNode)
    }

    private fun parseFunctionRestParameter(suffixes: Suffixes): FormalRestParameterNode? {
        if (tokenType != TokenType.TripleDot)
            return null

        consume()
        // TODO: Binding patterns
        val identifier = parseBindingIdentifier() ?: run {
            expected("identifier")
            consume()
            return null
        }
        return FormalRestParameterNode(BindingRestElementNode(identifier))
    }

    private fun parseFunctionBody(suffixes: Suffixes): StatementListNode? {
        return parseStatementList(suffixes.filter(Sfx.Yield, Sfx.Await).withReturn)
    }

    private fun parseGeneratorDeclaration(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseAsyncFunctionDeclaration(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseAsyncGeneratorDeclaration(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseClassDeclaration(suffixes: Suffixes): StatementNode? {
        // TODO
        return null
    }

    private fun parseLexicalDeclaration(suffixes: Suffixes, forceSemi: Boolean = false): StatementNode? {
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

        if (forceSemi) {
            // For use in a for statement
            if (tokenType != TokenType.Semicolon) {
                loadState()
                return null
            }
            consume()
        } else {
            automaticSemicolonInsertion()
        }

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

    private fun parseArrowFunction(suffixes: Suffixes): ArrowFunctionNode? {
        saveState()
        val parameters = parseArrowParameters(suffixes.filter(Sfx.Yield, Sfx.Await)) ?: run {
            discardState()
            return null
        }
        if ('\n' in token.trivia) {
            loadState()
            return null
        }
        if (tokenType != TokenType.Arrow) {
            loadState()
            return null
        }
        consume()
        val body = parseConciseBody(suffixes.filter(Sfx.In)) ?: run {
            expected("arrow function body")
            discardState()
            return null
        }
        return ArrowFunctionNode(parameters, body)
    }

    private fun parseArrowParameters(suffixes: Suffixes): ASTNode? {
        val identifier = parseBindingIdentifier()
        if (identifier != null)
            return identifier

        val cpeaapl = parseCPEAAPL(suffixes.filter(Sfx.Yield, Sfx.Await)) ?: return null

        val elements = cpeaapl.covered

        if (elements.count { it.isSpread } > 1) {
            syntaxError("arrow function cannot have multiple rest parameters")
            return null
        }

        if (elements.indexOfFirst { it.isSpread }.let { it != -1 && it != elements.lastIndex }) {
            syntaxError("arrow function cannot have normal parameters after a rest parameter")
            return null
        }

        for (element in elements) {
            if (!element.isSpread && element.node !is AssignmentExpressionNode && element.node !is BindingIdentifierNode) {
                syntaxError("Invalid arrow function parameter")
                return null
            }
        }

        val parameters = elements.filterNot { it.isSpread }.map {
            val (identifier, initializer) = if (it.node is AssignmentExpressionNode) {
                expect(it.node.lhs is BindingIdentifierNode)
                it.node.lhs to it.node.rhs
            } else {
                expect(it.node is BindingIdentifierNode)
                it.node to null
            }

            FormalParameterNode(BindingElementNode(SingleNameBindingNode(identifier, initializer?.let(::InitializerNode))))
        }

        val restParameter = elements.firstOrNull { it.isSpread }?.let {
            FormalRestParameterNode(BindingRestElementNode(it.node as BindingIdentifierNode))
        }

        return FormalParametersNode(FormalParameterListNode(parameters), restParameter)
    }

    private fun parseConciseBody(suffixes: Suffixes): ASTNode? {
        return if (tokenType == TokenType.OpenCurly) {
            consume()
            val body = parseFunctionBody(Suffixes().withReturn)
            consume(TokenType.CloseCurly)
            FunctionStatementList(body ?: StatementListNode(emptyList()))
        } else {
            parseExpression(suffixes.filter(Sfx.In))
        }
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

            if (tokenType == TokenType.OpenParen) {
                val args = parseArguments(suffixes) ?: break
                callExpression = if (callExpression == null) {
                    CallExpressionNode(initial, args)
                } else {
                    CallExpressionNode(callExpression, args)
                }
            } else if (tokenType == TokenType.OpenBracket) {
                consume()
                val expression = parseExpression(newSuffixes.withIn) ?: run {
                    expected("expression")
                    consume()
                    return null
                }
                consume(TokenType.CloseBracket)
                callExpression = if (callExpression == null) {
                    MemberExpressionNode(initial, expression, MemberExpressionNode.Type.Computed)
                } else {
                    MemberExpressionNode(callExpression, expression, MemberExpressionNode.Type.Computed)
                }
            } else if (tokenType == TokenType.Period) {
                consume()
                val identifier = parseIdentifierName() ?: run {
                    expected("identifier")
                    consume()
                    return null
                }
                callExpression = if (callExpression == null) {
                    MemberExpressionNode(initial, identifier, MemberExpressionNode.Type.NonComputed)
                } else {
                    MemberExpressionNode(callExpression, identifier, MemberExpressionNode.Type.NonComputed)
                }
            } else break
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
        return argumentsList?.let(::ArgumentsNode) ?: ArgumentsNode(ArgumentsListNode(emptyList()))
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

        val result = parseIdentifierReference(newSuffixes) ?:
            parseLiteral() ?:
            parseArrayLiteral(newSuffixes) ?:
            parseObjectLiteral(newSuffixes) ?:
            parseFunctionExpression(newSuffixes) ?:
            parseClassExpression(newSuffixes) ?:
            parseGeneratorExpression(newSuffixes) ?:
            parseAsyncFunctionExpression(newSuffixes) ?:
            parseAsyncGeneratorExpression(newSuffixes) ?:
            parseRegularExpressionLiteral(newSuffixes) ?:
            parseTemplateLiteral(newSuffixes)

        if (result != null)
            return result

        saveState()
        val cpeaapl = parseCPEAAPL(newSuffixes) ?: run {
            discardState()
            return null
        }

        if (cpeaapl.covered.isEmpty() || cpeaapl.covered[0].isSpread || cpeaapl.covered[0].node !is ExpressionNode) {
            loadState()
            return null
        }

        return ParenthesizedExpressionNode(cpeaapl.covered[0].node as ExpressionNode)
    }

    private fun parseLiteral(): LiteralNode? {
        return parseNullLiteral() ?:
            parseBooleanLiteral() ?:
            parseNumericLiteral() ?:
            parseStringLiteral()
    }

    private fun parseNullLiteral(): LiteralNode? {
        return if (tokenType == TokenType.NullLiteral) {
            consume()
            NullNode
        } else null
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
        if (tokenType != TokenType.OpenBracket)
            return null

        consume()

        val elements = mutableListOf<ArrayElementNode>()

        fun getElement(): ArrayElementNode? {
            if (tokenType == TokenType.Comma) {
                consume()
                return ArrayElementNode(null, ArrayElementNode.Type.Elision)
            }
            var type = if (tokenType == TokenType.TripleDot) {
                consume()
                ArrayElementNode.Type.Spread
            } else ArrayElementNode.Type.Normal
            val expr = parseAssignmentExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn) ?: run {
                expected("expression")
                consume()
                return null
            }
            return ArrayElementNode(expr, type)
        }

        elements.add(getElement() ?: return ArrayLiteralNode(elements))

        while (tokenType == TokenType.Comma) {
            consume()
            elements.add(getElement() ?: break)
        }

        consume(TokenType.CloseBracket)

        if (elements.isNotEmpty() && elements.last().type == ArrayElementNode.Type.Elision)
            elements.removeLast()

        return ArrayLiteralNode(elements)
    }

    private fun parseObjectLiteral(suffixes: Suffixes): ObjectLiteralNode? {
        if (tokenType != TokenType.OpenCurly)
            return null

        consume()
        val list = parsePropertyDefinitionList(suffixes.filter(Sfx.Yield, Sfx.Await))
        if (list != null && tokenType == TokenType.Comma)
            consume()

        consume(TokenType.CloseCurly)
        return ObjectLiteralNode(list)
    }

    private fun parsePropertyDefinitionList(suffixes: Suffixes): PropertyDefinitionListNode? {
        val properties = mutableListOf<PropertyDefinitionNode>()
        val newSuffixes = suffixes.filter(Sfx.Yield, Sfx.Await)

        val def = parsePropertyDefinitionNode(newSuffixes) ?: return null
        properties.add(def)

        while (tokenType == TokenType.Comma) {
            saveState()
            consume()
            val def2 = parsePropertyDefinitionNode(newSuffixes) ?: run {
                loadState()
                return PropertyDefinitionListNode(properties)
            }
            discardState()
            properties.add(def2)
        }

        return PropertyDefinitionListNode(properties)
    }

    private fun parsePropertyDefinitionNode(suffixes: Suffixes): PropertyDefinitionNode? {
        if (tokenType == TokenType.TripleDot) {
            consume()
            val expr = parseAssignmentExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn) ?: run {
                expected("expression")
                return null
            }
            return PropertyDefinitionNode(expr, null, PropertyDefinitionNode.Type.Spread)
        } else {
            val method = parseMethodDefinition(suffixes.filter(Sfx.Yield, Sfx.Await))
            if (method != null)
                return PropertyDefinitionNode(method, null, PropertyDefinitionNode.Type.Method)

            val propertyName = parsePropertyNameNode(suffixes.filter(Sfx.Yield, Sfx.Await))
            if (propertyName != null) {
                consume(TokenType.Colon)
                val expr = parseAssignmentExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn) ?: run {
                    expected("expression")
                    return null
                }
                return PropertyDefinitionNode(propertyName, expr, PropertyDefinitionNode.Type.KeyValue)
            }

            val identifier = parseIdentifierReference(suffixes.filter(Sfx.Yield, Sfx.Await)) ?: run {
                expected("identifier")
                return null
            }

            return PropertyDefinitionNode(identifier, null, PropertyDefinitionNode.Type.Shorthand)
        }
    }

    private fun parseMethodDefinition(suffixes: Suffixes): PropertyDefinitionNode? {
        // TODO
        return null
    }

    private fun parsePropertyNameNode(suffixes: Suffixes): PropertyNameNode? {
        if (tokenType == TokenType.OpenBracket) {
            saveState()
            consume()
            val expr = parseAssignmentExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn) ?: run {
                loadState()
                return null
            }
            consume(TokenType.CloseBracket)
            discardState()
            return PropertyNameNode(expr, true)
        } else {
            val name = parseIdentifierName() ?:
                parseStringLiteral() ?:
                parseNumericLiteral() ?:
                return null

            return PropertyNameNode(name, false)
        }
    }

    private fun parseFunctionExpression(suffixes: Suffixes): PrimaryExpressionNode? {
        if (tokenType != TokenType.Function)
            return null

        consume()
        val id = parseBindingIdentifier()
        consume(TokenType.OpenParen)
        val args = parseFormalParameters(Suffixes()) ?: return null
        consume(TokenType.CloseParen)
        consume(TokenType.OpenCurly)
        val body = parseFunctionBody(Suffixes())
        consume(TokenType.CloseCurly)

        return FunctionExpressionNode(id, args, FunctionStatementList(body))
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

    private fun parseCPEAAPL(suffixes: Suffixes): CPEAAPLNode? {
        if (tokenType != TokenType.OpenParen)
            return null

        consume()

        fun parsePart(): CPEAPPLPart? {
            if (tokenType == TokenType.TripleDot) {
                consume()
                val name = parseBindingIdentifier() ?: run {
                    expected("identifier")
                    return null
                }
                return CPEAPPLPart(name, true)
            }
            val expr = parseExpression(suffixes.filter(Sfx.Yield, Sfx.Await).withIn) ?: return null
            return CPEAPPLPart(expr, false)
        }

        val parts = mutableListOf<CPEAPPLPart>()

        while (tokenType != TokenType.CloseParen) {
            val part = parsePart() ?: break
            parts.add(part)
            if (part.isSpread) {
                break
            } else if (tokenType == TokenType.Comma) {
                consume()
            }
        }

        if (parts.isEmpty())
            return null

        if (!parts.last().isSpread && tokenType == TokenType.Comma)
            consume()

        consume(TokenType.CloseParen)

        return CPEAAPLNode(parts)
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
        if (tokenType == TokenType.CloseCurly || tokenType == TokenType.Eof)
            return token

        expected("semicolon")
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

    data class SyntaxError(
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
