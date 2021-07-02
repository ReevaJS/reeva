package me.mattco.reeva.parser

import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

class Parser(val source: String) {
    private var inDefaultContext = false
    private var inFunctionContext = false
    private var inYieldContext = false
    private var inAsyncContext = false
    private var disableAutoScoping = false

    private val labelStateStack = mutableListOf(LabelState())

    internal val reporter = ErrorReporter(this)

    private val tokenQueue = LinkedBlockingQueue<Token>()
    private val receivedTokenList = LinkedList<Token>()
    private var lastConsumedToken = Token.INVALID

    private var token: Token = Token.INVALID

    private val tokenType: TokenType
        inline get() = token.type
    private val isDone: Boolean
        inline get() = token === Token.INVALID

    internal val sourceStart: TokenLocation
        inline get() = token.start
    internal val sourceEnd: TokenLocation
        inline get() = token.end

    private var isStrict = false

    private fun initLexer() {
        Reeva.threadPool.submit {
            Thread.currentThread().name = "Lexer Thread"
            try {
                val lexer = Lexer(source)
                while (!lexer.isDone)
                    tokenQueue.add(lexer.nextToken())
                tokenQueue.add(Token.EOF)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        consume()
    }

    fun parseScript(): ParsingResult {
        return try {
            initLexer()
            val script = parseScriptImpl()
            ScopeResolver().resolve(script)
            EarlyErrorDetector(reporter).visit(script)
            ParsingResult.Success(script)
        } catch (e: ParsingException) {
            ParsingResult.ParseError(e.message!!, e.start, e.end)
        } catch (e: Throwable) {
            ParsingResult.InternalError(e)
        }
    }

    fun parseModule(): ParsingResult {
        TODO()
    }

    fun parseFunction(expectedKind: Operations.FunctionKind): ParsingResult {
        return try {
            initLexer()

            val function = parseFunctionDeclaration()
            expect(function.kind == expectedKind)

            ParsingResult.Success(function)
        } catch (e: ParsingException) {
            ParsingResult.ParseError(e.message!!, e.start, e.end)
        } catch (e: Throwable) {
            ParsingResult.InternalError(e)
        }
    }

    private fun parseScriptImpl(): ScriptNode = nps {
        // Script :
        //     ScriptBody?
        //
        // ScriptBody :
        //     StatementList

        isStrict = checkForAndConsumeUseStrict()
        ScriptNode(parseStatementList(), isStrict)
    }

    private fun parseModuleImpl(): ModuleNode {
        TODO()
    }

    /*
     * StatementList :
     *     StatementListItem
     *     StatementList StatementListItem
     */
    private fun parseStatementList(): StatementList = nps {
        val list = mutableListOf<StatementNode>()

        while (tokenType.isStatementToken)
            list.add(parseStatement())

        StatementList(list)
    }

    /*
     * StatementListItem :
     *     Statement
     *     Declaration
     *
     * Statement :
     *     BlockStatement
     *     VariableStatement
     *     EmptyStatement
     *     ExpressionStatement
     *     IfStatement
     *     BreakableStatement
     *     ContinueStatement
     *     BreakStatement
     *     [+Return] ReturnStatement
     *     WithStatement
     *     LabelledStatement
     *     ThrowStatement
     *     TryStatement
     *     DebuggerStatement
     *
     * BreakableStatement :
     *     IterationStatement
     *     SwitchStatement
     *
     * IterationStatement :
     *     DoWhileStatement
     *     WhileStatement
     *     ForStatement
     *     ForInOfStatement
     *
     * Declaration :
     *     HoistableDeclaration
     *     ClassDeclaration
     *     LexicalDeclaration
     *
     * HoistableDeclaration :
     *     FunctionDeclaration
     *     GeneratorDeclaration
     *     AsyncFunctionDeclaration
     *     AsyncGeneratorDeclaration
     */
    private fun matchLabellableStatement() = matchAny(
        TokenType.If,
        TokenType.For,
        TokenType.While,
        TokenType.Try,
        TokenType.Switch,
        TokenType.Do,
        TokenType.OpenCurly,
    )

    private fun parseLabellableStatement(): StatementNode? = nps {
        val labels = mutableSetOf<String>()

        while (matchIdentifier() && peek().type == TokenType.Colon) {
            labels.add(parseIdentifier())
            consume(TokenType.Colon)
        }

        val type = when (tokenType) {
            TokenType.If, TokenType.Try, TokenType.OpenCurly -> LabellableBlockType.Block
            TokenType.Do, TokenType.While, TokenType.For -> LabellableBlockType.Loop
            TokenType.Switch -> LabellableBlockType.Switch
            else -> {
                if (labels.isNotEmpty())
                    return@nps parseStatement()

                expect(matchIdentifier())
                return@nps ExpressionStatementNode(parseExpression().also { asi() })
            }
        }

        labelStateStack.last().pushBlock(type, labels)

        when (tokenType) {
            TokenType.If -> parseIfStatement()
            TokenType.For -> parseNormalForAndForEachStatement()
            TokenType.While -> parseWhileStatement()
            TokenType.Try -> parseTryStatement()
            TokenType.Switch -> parseSwitchStatement()
            TokenType.Do -> parseDoWhileStatement()
            TokenType.OpenCurly -> parseBlock()
            else -> unreachable()
        }.also {
            (it as Labellable).labels.addAll(labels)
            labelStateStack.last().popBlock()
        }
    }

    private fun parseStatement(): StatementNode {
        if (matchIdentifier() || matchLabellableStatement())
            return parseLabellableStatement() ?: parseStatement()

        return when (tokenType) {
            TokenType.Var, TokenType.Let, TokenType.Const -> parseVariableDeclaration(false)
            TokenType.Semicolon -> nps {
                consume()
                EmptyStatementNode()
            }
            TokenType.Continue -> parseContinueStatement()
            TokenType.Break -> parseBreakStatement()
            TokenType.Return -> parseReturnStatement()
            TokenType.With -> parseWithStatement()
            TokenType.Throw -> parseThrowStatement()
            TokenType.Debugger -> parseDebuggerStatement()
            TokenType.Function, TokenType.Async -> parseFunctionDeclaration()
            TokenType.Class -> parseClassDeclaration()
            else -> {
                if (tokenType.isExpressionToken) {
                    if (match(TokenType.Function))
                        reporter.functionInExpressionContext()
                    return nps {
                        ExpressionStatementNode(parseExpression().also { asi() })
                    }
                }
                reporter.expected("statement", tokenType)
            }
        }
    }

    private fun asi() {
        if (match(TokenType.Semicolon)) {
            consume()
            return
        }
        if (token.afterNewline)
            return
        if (matchAny(TokenType.CloseCurly, TokenType.Eof))
            return

        reporter.expected(TokenType.Semicolon, tokenType)
    }

    /*
     * VariableStatement :
     *     var VariableDeclarationList ;
     *
     * VariableDeclarationList :
     *     VariableDeclaration
     *     VariableDeclarationList , VariableDeclaration
     *
     * VariableDeclaration
     *     BindingIdentifier Initializer?
     *     BindingPattern initializer (TODO)
     *
     * LexicalDeclaration :
     *     LetOrConst BindingList ;
     *
     * LetOrConst :
     *     let
     *     const
     *
     * BindingList :
     *     LexicalBinding
     *     BindingList , LexicalBinding
     *
     * LexicalBinding :
     *     BindingIdentifier Initializer?
     *     BindingPattern Initializer (TODO)
     */
    private fun parseVariableDeclaration(isForEachLoop: Boolean = false): StatementNode = nps {
        val type = consume()
        expect(type == TokenType.Var || type == TokenType.Let || type == TokenType.Const)

        val declarations = mutableListOf<Declaration>()

        while (true) {
            val start = sourceStart

            if (match(TokenType.OpenCurly))
                TODO()

            val identifier = parseBindingIdentifier()

            val initializer = if (match(TokenType.Equals)) {
                consume()
                parseExpression(2)
            } else if (!isForEachLoop && type == TokenType.Const) {
                reporter.constMissingInitializer()
            } else null

            declarations.add(
                Declaration(identifier, initializer).withPosition(start, lastConsumedToken.end)
            )

            if (match(TokenType.Comma)) {
                consume()
            } else break
        }

        if (!isForEachLoop)
            asi()

        if (type == TokenType.Var) {
            VariableDeclarationNode(declarations)
        } else {
            LexicalDeclarationNode(isConst = type == TokenType.Const, declarations)
        }
    }

    /*
     * DoWhileStatement :
     *     do Statement while ( Expression ) ;
     */
    private fun parseDoWhileStatement(): StatementNode = nps {
        consume(TokenType.Do)
        val statement = parseStatement()
        consume(TokenType.While)
        consume(TokenType.OpenParen)
        val condition = parseExpression()
        consume(TokenType.CloseParen)
        asi()
        DoWhileStatementNode(condition, statement)
    }

    /*
     * WhileStatement :
     *     while ( Expression ) Statement
     */
    private fun parseWhileStatement(): StatementNode = nps {
        consume(TokenType.While)
        consume(TokenType.OpenParen)
        val condition = parseExpression(0)
        consume(TokenType.CloseParen)
        val statement = parseStatement()
        asi()
        WhileStatementNode(condition, statement)
    }

    /*
     * SwitchStatement :
     *     switch ( Expression ) CaseBlock
     *
     * CaseBlock :
     *     { CaseClauses? }
     *     { CaseClauses? DefaultClause CaseClauses? }
     *
     * CauseClauses :
     *     CaseClause
     *     CaseClauses CaseClause
     *
     * CaseClause :
     *     case Expression : StatementList?
     *
     * DefaultClause :
     *     default : StatementList?
     */
    private fun parseSwitchStatement(): StatementNode = nps {
        consume(TokenType.Switch)
        consume(TokenType.OpenParen)
        val target = parseExpression(0)
        consume(TokenType.CloseParen)
        consume(TokenType.OpenCurly)

        val clauses = nps {
            val clauses = mutableListOf<SwitchClause>()

            while (matchSwitchClause()) {
                if (match(TokenType.Case)) {
                    consume()
                    val caseTarget = parseExpression(2)
                    consume(TokenType.Colon)
                    if (matchSwitchClause()) {
                        clauses.add(SwitchClause(caseTarget, null))
                    } else {
                        clauses.add(SwitchClause(caseTarget, parseStatementList()))
                    }
                } else {
                    consume(TokenType.Default)
                    consume(TokenType.Colon)
                    if (matchSwitchClause()) {
                        clauses.add(SwitchClause(null, null))
                    } else {
                        clauses.add(SwitchClause(null, parseStatementList()))
                    }
                }
            }

            SwitchClauseList(clauses)
        }

        consume(TokenType.CloseCurly)

        SwitchStatementNode(target, clauses)
    }

    /*
     * ContinueStatement :
     *     continue ;
     *     continue [no LineTerminator here] LabelIdentifier ;
     */
    private fun parseContinueStatement(): StatementNode = nps {
        consume(TokenType.Continue)

        val continueToken = lastConsumedToken

        if (match(TokenType.Semicolon) || token.afterNewline) {
            if (match(TokenType.Semicolon))
                consume()

            labelStateStack.last().validateContinue(continueToken, null)
            return@nps ContinueStatementNode(null)
        }

        if (matchIdentifier()) {
            val identifier = parseIdentifier()
            labelStateStack.last().validateContinue(continueToken, lastConsumedToken)
            return@nps ContinueStatementNode(identifier).also { asi() }
        }

        reporter.expected("identifier", tokenType)
    }

    /*
     * BreakStatement :
     *     break ;
     *     break [no LineTerminator here] LabelIdentifier ;
     */
    private fun parseBreakStatement(): StatementNode = nps {
        consume(TokenType.Break)

        val breakToken = lastConsumedToken

        if (match(TokenType.Semicolon) || token.afterNewline) {
            if (match(TokenType.Semicolon))
                consume()

            labelStateStack.last().validateBreak(breakToken, null)
            return@nps BreakStatementNode(null)
        }

        if (matchIdentifier()) {
            val identifier = parseIdentifier()
            labelStateStack.last().validateBreak(breakToken, lastConsumedToken)
            return@nps BreakStatementNode(identifier).also { asi() }
        }

        reporter.expected("identifier", tokenType)
    }

    /*
     * ReturnStatement :
     *     return ;
     *     return [no LineTerminator here] Expression ;
     */
    private fun parseReturnStatement(): StatementNode = nps {
        expect(inFunctionContext)
        consume(TokenType.Return)

        if (match(TokenType.Semicolon)) {
            consume()
            return@nps ReturnStatementNode(null)
        }

        if (token.afterNewline)
            return@nps ReturnStatementNode(null)

        if (tokenType.isExpressionToken)
            return@nps ReturnStatementNode(parseExpression(0)).also { asi() }

        reporter.expected("expression", tokenType)
    }

    /*
     * WithStatement :
     *     with ( Expression ) Statement
     */
    private fun parseWithStatement(): StatementNode = nps {
        consume(TokenType.With)
        consume(TokenType.OpenParen)
        val expression = parseExpression()
        consume(TokenType.CloseParen)
        val body = parseStatement()
        WithStatementNode(expression, body)
    }

    /*
     * ThrowStatement :
     *     throw [no LineTerminator here] Expresion ;
     */
    private fun parseThrowStatement(): StatementNode = nps {
        consume(TokenType.Throw)
        if (!tokenType.isExpressionToken)
            reporter.expected("expression", tokenType)
        if (token.afterNewline)
            reporter.throwStatementNewLine()
        ThrowStatementNode(parseExpression()).also { asi() }
    }

    /*
     * TryStatement :
     *     try Block Catch
     *     try Block Finally
     *     try Block Catch Finally
     *
     * Catch :
     *     catch ( CatchParameter ) Block
     *     catch Block
     *
     * Finally :
     *     finally Block
     *
     * CatchParameter :
     *     BindingIdentifier
     *     BindingPattern (TODO)
     */
    private fun parseTryStatement(): StatementNode = nps {
        consume(TokenType.Try)
        val tryBlock = parseBlock()

        val catchBlock = if (match(TokenType.Catch)) {
            consume()
            val catchParam = if (match(TokenType.OpenParen)) {
                consume()
                parseBindingIdentifier().also {
                    consume(TokenType.CloseParen)
                }
            } else null
            CatchNode(catchParam, parseBlock())
        } else null

        val finallyBlock = if (match(TokenType.Finally)) {
            consume()
            parseBlock()
        } else null

        if (catchBlock == null && finallyBlock == null)
            reporter.expected(TokenType.Finally, tokenType)

        TryStatementNode(tryBlock, catchBlock, finallyBlock)
    }

    /*
     * DebuggerStatement :
     *     debugger ;
     */
    private fun parseDebuggerStatement(): StatementNode = nps {
        consume(TokenType.Debugger)
        asi()
        DebuggerStatementNode()
    }

    private fun matchSwitchClause() = matchAny(TokenType.Case, TokenType.Default)

    private fun parseNormalForAndForEachStatement(): StatementNode = nps {
        consume(TokenType.For)
        if (match(TokenType.Await))
            TODO()

        consume(TokenType.OpenParen)

        var initializer: ASTNode? = null

        if (!match(TokenType.Semicolon)) {
            if (tokenType.isExpressionToken) {
                initializer = nps { parseExpression(0, false, setOf(TokenType.In)) }
                if (matchForEach())
                    return@nps parseForEachStatement(initializer)
            } else if (tokenType.isVariableDeclarationToken) {
                initializer = parseVariableDeclaration(isForEachLoop = true)

                if (matchForEach())
                    return@nps parseForEachStatement(initializer)
            } else {
                reporter.unexpectedToken(tokenType)
            }
        }

        consume(TokenType.Semicolon)
        val condition = if (match(TokenType.Semicolon)) null else parseExpression(0)

        consume(TokenType.Semicolon)
        val update = if (match(TokenType.CloseParen)) null else parseExpression(0)

        consume(TokenType.CloseParen)

        val body = parseStatement()

        ForStatementNode(initializer, condition, update, body)
    }

    private fun parseForEachStatement(initializer: ASTNode): StatementNode = nps {
        if ((initializer is VariableDeclarationNode && initializer.declarations.size > 1) ||
            (initializer is LexicalDeclarationNode && initializer.declarations.size > 1)
        ) {
            reporter.forEachMultipleDeclarations()
        }

        val isIn = consume() == TokenType.In
        val rhs = parseExpression(0)
        consume(TokenType.CloseParen)

        val body = parseStatement()

        if (isIn) {
            ForInNode(initializer, rhs, body)
        } else {
            ForOfNode(initializer, rhs, body)
        }
    }

    private fun matchForEach() = match(TokenType.In) || (match(TokenType.Identifier) && token.literals == "of")

    /*
     * FunctionDeclaration :
     *     function BindingIdentifier ( FormalParameters ) { FunctionBody }
     *     [+Default] function ( FormalParameters ) { FunctionBody }
     */
    private fun parseFunctionDeclaration(): FunctionDeclarationNode = nps {
        val (identifier, params, body, kind) = parseFunctionHelper(isDeclaration = true)
        FunctionDeclarationNode(identifier!!, params, body, kind)
    }

    /*
     * FunctionExpression :
     *     function BindingIdentifier? ( FormalParameters ) { FunctionBody }
     */
    private fun parseFunctionExpression(): ExpressionNode = nps {
        val (identifier, params, body, isGenerator) = parseFunctionHelper(isDeclaration = false)
        FunctionExpressionNode(identifier, params, body, isGenerator)
    }

    private data class FunctionTemp(
        val identifier: BindingIdentifierNode?,
        val params: ParameterList,
        val body: BlockNode,
        val type: Operations.FunctionKind,
    ) : ASTNodeBase()

    private fun parseFunctionHelper(isDeclaration: Boolean): FunctionTemp = nps {
        val isAsync = if (match(TokenType.Async)) {
            consume()
            true
        } else false

        consume(TokenType.Function)

        val isGenerator = if (match(TokenType.Mul)) {
            consume()
            true
        } else false

        val type = when {
            isAsync && isGenerator -> Operations.FunctionKind.AsyncGenerator
            isAsync -> Operations.FunctionKind.Async
            isGenerator -> Operations.FunctionKind.Generator
            else -> Operations.FunctionKind.Normal
        }

        // TODO: Allow no identifier in default export
        val identifier = when {
            matchIdentifier() -> parseBindingIdentifier()
            isDeclaration -> reporter.functionStatementNoName()
            else -> null
        }

        val params = parseFunctionParameters()

        // TODO: Static Semantics

        val body = parseFunctionBody(isAsync, isGenerator)

        if (body.hasUseStrict && !params.isSimple())
            reporter.at(body).error("Illegal \"use strict\" directive in function with non-simple parameter list")

        FunctionTemp(identifier, params, body, type)
    }

    /*
     * ClassDeclaration :
     *     class BindingIdentifier ClassTail
     *     [+Default] class ClassTail
     */
    private fun parseClassDeclaration(): StatementNode = nps {
        consume(TokenType.Class)
        val identifier = if (!matchIdentifier()) {
            if (!inDefaultContext)
                reporter.classDeclarationNoName()
            null
        } else parseBindingIdentifier()

        ClassDeclarationNode(identifier, parseClassNode())
    }

    /*
     * ClassExpression :
     *     class BindingIdentifier? ClassTail
     */
    private fun parseClassExpression(): ExpressionNode = nps {
        consume(TokenType.Class)
        val identifier = if (matchIdentifier()) {
            parseBindingIdentifier()
        } else null
        ClassExpressionNode(identifier, parseClassNode())
    }

    /*
     * ClassTail :
     *     ClassHeritage? { ClassBody? }
     *
     * ClassHeritage :
     *     extends LeftHandSideExpression
     *
     * ClassBody :
     *     ClassElementList
     *
     * ClassElementList :
     *     ClassElement
     *     ClassElementList ClassElement
     *
     * ClassElement :
     *     MethodDefinition
     *     static MethodDefinition
     *     ;
     */
    private fun parseClassNode(): ClassNode = nps {
        TODO("Do this when I figure out how I want to handle class scopes")

//        val heritage = if (tokenType.isExpressionToken) {
//            consume()
//
//            // TODO: This can only be a LHSExpression, not any arbitrary expression
//            parseExpression()
//        } else null
//
//        consume(TokenType.OpenCurly)
//        val elements = mutableListOf<ClassElementNode>()
//
//        // TODO: Fields
//        while (true) {
//            if (match(TokenType.CloseCurly)) {
//                consume()
//                break
//            }
//
//            if (match(TokenType.Semicolon)) {
//                consume()
//                elements.add(EmptyClassElementNode())
//                continue
//            }
//
//            val isStatic = if (match(TokenType.Static)) {
//                consume()
//                true
//            } else false
//
//            val method = parseMethodDefinition()
//            elements.add(ClassMethodNode(method, isStatic))
//        }
//
//        ClassNode(heritage, elements)
    }

//    private fun parseMethodDefinition(): MethodDefinitionNode = nps {
//
//    }

    /*
     * FormalParameters :
     *     [empty]
     *     FunctionRestParameter
     *     FormalParameterList
     *     FormalParameterList ,
     *     FormalParameterList , FormalRestParameter
     *
     * FormalParameterList :
     *     FormalParameter
     *     FormalParameterList , FormalParameter
     *
     * FunctionRestParameter :
     *     BindingRestElement (TODO: currently just a BindingIdentifier)
     *
     * FormalParameter :
     *     BindingElement (TODO: currently just a BindingIdentifier)
     */
    private fun parseFunctionParameters(): ParameterList = nps {
        consume(TokenType.OpenParen)
        if (match(TokenType.CloseParen)) {
            consume()
            return@nps ParameterList()
        }

        val parameters = mutableListOf<Parameter>()

        while (true) {
            if (match(TokenType.CloseParen))
                break

            if (match(TokenType.TriplePeriod)) {
                nps {
                    consume()
                    val identifier = parseBindingIdentifier()
                    if (!match(TokenType.CloseParen))
                        reporter.paramAfterRest()
                    Parameter(identifier, null, true)
                }.also(parameters::add)
                break
            } else if (!matchIdentifier()) {
                reporter.expected("expression", tokenType)
            } else {
                nps {
                    val identifier = parseBindingIdentifier()
                    val initializer = if (match(TokenType.Equals)) {
                        consume()
                        parseExpression(0)
                    } else null
                    Parameter(identifier, initializer, false)
                }.also(parameters::add)
            }

            if (!match(TokenType.Comma))
                break
            consume()
        }

        consume(TokenType.CloseParen)
        ParameterList(parameters)
    }

    private fun matchIdentifier() = match(TokenType.Identifier) ||
        (!inYieldContext && !isStrict && match(TokenType.Yield)) ||
        (!inAsyncContext && !isStrict && match(TokenType.Await))

    private fun parseIdentifier(): String {
        expect(matchAny(TokenType.Identifier, TokenType.Await, TokenType.Yield))
        val identifier = token.literals
        consume()
        return identifier
    }

    private fun parseIdentifierReference(): IdentifierReferenceNode = nps {
        IdentifierReferenceNode(parseIdentifier())
    }

    private fun parseBindingIdentifier(): BindingIdentifierNode = nps {
        BindingIdentifierNode(parseIdentifier())
    }

    private fun checkForAndConsumeUseStrict(): Boolean {
        return if (match(TokenType.StringLiteral) && token.literals == "use strict") {
            consume()
            if (match(TokenType.Semicolon))
                consume()
            true
        } else false
    }

    private fun parseBlock(isFunctionBlock: Boolean = false): BlockNode = nps {
        consume(TokenType.OpenCurly)
        val prevIsStrict = isStrict
        val isStrict = checkForAndConsumeUseStrict()

        if (isStrict)
            this.isStrict = true

        val statements = parseStatementList()
        consume(TokenType.CloseCurly)
        this.isStrict = prevIsStrict

//        val varNames = mutableSetOf<String>()
//        val lexNames = mutableSetOf<String>()
//
//        statements.forEach {
//            if (it is VariableDeclarationNode) {
//                it.declarations.forEach { decl ->
//                    val name = decl.identifier.identifierName
//                    if (name in lexNames)
//                        reporter.at(decl.identifier).conflictingVarDeclaration(name)
//                    varNames.add(name)
//                }
//            } else if (it is LexicalDeclarationNode) {
//                it.declarations.forEach { decl ->
//                    val name = decl.identifier.identifierName
//                    if (name in varNames)
//                        reporter.at(decl.identifier).conflictingLexDeclaration(name)
//                    if (name in lexNames)
//                        reporter.at(decl.identifier).duplicateLexDeclaration(name)
//                    lexNames.add(name)
//                }
//            } else if (it is FunctionDeclarationNode) {
//                val name = it.identifier.identifierName
//                if (name in lexNames)
//                    reporter.at(it.identifier).conflictingVarDeclaration(name)
//                lexNames.add(name)
//            }
//        }

        BlockNode(statements, isStrict)
    }

    private fun parseExpression(
        minPrecedence: Int = 0,
        leftAssociative: Boolean = false,
        excludedTokens: Set<TokenType> = emptySet(),
    ): ExpressionNode = nps {
        // TODO: Template literal handling

        var expression = parsePrimaryExpression()

        while (tokenType.isSecondaryToken && tokenType !in excludedTokens) {
            if (tokenType.operatorPrecedence < minPrecedence)
                break
            if (tokenType.operatorPrecedence == minPrecedence && leftAssociative)
                break

            expression = parseSecondaryExpression(
                expression,
                tokenType.operatorPrecedence,
                tokenType.leftAssociative,
            )
        }

        if (match(TokenType.Comma) && minPrecedence <= 1) {
            val expressions = mutableListOf(expression)
            while (match(TokenType.Comma)) {
                consume()
                expressions.add(parseExpression(2))
            }
            CommaExpressionNode(expressions)
        } else expression
    }

    private fun parseSecondaryExpression(
        lhs: ExpressionNode,
        minPrecedence: Int,
        leftAssociative: Boolean,
    ): ExpressionNode {
        fun makeBinaryExpr(op: BinaryOperator): ExpressionNode {
            consume()
            return BinaryExpressionNode(lhs, parseExpression(minPrecedence, leftAssociative), op)
                .withPosition(lhs.sourceStart, lastConsumedToken.end)
        }

        fun makeAssignExpr(op: BinaryOperator?): ExpressionNode {
            consume()
            if (lhs !is IdentifierReferenceNode && lhs !is MemberExpressionNode && lhs !is CallExpressionNode)
                reporter.at(lhs).invalidLhsInAssignment()
            if (isStrict && lhs is IdentifierReferenceNode) {
                val name = lhs.identifierName
                if (name == "eval")
                    reporter.strictAssignToEval()
                if (name == "arguments")
                    reporter.strictAssignToArguments()
            } else if (isStrict && lhs is CallExpressionNode) {
                reporter.at(lhs).invalidLhsInAssignment()
            }

            return AssignmentExpressionNode(lhs, parseExpression(minPrecedence, leftAssociative), op)
                .withPosition(lhs.sourceStart, lastConsumedToken.end)
        }

        return when (tokenType) {
            TokenType.OpenParen -> parseCallExpression(lhs)
            TokenType.Add -> makeBinaryExpr(BinaryOperator.Add)
            TokenType.Sub -> makeBinaryExpr(BinaryOperator.Sub)
            TokenType.BitwiseAnd -> makeBinaryExpr(BinaryOperator.BitwiseAnd)
            TokenType.BitwiseOr -> makeBinaryExpr(BinaryOperator.BitwiseOr)
            TokenType.BitwiseXor -> makeBinaryExpr(BinaryOperator.BitwiseXor)
            TokenType.Coalesce -> makeBinaryExpr(BinaryOperator.Coalesce)
            TokenType.StrictEquals -> makeBinaryExpr(BinaryOperator.StrictEquals)
            TokenType.StrictNotEquals -> makeBinaryExpr(BinaryOperator.StrictNotEquals)
            TokenType.SloppyEquals -> makeBinaryExpr(BinaryOperator.SloppyEquals)
            TokenType.SloppyNotEquals -> makeBinaryExpr(BinaryOperator.SloppyNotEquals)
            TokenType.Exp -> makeBinaryExpr(BinaryOperator.Exp)
            TokenType.And -> makeBinaryExpr(BinaryOperator.And)
            TokenType.Or -> makeBinaryExpr(BinaryOperator.Or)
            TokenType.Mul -> makeBinaryExpr(BinaryOperator.Mul)
            TokenType.Div -> makeBinaryExpr(BinaryOperator.Div)
            TokenType.Mod -> makeBinaryExpr(BinaryOperator.Mod)
            TokenType.LessThan -> makeBinaryExpr(BinaryOperator.LessThan)
            TokenType.GreaterThan -> makeBinaryExpr(BinaryOperator.GreaterThan)
            TokenType.LessThanEquals -> makeBinaryExpr(BinaryOperator.LessThanEquals)
            TokenType.GreaterThanEquals -> makeBinaryExpr(BinaryOperator.GreaterThanEquals)
            TokenType.Instanceof -> makeBinaryExpr(BinaryOperator.Instanceof)
            TokenType.In -> makeBinaryExpr(BinaryOperator.In)
            TokenType.Shl -> makeBinaryExpr(BinaryOperator.Shl)
            TokenType.Shr -> makeBinaryExpr(BinaryOperator.Shr)
            TokenType.UShr -> makeBinaryExpr(BinaryOperator.UShr)
            TokenType.Equals -> makeAssignExpr(null)
            TokenType.MulEquals -> makeAssignExpr(BinaryOperator.Mul)
            TokenType.DivEquals -> makeAssignExpr(BinaryOperator.Div)
            TokenType.ModEquals -> makeAssignExpr(BinaryOperator.Mod)
            TokenType.AddEquals -> makeAssignExpr(BinaryOperator.Add)
            TokenType.SubEquals -> makeAssignExpr(BinaryOperator.Sub)
            TokenType.ShlEquals -> makeAssignExpr(BinaryOperator.Shl)
            TokenType.ShrEquals -> makeAssignExpr(BinaryOperator.Shr)
            TokenType.UShrEquals -> makeAssignExpr(BinaryOperator.UShr)
            TokenType.BitwiseAndEquals -> makeAssignExpr(BinaryOperator.BitwiseAnd)
            TokenType.BitwiseOrEquals -> makeAssignExpr(BinaryOperator.BitwiseOr)
            TokenType.BitwiseXorEquals -> makeAssignExpr(BinaryOperator.BitwiseXor)
            TokenType.ExpEquals -> makeAssignExpr(BinaryOperator.Exp)
            TokenType.AndEquals -> makeAssignExpr(BinaryOperator.And)
            TokenType.OrEquals -> makeAssignExpr(BinaryOperator.Or)
            TokenType.CoalesceEquals -> makeAssignExpr(BinaryOperator.Coalesce)
            TokenType.Period -> {
                consume()
                if (!matchIdentifierName())
                    reporter.expected("identifier", tokenType)
                MemberExpressionNode(
                    lhs,
                    nps { parseIdentifierName() },
                    MemberExpressionNode.Type.NonComputed
                ).withPosition(lhs.sourceStart, lastConsumedToken.end)
            }
            TokenType.OpenBracket -> {
                consume()
                val rhs = parseExpression(0)
                consume(TokenType.CloseBracket)
                return MemberExpressionNode(
                    lhs,
                    rhs,
                    MemberExpressionNode.Type.Computed,
                ).withPosition(lhs.sourceStart, lastConsumedToken.end)
            }
            TokenType.Inc -> {
                consume()
                UpdateExpressionNode(lhs, isIncrement = true, isPostfix = true)
                    .withPosition(lhs.sourceStart, lastConsumedToken.end)
            }
            TokenType.Dec -> {
                consume()
                UpdateExpressionNode(lhs, isIncrement = false, isPostfix = true)
                    .withPosition(lhs.sourceStart, lastConsumedToken.end)
            }
            TokenType.QuestionMark -> parseConditional(lhs).withPosition(lhs.sourceStart, lastConsumedToken.end)
            else -> unreachable()
        }
    }

    private fun parseConditional(lhs: ExpressionNode): ExpressionNode = nps {
        consume(TokenType.QuestionMark)
        val ifTrue = parseExpression(2)
        consume(TokenType.Colon)
        val ifFalse = parseExpression(2)
        ConditionalExpressionNode(lhs, ifTrue, ifFalse)
    }

    private fun parseCallExpression(lhs: ExpressionNode): ExpressionNode = nps {
        CallExpressionNode(lhs, parseArguments())
    }

    private fun parseNewExpression(): ExpressionNode = nps {
        consume(TokenType.New)
        val target = parseExpression(TokenType.New.operatorPrecedence, false, setOf(TokenType.OpenParen))
        NewExpressionNode(target, parseArguments())
    }

    private fun parseArguments(): ArgumentList = nps {
        consume(TokenType.OpenParen)

        val arguments = mutableListOf<ArgumentNode>()

        while (tokenType.isExpressionToken || match(TokenType.TriplePeriod)) {
            val start = sourceStart
            val isSpread = if (match(TokenType.TriplePeriod)) {
                consume()
                true
            } else false
            val node = nps {
                ArgumentNode(parseExpression(2), isSpread)
                    .withPosition(start, lastConsumedToken.end)
            }
            arguments.add(node)
            if (!match(TokenType.Comma))
                break
            consume()
        }

        consume(TokenType.CloseParen)

        ArgumentList(arguments)
    }

    private fun parseYieldExpression(): ExpressionNode = nps {
        expect(inYieldContext)

        consume(TokenType.Yield)
        val isYieldStar = if (match(TokenType.Mul)) {
            consume()
            true
        } else false

        if (match(TokenType.Semicolon)) {
            consume()
            return@nps YieldExpressionNode(null, isYieldStar)
        }

        if (token.afterNewline)
            return@nps YieldExpressionNode(null, isYieldStar)

        if (tokenType.isExpressionToken)
            return@nps YieldExpressionNode(parseExpression(0), isYieldStar)

        reporter.expected("expression", tokenType)
    }

    private fun parsePrimaryExpression(): ExpressionNode = nps {
        if (tokenType.isUnaryToken)
            return@nps parseUnaryExpression()

        when (tokenType) {
            TokenType.OpenParen -> {
                val cpeaapl = parseCPEAAPLNode()
                if (match(TokenType.Arrow)) {
                    tryParseArrowFunction(cpeaapl)
                } else CPEAAPLVisitor(this, cpeaapl).parseAsParenthesizedExpression()
            }
            TokenType.This -> {
                consume()
                ThisLiteralNode()
            }
            TokenType.Class -> TODO()
            TokenType.Super -> TODO()
            TokenType.Identifier -> {
                if (peek().type == TokenType.Arrow)
                    TODO()
                parseIdentifierReference()
            }
            TokenType.NumericLiteral -> parseNumericLiteral()
            TokenType.BigIntLiteral -> TODO()
            TokenType.True -> {
                consume()
                TrueNode()
            }
            TokenType.False -> {
                consume()
                FalseNode()
            }
            TokenType.Function -> parseFunctionExpression()
            TokenType.StringLiteral -> StringLiteralNode(token.literals).also { consume() }
            TokenType.NullLiteral -> {
                consume()
                NullLiteralNode()
            }
            TokenType.OpenCurly -> parseObjectLiteral()
            TokenType.OpenBracket -> parseArrayLiteral()
            TokenType.RegExpLiteral -> parseRegExpLiteral()
            TokenType.TemplateLiteralStart -> parseTemplateLiteral()
            TokenType.New -> parseNewExpression()
            TokenType.Yield -> parseYieldExpression()
            else -> reporter.expected("primary expression", tokenType)
        }
    }

    private fun parseRegExpLiteral(): RegExpLiteralNode = nps {
        val source = token.literals
        consume(TokenType.RegExpLiteral)

        val flags = if (match(TokenType.RegexFlags)) {
            token.literals.also { consume() }
        } else ""

        RegExpLiteralNode(source, flags)
    }

    private fun parseTemplateLiteral(): ExpressionNode = nps {
        consume(TokenType.TemplateLiteralStart)

        val expressions = mutableListOf<ExpressionNode>()
        fun addEmptyString() {
            expressions.add(StringLiteralNode("").withPosition(sourceStart, sourceStart))
        }

        if (!match(TokenType.TemplateLiteralString))
            addEmptyString()

        while (!isDone && !matchAny(TokenType.TemplateLiteralEnd, TokenType.UnterminatedTemplateLiteral)) {
            if (match(TokenType.TemplateLiteralString)) {
                nps {
                    StringLiteralNode(token.literals).also { consume() }
                }.also(expressions::add)
            } else if (match(TokenType.TemplateLiteralExprStart)) {
                consume()
                if (match(TokenType.TemplateLiteralExprEnd))
                    reporter.emptyTemplateLiteralExpr()

                expressions.add(parseExpression(0))
                if (!match(TokenType.TemplateLiteralExprEnd))
                    reporter.unterminatedTemplateLiteralExpr()
                consume()
                if (!match(TokenType.TemplateLiteralString))
                    addEmptyString()
            } else {
                reporter.expected("template literal string or expression", tokenType)
            }
        }

        if (match(TokenType.UnterminatedTemplateLiteral))
            reporter.unterminatedTemplateLiteral()
        consume()

        TemplateLiteralNode(expressions)
    }

    /*
     * ObjectLiteral :
     *     { }
     *     { PropertyDefinitionList }
     *     { PropertyDefinitionList , }
     *
     * PropertyDefinitionList :
     *     PropertyDefinition
     *     PropertyDefinitionList , PropertyDefinition
     *
     * Note that this is NOT a covered object literal. I.e., this will
     * not work for parsing destructured parameters in the future. This
     * is only for contexts where we know it is an object literal.
     */
    private fun parseObjectLiteral(): ExpressionNode = nps {
        val objectStart = sourceStart
        consume(TokenType.OpenCurly)

        val propertiesStart = sourceStart
        val properties = mutableListOf<Property>()

        while (!match(TokenType.CloseCurly)) {
            properties.add(parseObjectProperty())
            if (match(TokenType.Comma)) {
                consume()
            } else break
        }

        val list = PropertyDefinitionList(properties).withPosition(propertiesStart, lastConsumedToken.end)
        consume(TokenType.CloseCurly)

        return ObjectLiteralNode(list).withPosition(objectStart, lastConsumedToken.end)
    }

    /*
     * PropertyDefinition :
     *     IdentifierReference
     *     CoverInitializedName
     *     PropertyName : AssignmentExpression
     *     MethodDefinition
     *     ... AssignmentExpression
     */
    private fun parseObjectProperty(): Property = nps {
        if (match(TokenType.TriplePeriod)) {
            consume()
            return@nps SpreadProperty(parseExpression(2))
        }

        val name = parsePropertyName()

        if (matchPropertyName() || match(TokenType.OpenParen)) {
            val (type, needsNewName) = if (matchPropertyName()) {
                if (name.type != PropertyName.Type.Identifier)
                    reporter.unexpectedToken(tokenType)

                val identifier = (name.expression as IdentifierNode).identifierName
                if (identifier != "get" && identifier != "set")
                    reporter.unexpectedToken(tokenType)

                val type = if (identifier == "get") {
                    MethodDefinitionNode.Kind.Getter
                } else MethodDefinitionNode.Kind.Setter

                type to true
            } else MethodDefinitionNode.Kind.Normal to false

            val methodName = if (needsNewName) parsePropertyName() else name

            val params = parseFunctionParameters()

            // TODO: Async/Generator methods
            val body = parseFunctionBody(isAsync = false, isGenerator = false)
            return@nps MethodProperty(MethodDefinitionNode(methodName, params, body, type))
        }

        if (matchAny(TokenType.Comma, TokenType.CloseCurly)) {
            if (name.type != PropertyName.Type.Identifier)
                reporter.at(name).invalidShorthandProperty()

            val identifier = (name.expression as IdentifierNode).identifierName
            val node = IdentifierReferenceNode(identifier).withPosition(name)

            return@nps ShorthandProperty(node)
        }

        consume(TokenType.Colon)
        val expression = parseExpression(2)

        KeyValueProperty(name, expression)
    }

    private fun matchPropertyName() = matchIdentifierName() ||
        matchAny(TokenType.OpenBracket, TokenType.StringLiteral, TokenType.NumericLiteral)

    /*
     * PropertyName :
     *     LiteralPropertyName
     *     ComputedPropertyName
     *
     * LiteralPropertyName :
     *     IdentifierName
     *     StringLiteral
     *     NumericLiteral
     *
     * ComputedPropertyName :
     *     [ AssignmentExpression ]
     */
    private fun parsePropertyName(): PropertyName = nps {
        when {
            match(TokenType.OpenBracket) -> {
                consume()
                val expr = parseExpression(0)
                consume(TokenType.CloseBracket)
                return@nps PropertyName(expr, PropertyName.Type.Computed)
            }
            match(TokenType.StringLiteral) -> {
                PropertyName(nps {
                    StringLiteralNode(token.literals).also { consume() }
                }, PropertyName.Type.String)
            }
            match(TokenType.NumericLiteral) -> {
                PropertyName(parseNumericLiteral(), PropertyName.Type.Number)
            }
            matchIdentifierName() -> {
                PropertyName(parseIdentifierName(), PropertyName.Type.Identifier)
            }
            else -> reporter.unexpectedToken(tokenType)
        }
    }

    private fun matchIdentifierName(): Boolean {
        return match(TokenType.Identifier) || tokenType.category == TokenType.Category.Keyword
    }

    private fun parseIdentifierName(): IdentifierNode = nps {
        val identifier = token.literals
        consume()
        IdentifierNode(identifier)
    }

    /*
     * ArrayLiteral :
     *     [ Elision? ]
     *     [ ElementList ]
     *     [ ElementList , Elision? ]
     *
     * ElementList :
     *     Elision? AssignmentExpression
     *     Elision? SpreadElement
     *     ElementList , Elision? AssignmentExpression
     *     ElementList , Elision? SpreadElement
     *
     * Elision :
     *     ,
     *     Elision ,
     *
     * SpreadElement :
     *     ... AssignmentExpression
     */
    private fun parseArrayLiteral(): ArrayLiteralNode = nps {
        consume(TokenType.OpenBracket)
        if (match(TokenType.CloseBracket)) {
            consume()
            return@nps ArrayLiteralNode(emptyList())
        }

        val elements = mutableListOf<ArrayElementNode>()

        while (!match(TokenType.CloseBracket)) {
            while (match(TokenType.Comma)) {
                elements.add(nps {
                    consume()
                    ArrayElementNode(null, ArrayElementNode.Type.Elision)
                })
            }

            nps {
                val isSpread = if (match(TokenType.TriplePeriod)) {
                    consume()
                    true
                } else false

                if (!tokenType.isExpressionToken)
                    reporter.expected("expression", tokenType)

                val expression = parseExpression(2)

                ArrayElementNode(
                    expression, if (isSpread) {
                        ArrayElementNode.Type.Spread
                    } else ArrayElementNode.Type.Normal
                )
            }.also(elements::add)

            if (match(TokenType.Comma)) {
                consume()
            } else if (!match(TokenType.CloseBracket)) {
                break
            }
        }

        consume(TokenType.CloseBracket)
        ArrayLiteralNode(elements)
    }

    private fun parseNumericLiteral(): NumericLiteralNode = nps {
        val numericToken = token
        val value = token.literals
        consume(TokenType.NumericLiteral)

        if (value.length >= 2 && value[0] == '0' && value[1].isDigit() && isStrict)
            reporter.strictImplicitOctal()

        if (matchIdentifier()) {
            val nextToken = peek()
            if (!nextToken.afterNewline && numericToken.end.column == nextToken.start.column - 1)
                reporter.identifierAfterNumericLiteral()
        }

        NumericLiteralNode(numericToken.doubleValue())
    }

    private fun parseCPEAAPLNode(): CPEAAPLNode = nps {
        val prevDisableScoping = disableAutoScoping
        disableAutoScoping = true

        consume(TokenType.OpenParen)
        val parts = mutableListOf<CPEAAPLPart>()
        var endsWithComma = true

        while (tokenType.isExpressionToken || match(TokenType.TriplePeriod)) {
            var isSpread = false

            nps {
                isSpread = if (match(TokenType.TriplePeriod)) {
                    consume()
                    true
                } else false

                val expression = parseExpression(2)
                CPEAAPLPart(expression, isSpread)
            }.also(parts::add)

            if (isSpread)
                break

            if (!match(TokenType.Comma)) {
                endsWithComma = false
                break
            }

            consume()
        }

        consume(TokenType.CloseParen)
        disableAutoScoping = prevDisableScoping

        CPEAAPLNode(parts, endsWithComma)
    }

    private fun tryParseArrowFunction(node: CPEAAPLNode): ArrowFunctionNode = nps {
        consume(TokenType.Arrow)

        if (token.afterNewline)
            reporter.arrowFunctionNewLine()

        val parameters = CPEAAPLVisitor(this, node).parseAsParameterList()

        // TODO: Async/Generator methods
        val body = parseFunctionBody(isAsync = false, isGenerator = false)

        ArrowFunctionNode(parameters, body, Operations.FunctionKind.Normal)
    }

    private fun parseUnaryExpression(): ExpressionNode = nps {
        val type = consume()
        val expression = parseExpression(type.operatorPrecedence, type.leftAssociative)

        when (type) {
            TokenType.Inc -> UpdateExpressionNode(expression, isIncrement = true, isPostfix = false)
            TokenType.Dec -> UpdateExpressionNode(expression, isIncrement = false, isPostfix = false)
            TokenType.Not -> UnaryExpressionNode(expression, UnaryOperator.Not)
            TokenType.BitwiseNot -> UnaryExpressionNode(expression, UnaryOperator.BitwiseNot)
            TokenType.Add -> UnaryExpressionNode(expression, UnaryOperator.Plus)
            TokenType.Sub -> UnaryExpressionNode(expression, UnaryOperator.Minus)
            TokenType.Typeof -> UnaryExpressionNode(expression, UnaryOperator.Typeof)
            TokenType.Void -> UnaryExpressionNode(expression, UnaryOperator.Void)
            TokenType.Delete -> UnaryExpressionNode(expression, UnaryOperator.Delete)
            else -> unreachable()
        }
    }

    private fun parseIfStatement(): StatementNode = nps {
        consume(TokenType.If)
        consume(TokenType.OpenParen)
        val condition = parseExpression()
        consume(TokenType.CloseParen)

        val trueBlock = parseStatement()

        if (!match(TokenType.Else))
            return@nps IfStatementNode(condition, trueBlock, null)

        consume()
        if (match(TokenType.If))
            return@nps IfStatementNode(condition, trueBlock, parseIfStatement())

        val falseBlock = parseStatement()

        return@nps IfStatementNode(condition, trueBlock, falseBlock)
    }

    private fun parseFunctionBody(isAsync: Boolean, isGenerator: Boolean): BlockNode {
        labelStateStack.add(LabelState())
        val previousFunctionCtx = inFunctionContext
        val previousYieldCtx = inYieldContext
        val previewAsyncCtx = inAsyncContext
        val previousDefaultCtx = inDefaultContext

        inFunctionContext = true
        inYieldContext = isGenerator
        inAsyncContext = isAsync
        inDefaultContext = false

        val result = parseBlock(isFunctionBlock = true)

        inFunctionContext = previousFunctionCtx
        inYieldContext = previousYieldCtx
        inAsyncContext = previewAsyncCtx
        inDefaultContext = previousDefaultCtx
        labelStateStack.removeLast()

        return result
    }

    // Helper for setting source positions of AST. Stands for
    // node parsing scope; the name is short to prevent long
    // non-local return labels (i.e. "return@nodeParsingScope")
    private inline fun <T : ASTNode?> nps(block: () -> T): T {
        val start = sourceStart
        val node = block()
        if (node == null)
            return node
        node.sourceStart = start
        node.sourceEnd = lastConsumedToken.end
        return node
    }

    private fun match(vararg types: TokenType): Boolean {
        expect(types.isNotEmpty())
        if (tokenType != types[0])
            return false

        ensureCachedTokenCount(types.size - 1)
        return types.drop(1).withIndex().all { (index, type) ->
            receivedTokenList[index].type == type
        }
    }

    private fun matchAny(vararg types: TokenType): Boolean {
        ensureCachedTokenCount(1)
        return types.any { it == tokenType }
    }

    private fun peek(n: Int = 1): Token {
        if (n == 0)
            return token
        ensureCachedTokenCount(n)
        return receivedTokenList[n - 1]
    }

    private fun consume(): TokenType {
        val old = tokenType
        lastConsumedToken = token
        token = if (receivedTokenList.isNotEmpty()) {
            receivedTokenList.removeFirst()
        } else tokenQueue.take()
        token.error()?.also {
            throw ParsingException(it, token.start, token.end)
        }
        return old
    }

    private fun consume(type: TokenType) {
        if (tokenType != type)
            reporter.expected(type, tokenType)
        consume()
    }

    private fun ensureCachedTokenCount(n: Int) {
        repeat(n - receivedTokenList.size) {
            receivedTokenList.addLast(tokenQueue.take())
        }
    }

    class ParsingException(
        message: String,
        val start: TokenLocation,
        val end: TokenLocation,
    ) : Throwable(message)

    enum class LabellableBlockType {
        Loop,
        Switch,
        Block,
    }

    data class Block(val type: LabellableBlockType, val labels: Set<String>)

    inner class LabelState {
        private val blocks = mutableListOf<Block>()

        fun pushBlock(type: LabellableBlockType, labels: Set<String>) {
            blocks.add(Block(type, labels))
        }

        fun popBlock() {
            blocks.removeLast()
        }

        fun validateBreak(breakToken: Token, identifierToken: Token?) {
            if (identifierToken != null) {
                val identifier = identifierToken.literals
                if (!blocks.any { identifier in it.labels })
                    reporter.at(identifierToken).invalidBreakTarget(identifier)
            } else if (blocks.none { it.type != LabellableBlockType.Block }) {
                reporter.at(breakToken).breakOutsideOfLoopOrSwitch()
            }
        }

        fun validateContinue(continueToken: Token, identifierToken: Token?) {
            if (identifierToken != null) {
                val identifier = identifierToken.literals
                val matchingBlocks = blocks.filter { identifier in it.labels }
                if (matchingBlocks.none { it.type == LabellableBlockType.Loop })
                    reporter.at(identifierToken).invalidContinueTarget(identifier)
            } else if (blocks.none { it.type != LabellableBlockType.Block }) {
                reporter.at(continueToken).continueOutsideOfLoop()
            }
        }

    }

    class LabelStack {
        private val stack = Stack<State>()

        init {
            stack.push(State())
        }

        // Returns false if the label already exists, true otherwise
        fun addBreakLabel(label: String): Boolean {
            return stack.peek().breakableLabels.add(label)
        }

        // Returns false if the label already exists, true otherwise
        fun addContinueLabel(label: String): Boolean {
            return stack.peek().continuableLabels.add(label)
        }

        fun isBreakLabel(label: String) = label in stack.peek().breakableLabels

        fun isContinueLabel(label: String) = label in stack.peek().continuableLabels

        fun pushFunctionBoundary() {
            stack.push(State())
        }

        fun popFunctionBoundary() {
            stack.pop()
        }

        data class Label(val name: String, val type: Type) {
            enum class Type {

            }
        }

        data class State(
            val breakableLabels: MutableSet<String> = mutableSetOf(),
            val continuableLabels: MutableSet<String> = mutableSetOf(),
        )
    }
}
