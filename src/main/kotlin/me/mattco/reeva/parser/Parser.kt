package me.mattco.reeva.parser

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.expressions.TemplateLiteralNode
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

class Parser(text: String) {
    private val tokens = Lexer(text).toList() + Token(TokenType.Eof, "", "", SourceLocation(-1, -1), SourceLocation(-1, -1))

    val syntaxErrors = mutableListOf<SyntaxError>()
    private lateinit var goalSymbol: GoalSymbol

    private var inYieldContext = false
    private var inAwaitContext = false
    private var inReturnContext = false
    private var inInContext = false
    private var inDefaultContext = false
    private var inTaggedContext = false

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
        val statementList = parseStatementList() ?: StatementListNode(emptyList())
        if (!isDone)
            unexpected("token ${token.value}")
        if (stateStack.size != 1) {
            throw IllegalStateException("parseScript ended with ${stateStack.size - 1} extra states on the stack")
        }
        return ScriptNode(statementList)
    }

    fun parseModule() {
        goalSymbol = GoalSymbol.Module
        TODO()
    }

    private fun parseStatementList(): StatementListNode? {
        val statements = mutableListOf<StatementListItemNode>()

        var statement = parseStatementListItem() ?: return null
        statements.add(statement)

        while (true) {
            statement = parseStatementListItem() ?: break
            statements.add(statement)
        }

        return StatementListNode(statements)
    }

    private fun parseStatementListItem(): StatementListItemNode? {
        return parseStatement() ?: parseDeclaration()
    }

    private fun parseStatement(): StatementNode? {
        return parseBlockStatement() ?:
                parseVariableStatement() ?:
                parseEmptyStatement() ?:
                parseExpressionStatement() ?:
                parseIfStatement() ?:
                parseBreakableStatement() ?:
                parseContinueStatement() ?:
                parseBreakStatement() ?:
                (if (inReturnContext) parseReturnStatement() else null) ?:
                parseWithStatement() ?:
                parseLabelledStatement() ?:
                parseThrowStatement() ?:
                parseTryStatement() ?:
                parseDebuggerStatement()
    }

    private fun parseBlockStatement(): BlockStatementNode? {
        return parseBlock()?.let(::BlockStatementNode)
    }

    private fun parseBlock(): BlockNode? {
        if (tokenType != TokenType.OpenCurly)
            return null

        consume()
        val statements = parseStatementList()
        consume(TokenType.CloseCurly)

        return BlockNode(statements)
    }

    private fun parseVariableStatement(): VariableStatementNode? {
        if (tokenType != TokenType.Var)
            return null
        consume()

        val list = withIn { parseVariableDeclarationList() } ?: run {
            expected("identifier")
            consume()
            return null
        }

        automaticSemicolonInsertion()

        return VariableStatementNode(list)
    }

    private fun parseVariableDeclarationList(): VariableDeclarationList? {
        val declarations = mutableListOf<VariableDeclarationNode>()

        val declaration = parseVariableDeclaration() ?: return null
        declarations.add(declaration)

        while (tokenType == TokenType.Comma) {
            saveState()
            consume()
            val declaration2 = parseVariableDeclaration()
            if (declaration2 == null) {
                loadState()
                break
            }
            discardState()
            declarations.add(declaration2)
        }

        return VariableDeclarationList(declarations)
    }

    private fun parseVariableDeclaration(): VariableDeclarationNode? {
        // TODO: Attempt the BindingPattern branch
        val identifier = parseBindingIdentifier() ?: return null
        val initializer = parseInitializer()
        return VariableDeclarationNode(identifier, initializer)
    }

    private fun parseInitializer(): InitializerNode? {
        if (tokenType != TokenType.Equals)
            return null

        saveState()
        consume()
        val expr = parseAssignmentExpression() ?: run {
            loadState()
            return null
        }
        discardState()
        return InitializerNode(expr)
    }

    private fun parseEmptyStatement(): StatementNode? {
        return if (tokenType == TokenType.Semicolon) {
            consume()
            EmptyStatementNode
        } else null
    }

    private fun parseExpressionStatement(): StatementNode? {
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

        return withIn { parseExpression() }?.let(::ExpressionStatementNode)?.also {
            automaticSemicolonInsertion()
        }
    }

    private fun parseExpression(): ExpressionNode? {
        val expressions = mutableListOf<ExpressionNode>()

        val expr1 = parseAssignmentExpression() ?: return null
        expressions.add(expr1)

        fun returnVal() = if (expressions.size == 1) expressions[0] else CommaExpressionNode(expressions)

        while (tokenType == TokenType.Comma) {
            saveState()
            consume()
            val expr2 = parseAssignmentExpression() ?: run {
                loadState()
                return returnVal()
            }
            discardState()
            expressions.add(expr2)
        }

        return returnVal()
    }

    private fun parseIfStatement(): StatementNode? {
        if (tokenType != TokenType.If)
            return null

        consume()
        consume(TokenType.OpenParen)

        val condition = withIn { parseExpression() } ?: run {
            expected("expression")
            consume()
            return null
        }

        consume(TokenType.CloseParen)
        val trueBlock = parseStatement() ?: run {
            expected("statement")
            consume()
            return null
        }

        if (tokenType != TokenType.Else)
            return IfStatementNode(condition, trueBlock, null)

        consume()
        val falseBlock = parseStatement() ?: run {
            expected("statement")
            consume()
            return null
        }

        return IfStatementNode(condition, trueBlock, falseBlock)
    }

    private fun parseBreakableStatement(): StatementNode? {
        return parseIterationStatement() ?: parseSwitchStatement()
    }

    // @ECMA why is this nonterminal so big :(
    private fun parseIterationStatement(): StatementNode? {

        when (tokenType) {
            TokenType.Do -> {
                consume()

                val statement = parseStatement() ?: run {
                    expected("statement")
                    consume()
                    return null
                }

                consume(TokenType.While)
                consume(TokenType.OpenParen)
                val condition = withIn { parseExpression() } ?: run {
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

                val expression = withIn { parseExpression() } ?: run {
                    expected("expression")
                    consume()
                    return null
                }

                consume(TokenType.CloseParen)
                val statement = parseStatement() ?: run {
                    expected("statement")
                    consume()
                    return null
                }

                return WhileStatementNode(expression, statement)
            }
            TokenType.For -> {
                return parseForStatement()
            }
            else -> return null
        }
    }

    private fun parseForStatement(): StatementNode? {
        if (tokenType != TokenType.For)
            return null
        consume()
        if (tokenType == TokenType.Await)
            TODO()
        consume(TokenType.OpenParen)
        return parseNormalForStatement() ?: parseForInOfStatement()
    }

    private fun parseNormalForStatement(): StatementNode? {
        val initializer = when (tokenType) {
            TokenType.Semicolon -> {
                consume()
                null
            }
            TokenType.Var -> {
                saveState()
                consume()
                val declList = parseVariableDeclarationList()
                if (declList == null || tokenType != TokenType.Semicolon) {
                    loadState()
                    return null
                } else {
                    consume()
                    discardState()
                }
                VariableStatementNode(declList)
            }
            TokenType.Let, TokenType.Const -> parseLexicalDeclaration(forceSemi = true) ?: return null
            else -> {
                saveState()
                val expr = parseExpression()
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

        val condition = withIn { parseExpression() }
        consume(TokenType.Semicolon)
        val incrementer = withIn { parseExpression() }
        consume(TokenType.CloseParen)
        val body = parseStatement() ?: run {
            expected("statement")
            consume()
            return null
        }

        return ForStatementNode(initializer, condition, incrementer, body)
    }

    private fun parseForInOfStatement(): StatementNode? {
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
                parseLeftHandSideExpression()?.also { discardState() } ?: run {
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
            withIn { parseExpression() } ?: run {
                expected("expression")
                consume()
                return null
            }
        } else {
            withIn { parseAssignmentExpression() } ?: run {
                expected("expression")
                consume()
                return null
            }
        }

        consume(TokenType.CloseParen)
        val body = parseStatement() ?: run {
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

    private fun parseBindingList(): BindingListNode? {
        val declarations = mutableListOf<LexicalBindingNode>()

        val declaration = parseLexicalBinding() ?: return null
        declarations.add(declaration)

        while (tokenType == TokenType.Comma) {
            saveState()
            consume()
            val declaration2 = parseLexicalBinding()
            if (declaration2 == null) {
                loadState()
                break
            }
            discardState()
            declarations.add(declaration)
        }

        return BindingListNode(declarations)
    }

    private fun parseLexicalBinding(): LexicalBindingNode? {
        // TODO: Attempt the BindingPattern branch
        val identifier = parseBindingIdentifier() ?: return null
        val initializer = parseInitializer()
        return LexicalBindingNode(identifier, initializer)
    }

    private fun parseSwitchStatement(): StatementNode? {
        // TODO
        return null
    }

    private fun parseContinueStatement(): StatementNode? {
        // TODO
        return null
    }

    private fun parseBreakStatement(): StatementNode? {
        if (tokenType != TokenType.Break)
            return null
        consume()
        if ('\n' in token.trivia) {
            automaticSemicolonInsertion()
            return BreakStatementNode(null)
        }
        val expr = parseLabelIdentifier()
        automaticSemicolonInsertion()
        return BreakStatementNode(expr)
    }

    private fun parseReturnStatement(): StatementNode? {
        if (tokenType != TokenType.Return)
            return null
        consume()
        if ('\n' in token.trivia) {
            automaticSemicolonInsertion()
            return ReturnStatementNode(null)
        }
        val expr = withIn { parseExpression() }
        automaticSemicolonInsertion()
        return ReturnStatementNode(expr)
    }

    private fun parseWithStatement(): StatementNode? {
        // TODO
        return null
    }

    private fun parseLabelledStatement(): StatementNode? {
        // TODO
        return null
    }

    private fun parseThrowStatement(): ThrowStatementNode? {
        if (tokenType != TokenType.Throw)
            return null

        consume()

        if ('\n' in token.trivia) {
            unexpected("newline, expected expression")
            consume()
            return null
        }

        val expr = parseExpression() ?: run {
            expected("expression")
            consume()
            return null
        }

        automaticSemicolonInsertion()
        return ThrowStatementNode(expr)
    }

    private fun parseTryStatement(): TryStatementNode? {
        if (tokenType != TokenType.Try)
            return null
        consume()

        val tryBlock = parseBlock() ?: run {
            expected("block")
            consume()
            return null
        }

        val catchBlock = parseCatch()

        val finallyBlock = if (tokenType == TokenType.Finally) {
            consume()
            parseBlock() ?: run {
                expected("block")
                consume()
                return null
            }
        } else null

        if (catchBlock == null && finallyBlock == null) {
            expected("'catch' or 'finally' keyword")
            return null
        }

        return TryStatementNode(tryBlock, catchBlock, finallyBlock)
    }

    private fun parseCatch(): CatchNode? {
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

        val block = parseBlock() ?: run {
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

    private fun parseDeclaration(): StatementNode? {
        return parseHoistableDeclaration() ?:
            parseClassDeclaration() ?:
            withIn { parseLexicalDeclaration() }
    }

    private fun parseHoistableDeclaration(): StatementNode? {
        return parseFunctionDeclaration() ?:
            parseGeneratorDeclaration() ?:
            parseAsyncFunctionDeclaration() ?:
            parseAsyncGeneratorDeclaration()
    }

    private fun parseFunctionDeclaration(): StatementNode? {
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
        val parameters = parseFormalParameters() ?: return null

        consume(TokenType.CloseParen)
        consume(TokenType.OpenCurly)
        val body = parseFunctionBody()
        consume(TokenType.CloseCurly)

        return FunctionDeclarationNode(identifier, parameters, FunctionStatementList(body))
    }

    private fun parseFormalParameters(): FormalParametersNode? {
        if (tokenType == TokenType.CloseParen)
            return FormalParametersNode(FormalParameterListNode(emptyList()), null)

        val parameters = mutableListOf<FormalParameterNode>()

        parseFunctionRestParameter()?.also {
            return FormalParametersNode(FormalParameterListNode(emptyList()), it)
        }

        var restNode: FormalRestParameterNode? = null

        var identifier = parseBindingIdentifier() ?: return null
        var initializer = withIn { parseInitializer() }
        parameters.add(FormalParameterNode(BindingElementNode(SingleNameBindingNode(identifier, initializer))))

        while (tokenType == TokenType.Comma) {
            consume()
            identifier = parseBindingIdentifier() ?: break
            initializer = withIn { parseInitializer() }
            parameters.add(FormalParameterNode(BindingElementNode(SingleNameBindingNode(identifier, initializer))))
        }

        if (tokenType == TokenType.TripleDot)
            restNode = parseFunctionRestParameter()

        return FormalParametersNode(FormalParameterListNode(parameters), restNode)
    }

    private fun parseFunctionRestParameter(): FormalRestParameterNode? {
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

    private fun parseFunctionBody(): StatementListNode? {
        return withReturn { parseStatementList() }
    }

    private fun parseGeneratorDeclaration(): StatementNode? {
        // TODO
        return null
    }

    private fun parseAsyncFunctionDeclaration(): StatementNode? {
        // TODO
        return null
    }

    private fun parseAsyncGeneratorDeclaration(): StatementNode? {
        // TODO
        return null
    }

    private fun parseClassDeclaration(): StatementNode? {
        if (tokenType != TokenType.Class)
            return null

        consume()

        val identifier = if (!inDefaultContext) {
            parseBindingIdentifier() ?: run {
                expected("identifier")
                consume()
                return null
            }
        } else null

        // Null indicates an error
        val tail = parseClassTail() ?: return null

        return ClassDeclarationNode(ClassNode(identifier, tail.heritage, tail.body))
    }

    private fun parseClassTail(): ClassTailNode? {
        val heritage = if (tokenType == TokenType.Extends) {
            consume()
            parseLeftHandSideExpression() ?: run {
                expected("expression")
                consume()
                return null
            }
        } else null

        consume(TokenType.OpenCurly)

        val elements = mutableListOf<ClassElementNode>()
        while (tokenType != TokenType.CloseCurly) {
            val element = parseClassElement() ?: break
            if (element.method == null)
                continue
            elements.add(element)
        }

        consume(TokenType.CloseCurly)

        return ClassTailNode(heritage, ClassElementList(elements))
    }

    private fun parseClassElement(): ClassElementNode? {
        if (tokenType == TokenType.Semicolon) {
            consume()
            return ClassElementNode(null, null)
        }

        val isStatic = tokenType == TokenType.Identifier && token.value == "static" && peek(1).type != TokenType.OpenParen
        if (isStatic)
            consume()

        val method = parseMethodDefinition() ?: run {
            if (isStatic) {
                consume()
                expected("method definition")
            }
            return null
        }

        return ClassElementNode(method, isStatic)
    }

    private fun parseLexicalDeclaration(forceSemi: Boolean = false): StatementNode? {
        if (!matchAny(TokenType.Let, TokenType.Const))
            return null

        saveState()

        val isConst = when (consume().type) {
            TokenType.Let -> false
            TokenType.Const -> true
            else -> unreachable()
        }

        val list = parseBindingList() ?: run {
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

    private fun parseIdentifierReference(): IdentifierReferenceNode? {
        parseIdentifier()?.let {
            return IdentifierReferenceNode(it.identifierName)
        }

        return when (tokenType) {
            TokenType.Await -> if (inAwaitContext) {
                null
            } else {
                consume()
                IdentifierReferenceNode("yield")
            }
            TokenType.Yield -> if (inYieldContext) {
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

    private fun parseLabelIdentifier(): LabelIdentifierNode? {
        return parseIdentifierReference()?.let { LabelIdentifierNode(it.identifierName) }
    }

    private fun parseIdentifier(): IdentifierNode? {
        return when {
            tokenType != TokenType.Identifier -> null
            isReserved(token.value) -> null
            else -> IdentifierNode(consume().value)
        }
    }

    private fun parseIdentifierName(): IdentifierNode? {
        if (!token.isIdentifierName)
            return null
        return IdentifierNode(consume().value)
    }

    private fun parseYieldExpression(): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseArrowFunction(): ArrowFunctionNode? {
        saveState()
        val parameters = parseArrowParameters() ?: run {
            loadState()
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
        discardState()
        val body = parseConciseBody() ?: run {
            expected("arrow function body")
            return null
        }
        return ArrowFunctionNode(parameters, body)
    }

    private fun parseArrowParameters(): ASTNode? {
        val identifier = parseBindingIdentifier()
        if (identifier != null)
            return identifier

        saveState()

        val cpeaapl = parseCPEAAPL() ?: run {
            loadState()
            return null
        }

        val elements = cpeaapl.covered

        if (elements.count { it.isSpread } > 1) {
            loadState()
            return null
        }

        if (elements.indexOfFirst { it.isSpread }.let { it != -1 && it != elements.lastIndex }) {
            loadState()
            return null
        }

        for (element in elements) {
            if (!element.isSpread && element.node !is AssignmentExpressionNode && element.node !is CommaExpressionNode && element.node !is IdentifierReferenceNode) {
                loadState()
                return null
            }
        }

        discardState()

        val parameters = elements.flatMap {
            if (it.node is CommaExpressionNode) {
                it.node.expressions.map { CPEAPPLPart(it, false) }
            } else listOf(it)
        }.filterNot { it.isSpread }.map {
            val (identifier, initializer) = if (it.node is AssignmentExpressionNode) {
                expect(it.node.lhs is IdentifierReferenceNode)
                BindingIdentifierNode(it.node.lhs.identifierName) to it.node.rhs
            } else {
                expect(it.node is IdentifierReferenceNode)
                BindingIdentifierNode(it.node.identifierName) to null
            }

            FormalParameterNode(BindingElementNode(SingleNameBindingNode(identifier, initializer?.let(::InitializerNode))))
        }

        val restParameter = elements.firstOrNull { it.isSpread }?.let {
            FormalRestParameterNode(BindingRestElementNode(it.node as BindingIdentifierNode))
        }

        return FormalParametersNode(FormalParameterListNode(parameters), restParameter)
    }

    private fun parseConciseBody(): ASTNode? {
        return if (tokenType == TokenType.OpenCurly) {
            consume()
            val body = withReturn { parseFunctionBody() }
            consume(TokenType.CloseCurly)
            FunctionStatementList(body ?: StatementListNode(emptyList()))
        } else {
            parseExpression()
        }
    }

    private fun parseAsyncArrowFunction(): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseLeftHandSideExpression(): ExpressionNode? {
        return parseCallExpression() ?:
            parseNewExpression() ?:
            parseOptionalExpression()
    }

    private fun parseNewExpression(): ExpressionNode? {
        if (tokenType != TokenType.New)
            return parseMemberExpression()

        consume()
        val expr = parseNewExpression() ?: run {
            expected("expression")
            consume()
            return null
        }
        return NewExpressionNode(expr, null)
    }

    private fun parseCallExpression(): ExpressionNode? {
        val initial = parseMemberExpression() ?:
            parseSuperCall() ?:
            parseImportCall() ?:
            return null

        var callExpression: ExpressionNode? = null

        while (true) {
            if (tokenType == TokenType.TemplateLiteralStart)
                TODO()

            if (tokenType == TokenType.OpenParen) {
                val args = parseArguments() ?: break
                callExpression = if (callExpression == null) {
                    CallExpressionNode(initial, args)
                } else {
                    CallExpressionNode(callExpression, args)
                }
            } else if (tokenType == TokenType.OpenBracket) {
                consume()
                val expression = withIn { parseExpression() } ?: run {
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

    private fun parseSuperCall(): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseImportCall(): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseOptionalExpression(): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseMemberExpression(): ExpressionNode? {
        val primaryExpression = parsePrimaryExpression() ?: run {
            parseSuperProperty()?.also { return it }
            parseMetaProperty()?.also { return it }
            if (tokenType == TokenType.New) {
                consume()
                val expr = parseMemberExpression() ?: run {
                    discardState()
                    expected("expression")
                    return null
                }
                val args = parseArguments() ?: run {
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
                val expression = withIn { parseExpression() } ?: run {
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

    private fun parseSuperProperty(): ExpressionNode? {
        if (tokenType != TokenType.Super)
            return null
        consume()
        return when (tokenType) {
            TokenType.OpenBracket -> {
                consume()
                val expression = withIn { parseExpression() } ?: run {
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

    private fun parseCCEAARH(context: CCEAARHContext): CCEAAAHNode? {
        return when (context) {
            CCEAARHContext.CallExpression -> {
                val memberExpr = parseMemberExpression() ?: return null
                saveState()
                val args = parseArguments() ?: run {
                    loadState()
                    return null
                }
                discardState()
                return CCEAAAHNode(CallExpressionNode(memberExpr, args))
            }
        }
    }

    private fun parseArguments(): ArgumentsNode? {
        if (tokenType != TokenType.OpenParen)
            return null
        consume()

        val argumentsList = parseArgumentsList()
        if (argumentsList != null && tokenType == TokenType.Comma) {
            consume()
        }
        consume(TokenType.CloseParen)
        return argumentsList?.let(::ArgumentsNode) ?: ArgumentsNode(ArgumentsListNode(emptyList()))
    }

    private fun parseArgumentsList(): ArgumentsListNode? {
        val arguments = mutableListOf<ArgumentListEntry>()
        var first = true

        do {
            saveState()

            if (!first) {
                if (tokenType != TokenType.Comma) {
                    discardState()
                    break
                }
                consume()
            }

            if (tokenType == TokenType.TripleDot) {
                consume()
                val expr = withIn { parseAssignmentExpression() } ?: run {
                    expected("expression")
                    consume()
                    discardState()
                    return null
                }
                arguments.add(ArgumentListEntry(expr, true))
            } else {
                val expr = withIn { parseAssignmentExpression() } ?: run {
                    loadState()
                    return null
                }
                arguments.add(ArgumentListEntry(expr, false))
            }

            discardState()
            first = false
        } while (true)

        return ArgumentsListNode(arguments)
    }

    private fun parsePrimaryExpression(): PrimaryExpressionNode? {
        if (tokenType == TokenType.This) {
            consume()
            return ThisNode
        }

        val result = parseIdentifierReference() ?:
            parseLiteral() ?:
            parseArrayLiteral() ?:
            parseObjectLiteral() ?:
            parseFunctionExpression() ?:
            parseClassExpression() ?:
            parseGeneratorExpression() ?:
            parseAsyncFunctionExpression() ?:
            parseAsyncGeneratorExpression() ?:
            parseRegularExpressionLiteral() ?:
            withoutTagged { parseTemplateLiteral() }

        if (result != null)
            return result

        saveState()
        val cpeaapl = parseCPEAAPL() ?: run {
            discardState()
            return null
        }

        if (cpeaapl.covered.isEmpty() || cpeaapl.covered[0].isSpread || cpeaapl.covered[0].node !is ExpressionNode) {
            loadState()
            return null
        }

        discardState()
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

    private fun parseArrayLiteral(): PrimaryExpressionNode? {
        if (tokenType != TokenType.OpenBracket)
            return null

        consume()

        val elements = mutableListOf<ArrayElementNode>()

        fun getElement(): ArrayElementNode? {
            if (tokenType == TokenType.CloseBracket) {
                return null
            } else if (tokenType == TokenType.Comma) {
                consume()
                return ArrayElementNode(null, ArrayElementNode.Type.Elision)
            }
            var type = if (tokenType == TokenType.TripleDot) {
                consume()
                ArrayElementNode.Type.Spread
            } else ArrayElementNode.Type.Normal
            val expr = withIn { parseAssignmentExpression() } ?: run {
                expected("expression")
                consume()
                return null
            }
            return ArrayElementNode(expr, type)
        }

        elements.add(getElement() ?: run {
            consume(TokenType.CloseBracket)
            return ArrayLiteralNode(elements)
        })

        while (tokenType == TokenType.Comma) {
            consume()
            elements.add(getElement() ?: break)
        }

        consume(TokenType.CloseBracket)

        if (elements.isNotEmpty() && elements.last().type == ArrayElementNode.Type.Elision)
            elements.removeLast()

        return ArrayLiteralNode(elements)
    }

    private fun parseObjectLiteral(): ObjectLiteralNode? {
        if (tokenType != TokenType.OpenCurly)
            return null

        consume()
        val list = parsePropertyDefinitionList()
        if (list != null && tokenType == TokenType.Comma)
            consume()

        consume(TokenType.CloseCurly)
        return ObjectLiteralNode(list)
    }

    private fun parsePropertyDefinitionList(): PropertyDefinitionListNode? {
        val properties = mutableListOf<PropertyDefinitionNode>()

        val def = parsePropertyDefinitionNode() ?: return null
        properties.add(def)

        while (tokenType == TokenType.Comma) {
            saveState()
            consume()
            val def2 = parsePropertyDefinitionNode() ?: run {
                loadState()
                return PropertyDefinitionListNode(properties)
            }
            discardState()
            properties.add(def2)
        }

        return PropertyDefinitionListNode(properties)
    }

    private fun parsePropertyDefinitionNode(): PropertyDefinitionNode? {
        if (tokenType == TokenType.TripleDot) {
            consume()
            val expr = withIn { parseAssignmentExpression() } ?: run {
                expected("expression")
                return null
            }
            return PropertyDefinitionNode(expr, null, PropertyDefinitionNode.Type.Spread)
        } else {
            val method = parseMethodDefinition()
            if (method != null)
                return PropertyDefinitionNode(method, null, PropertyDefinitionNode.Type.Method)

            val propertyName = parsePropertyNameNode()
            if (propertyName != null) {
                consume(TokenType.Colon)
                val expr = withIn { parseAssignmentExpression() } ?: run {
                    expected("expression")
                    return null
                }
                return PropertyDefinitionNode(propertyName, expr, PropertyDefinitionNode.Type.KeyValue)
            }

            val identifier = parseIdentifierReference() ?: return null

            return PropertyDefinitionNode(identifier, null, PropertyDefinitionNode.Type.Shorthand)
        }
    }

    private fun parseMethodDefinition(): MethodDefinitionNode? {
        saveState()
        var type = MethodDefinitionNode.Type.Normal

        val name = parsePropertyNameNode()?.let {
            if (it.expr is IdentifierNode && it.expr.identifierName in listOf("get", "set")) {
                val newName = parsePropertyNameNode()
                if (newName != null) {
                    type = if (it.expr.identifierName == "get") {
                        MethodDefinitionNode.Type.Getter
                    } else MethodDefinitionNode.Type.Setter
                    return@let newName
                }
            }
            it
        }

        if (name == null || tokenType != TokenType.OpenParen) {
            loadState()
            return null
        }
        consume()
        discardState()

        val params = withoutYield { withoutAwait { parseFormalParameters() } } ?: run {
            expected("parameters")
            consume()
            return null
        }

        consume(TokenType.CloseParen)

        if (type == MethodDefinitionNode.Type.Getter && params.functionParameters.parameters.isNotEmpty()) {
            syntaxError("expected object literal getter to have zero parameters")
            return null
        }

        if (type == MethodDefinitionNode.Type.Setter && params.functionParameters.parameters.size != 1) {
            syntaxError("expected object literal setter to have one parameter")
            return null
        }

        consume(TokenType.OpenCurly)
        val body = parseFunctionBody()
        consume(TokenType.CloseCurly)

        return MethodDefinitionNode(name, params, FunctionStatementList(body), type)
    }

    private fun parsePropertyNameNode(): PropertyNameNode? {
        if (tokenType == TokenType.OpenBracket) {
            saveState()
            consume()
            val expr = withIn { parseAssignmentExpression() } ?: run {
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

    private fun parseFunctionExpression(): PrimaryExpressionNode? {
        if (tokenType != TokenType.Function)
            return null

        consume()
        val id = parseBindingIdentifier()
        consume(TokenType.OpenParen)
        val args = parseFormalParameters() ?: return null
        consume(TokenType.CloseParen)
        consume(TokenType.OpenCurly)
        val body = parseFunctionBody()
        consume(TokenType.CloseCurly)

        return FunctionExpressionNode(id, args, FunctionStatementList(body))
    }

    private fun parseClassExpression(): PrimaryExpressionNode? {
        // TODO
        return null
    }

    private fun parseGeneratorExpression(): PrimaryExpressionNode? {
        // TODO
        return null
    }

    private fun parseAsyncFunctionExpression(): PrimaryExpressionNode? {
        // TODO
        return null
    }

    private fun parseAsyncGeneratorExpression(): PrimaryExpressionNode? {
        // TODO
        return null
    }

    private fun parseRegularExpressionLiteral(): PrimaryExpressionNode? {
        // TODO
        return null
    }

    private fun parseTemplateLiteral(): PrimaryExpressionNode? {
        if (tokenType != TokenType.TemplateLiteralStart)
            return null

        consume()

        val parts = mutableListOf<ExpressionNode>()

        while (tokenType != TokenType.TemplateLiteralEnd) {
            if (tokenType == TokenType.UnterminatedTemplateLiteral) {
                consume()
                expected("template literal terminator")
                return null
            }

            when (tokenType) {
                TokenType.TemplateLiteralString -> parts.add(StringLiteralNode(consume().value))
                TokenType.TemplateLiteralExprStart -> {
                    consume()
                    parts.add(withIn { parseExpression() } ?: run {
                        expected("expression")
                        consume()
                        return null
                    })
                    if (tokenType != TokenType.TemplateLiteralExprEnd) {
                        expected("'}'")
                        consume()
                        return null
                    }
                    consume()
                }
                else -> {
                    unexpected(tokenType.name)
                    consume()
                    return null
                }
            }
        }

        consume(TokenType.TemplateLiteralEnd)
        return TemplateLiteralNode(parts)
    }

    private fun parseCPEAAPL(): CPEAAPLNode? {
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
            val expr = withIn { parseExpression() } ?: return null
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

        if (parts.isEmpty()) {
            consume(TokenType.CloseParen)
            return CPEAAPLNode(emptyList())
        }

        if (!parts.last().isSpread && tokenType == TokenType.Comma)
            consume()

        consume(TokenType.CloseParen)

        return CPEAAPLNode(parts)
    }

    private fun parseAssignmentExpression(): ExpressionNode? {
        fun doMatch(): ExpressionNode? {
            return parseArrowFunction() ?:
                parseConditionalExpression() ?:
                if (inYieldContext) parseYieldExpression() else null ?:
                parseAsyncArrowFunction()
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

            val rhs = parseAssignmentExpression() ?: run {
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

    private fun parseConditionalExpression(): ExpressionNode? {
        val lhs = parseShortCircuitExpression() ?: return null
        if (tokenType != TokenType.Question)
            return lhs

        consume()
        val middle = withIn { parseAssignmentExpression() }
        if (middle == null) {
            consume()
            expected("expression")
            return null
        }

        consume(TokenType.Colon)
        val rhs = withIn { parseAssignmentExpression() }
        return if (rhs == null) {
            consume()
            expected("expression")
            null
        } else ConditionalExpressionNode(lhs, middle, rhs)
    }

    private fun parseShortCircuitExpression(): ExpressionNode? {
        return parseLogicalORExpression() ?: parseCoalesceExpresion()
    }

    private fun parseCoalesceExpresion(): ExpressionNode? {
        saveState()
        val head = parseBitwiseORExpression() ?: run {
            loadState()
            return null
        }
        if (tokenType != TokenType.DoubleQuestion) {
            loadState()
            return null
        }
        consume()
        discardState()

        val expr = parseCoalesceExpresion()
        return if (expr == null) {
            expected("expression")
            consume()
            null
        } else CoalesceExpressionNode(head, expr)
    }

    private fun parseLogicalORExpression(): ExpressionNode? {
        val lhs = parseLogicalANDExpression() ?: return null
        if (tokenType != TokenType.DoublePipe)
            return lhs
        consume()

        val rhs = parseLogicalORExpression()
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else LogicalORExpressionNode(lhs, rhs)
    }

    private fun parseLogicalANDExpression(): ExpressionNode? {
        val lhs = parseBitwiseORExpression() ?: return null
        if (tokenType != TokenType.DoubleAmpersand)
            return lhs
        consume()

        val rhs = parseLogicalANDExpression()
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else LogicalANDExpressionNode(lhs, rhs)
    }

    private fun parseBitwiseORExpression(): ExpressionNode? {
        val lhs = parseBitwiseXORExpression() ?: return null
        if (tokenType != TokenType.Pipe)
            return lhs
        consume()

        val rhs = parseBitwiseORExpression()
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else BitwiseORExpressionNode(lhs, rhs)
    }

    private fun parseBitwiseXORExpression(): ExpressionNode? {
        val lhs = parseBitwiseANDExpression() ?: return null
        if (tokenType != TokenType.Caret)
            return lhs
        consume()

        val rhs = parseBitwiseXORExpression()
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else BitwiseXORExpressionNode(lhs, rhs)
    }

    private fun parseBitwiseANDExpression(): ExpressionNode? {
        val lhs = parseEqualityExpression() ?: return null
        if (tokenType != TokenType.Ampersand)
            return lhs
        consume()

        val rhs = parseBitwiseANDExpression()
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else BitwiseANDExpressionNode(lhs, rhs)
    }

    private fun parseEqualityExpression(): ExpressionNode? {
        val lhs = parseRelationalExpression() ?: return null
        val op = when (tokenType) {
            TokenType.DoubleEquals -> EqualityExpressionNode.Operator.NonstrictEquality
            TokenType.ExclamationEquals -> EqualityExpressionNode.Operator.NonstrictInequality
            TokenType.TripleEquals -> EqualityExpressionNode.Operator.StrictEquality
            TokenType.ExclamationDoubleEquals -> EqualityExpressionNode.Operator.StrictInequality
            else -> return lhs
        }
        consume()


        val rhs = parseEqualityExpression()
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else EqualityExpressionNode(lhs, rhs, op)
    }

    private fun parseRelationalExpression(): ExpressionNode? {
        val lhs = parseShiftExpression() ?: return null
        val op = when (tokenType) {
            TokenType.LessThan -> RelationalExpressionNode.Operator.LessThan
            TokenType.GreaterThan -> RelationalExpressionNode.Operator.GreaterThan
            TokenType.LessThanEquals -> RelationalExpressionNode.Operator.LessThanEquals
            TokenType.GreaterThanEquals -> RelationalExpressionNode.Operator.GreaterThanEquals
            TokenType.Instanceof -> RelationalExpressionNode.Operator.Instanceof
            TokenType.In -> if (inInContext) RelationalExpressionNode.Operator.In else return lhs
            else -> return lhs
        }
        consume()

        val rhs = parseRelationalExpression()
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else RelationalExpressionNode(lhs, rhs, op)
    }

    private fun parseShiftExpression(): ExpressionNode? {
        val lhs = parseAdditiveExpression() ?: return null
        val op = when (tokenType) {
            TokenType.ShiftLeft -> ShiftExpressionNode.Operator.ShiftLeft
            TokenType.ShiftRight -> ShiftExpressionNode.Operator.ShiftRight
            TokenType.UnsignedShiftRight -> ShiftExpressionNode.Operator.UnsignedShiftRight
            else -> return lhs
        }
        consume()

        val rhs = parseShiftExpression()
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else ShiftExpressionNode(lhs, rhs, op)
    }

    private fun parseAdditiveExpression(): ExpressionNode? {
        val lhs = parseMultiplicativeExpression() ?: return null
        val isSubtraction = when (tokenType) {
            TokenType.Plus -> false
            TokenType.Minus -> true
            else -> return lhs
        }
        consume()

        val rhs = parseAdditiveExpression()
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else AdditiveExpressionNode(lhs, rhs, isSubtraction)
    }

    private fun parseMultiplicativeExpression(): ExpressionNode? {
        val lhs = parseExponentiationExpression() ?: return null
        val op = when (tokenType) {
            TokenType.Asterisk -> MultiplicativeExpressionNode.Operator.Multiply
            TokenType.Slash -> MultiplicativeExpressionNode.Operator.Divide
            TokenType.Percent -> MultiplicativeExpressionNode.Operator.Modulo
            else -> return lhs
        }
        consume()

        val rhs = parseMultiplicativeExpression()
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else MultiplicativeExpressionNode(lhs, rhs, op)
    }

    private fun parseExponentiationExpression(): ExpressionNode? {
        val lhs = parseUnaryExpression() ?: return null
        if (tokenType != TokenType.DoubleAsterisk)
            return lhs
        consume()

        val rhs = parseExponentiationExpression()
        return if (rhs == null) {
            expected("expression")
            consume()
            null
        } else ExponentiationExpressionNode(lhs, rhs)
    }

    private fun parseUnaryExpression(): ExpressionNode? {
        val op = when (tokenType) {
            TokenType.Delete -> UnaryExpressionNode.Operator.Delete
            TokenType.Void -> UnaryExpressionNode.Operator.Void
            TokenType.Typeof -> UnaryExpressionNode.Operator.Typeof
            TokenType.Plus -> UnaryExpressionNode.Operator.Plus
            TokenType.Minus -> UnaryExpressionNode.Operator.Minus
            TokenType.Tilde -> UnaryExpressionNode.Operator.BitwiseNot
            TokenType.Exclamation -> UnaryExpressionNode.Operator.Not
            else -> return parseUpdateExpression() ?: run {
                if (inAwaitContext) {
                    parseAwaitExpression()
                } else null
            }
        }
        consume()

        val expr = parseUnaryExpression()
        return if (expr == null) {
            expected("expression")
            consume()
            null
        } else UnaryExpressionNode(expr, op)
    }

    private fun parseUpdateExpression(): ExpressionNode? {
        val expr: ExpressionNode
        val isIncrement: Boolean
        val isPostfix: Boolean

        if (matchAny(TokenType.PlusPlus, TokenType.MinusMinus)) {
            isIncrement = consume().type == TokenType.PlusPlus
            isPostfix = false
            expr = parseUnaryExpression().let {
                if (it == null) {
                    expected("expression")
                    consume()
                    return null
                } else it
            }
        } else {
            expr = parseLeftHandSideExpression() ?: return null
            if (!matchAny(TokenType.PlusPlus, TokenType.MinusMinus))
                return expr
            if ('\n' in token.trivia)
                return expr
            isPostfix = true
            isIncrement = consume().type == TokenType.PlusPlus
        }

        return UpdateExpressionNode(expr, isIncrement, isPostfix)
    }

    private fun parseAwaitExpression(): ExpressionNode? {
        if (tokenType != TokenType.Await)
            return null
        consume()

        val expr = parseUnaryExpression()
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

    fun <T> withYield(block: () -> T): T {
        val prev = inYieldContext
        inYieldContext = true
        return block().also { inYieldContext = prev }
    }

    fun <T> withAwait(block: () -> T): T {
        val prev = inAwaitContext
        inAwaitContext = true
        return block().also { inAwaitContext = prev }
    }

    fun <T> withReturn(block: () -> T): T {
        val prev = inReturnContext
        inReturnContext = true
        return block().also { inReturnContext = prev }
    }

    fun <T> withIn(block: () -> T): T {
        val prev = inInContext
        inInContext = true
        return block().also { inInContext = prev }
    }

    fun <T> withDefault(block: () -> T): T {
        val prev = inDefaultContext
        inDefaultContext = true
        return block().also { inDefaultContext = prev }
    }

    fun <T> withTagged(block: () -> T): T {
        val prev = inTaggedContext
        inTaggedContext = true
        return block().also { inTaggedContext = prev }
    }

    fun <T> withoutYield(block: () -> T): T {
        val prev = inYieldContext
        inYieldContext = false
        return block().also { inYieldContext = prev }
    }

    fun <T> withoutAwait(block: () -> T): T {
        val prev = inAwaitContext
        inAwaitContext = false
        return block().also { inAwaitContext = prev }
    }

    fun <T> withoutReturn(block: () -> T): T {
        val prev = inReturnContext
        inReturnContext = false
        return block().also { inReturnContext = prev }
    }

    fun <T> withoutIn(block: () -> T): T {
        val prev = inInContext
        inInContext = false
        return block().also { inInContext = prev }
    }

    fun <T> withoutDefault(block: () -> T): T {
        val prev = inDefaultContext
        inDefaultContext = false
        return block().also { inDefaultContext = prev }
    }

    fun <T> withoutTagged(block: () -> T): T {
        val prev = inTaggedContext
        inTaggedContext = false
        return block().also { inTaggedContext = prev }
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
