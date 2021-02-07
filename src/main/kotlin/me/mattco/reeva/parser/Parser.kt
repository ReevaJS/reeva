package me.mattco.reeva.parser

import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable
import java.io.File
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

// https://gist.github.com/olegcherr/b62a09aba1bff643a049
fun simpleMeasureTest(
    ITERATIONS: Int = 1000,
    TEST_COUNT: Int = 10,
    WARM_COUNT: Int = 2,
    callback: ()->Unit
) {
    val results = ArrayList<Long>()
    var totalTime = 0L
    var t = 0

    println("$PRINT_REFIX -> go")

    while (++t <= TEST_COUNT + WARM_COUNT) {
        val startTime = System.currentTimeMillis()

        var i = 0
        while (i++ < ITERATIONS)
            callback()

        if (t <= WARM_COUNT) {
            println("$PRINT_REFIX Warming $t of $WARM_COUNT")
            continue
        }

        val time = System.currentTimeMillis() - startTime
        println(PRINT_REFIX+" "+time.toString()+"ms")

        results.add(time)
        totalTime += time
    }

    results.sort()

    val average = totalTime / TEST_COUNT
    val median = results[results.size / 2]

    println("$PRINT_REFIX -> average=${average}ms / median=${median}ms")
}

/**
 * Used to filter console messages easily
 */
private val PRINT_REFIX = "[TimeTest]"

fun main() {
    val source = File("./demo/test262.js").readText()

    try {
        simpleMeasureTest(30, 20, 10) {
            Parser(source).parseScript()
        }
    } catch (e: Parser.ParsingException) {
        println("ERROR (${e.start.line + 1}:${e.start.column + 1} - ${e.end.line + 1}:${e.end.column + 1}) ${e.message}")
        e.printStackTrace()
    } finally {
        Reeva.teardown()
    }
}

class Parser(val source: String) {
    private var inDefaultContext = false
    private var inFunctionContext = false
    private var inYieldContext = false
    private var inAsyncContext = false

    private var inContinueContext = false
    private var inBreakContext = false
    private val labelStack = LabelStack()

    private val tokenQueue = LinkedBlockingQueue<Token>()
    private val receivedTokenList = LinkedList<Token>()

    var token: Token = Token.INVALID

    val tokenType: TokenType
        get() = token.type
    val isDone: Boolean
        get() = token === Token.INVALID

    val sourceStart: TokenLocation
        get() = token.start
    val sourceEnd: TokenLocation
        get() = token.end

    private lateinit var scope: Scope

    private fun initLexer() {
        Reeva.threadPool.submit {
            val lexer = Lexer(source)
            while (!lexer.isDone)
                tokenQueue.add(lexer.nextToken())
            tokenQueue.add(Token.EOF)
        }
        consume()
    }

    @Throws(ParsingException::class)
    fun parseScript(): ScriptNode {
        initLexer()

        val globalScope = HoistingScope()
        scope = globalScope

        globalScope.hasUseStrictDirective = checkForAndConsumeUseStrict()

        val script = parseScriptImpl()
        script.scope = globalScope

        return script
    }

    @Throws(ParsingException::class)
    fun parseModule(): ModuleNode {
        TODO()
    }

    private fun parseScriptImpl(): ScriptNode = nps {
        // Script :
        //     ScriptBody?
        //
        // ScriptBody :
        //     StatementList

        return ScriptNode(parseStatementList())
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
    private fun parseStatement(): StatementNode {
        return when (tokenType) {
            TokenType.OpenCurly -> parseBlock()
            TokenType.Var, TokenType.Let, TokenType.Const -> parseVariableDeclaration(false)
            TokenType.Semicolon -> nps {
                consume()
                EmptyStatementNode()
            }
            TokenType.If -> parseIfStatement()
            TokenType.Do -> parseDoWhileStatement()
            TokenType.While -> parseWhileStatement()
            TokenType.For -> parseNormalForAndForEachStatement()
            TokenType.Switch -> parseSwitchStatement()
            TokenType.Continue -> parseContinueStatement()
            TokenType.Break -> parseBreakStatement()
            TokenType.Return -> parseReturnStatement()
            TokenType.With -> parseWithStatement()
            TokenType.Throw -> parseThrowStatement()
            TokenType.Try -> parseTryStatement()
            TokenType.Debugger -> parseDebuggerStatement()
            TokenType.Function, TokenType.Async -> parseFunctionDeclaration()
            TokenType.Class -> parseClassDeclaration()
            else -> {
                if (matchIdentifier()) {
                    val labelledStatement = tryParseLabelledStatement()
                    if (labelledStatement != null)
                        return labelledStatement
                }
                if (tokenType.isExpressionToken) {
                    if (match(TokenType.Function))
                        reportError(ParserErrors.FunctionInExpressionContext)
                    return nps {
                        ExpressionStatementNode(parseExpression().also { asi() })
                    }
                }
                reportError(ParserErrors.Expected("statement", tokenType.string))
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

        reportError(ParserErrors.ExpectedToken(TokenType.Semicolon, tokenType))
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
        val type = when (consume()) {
            TokenType.Var -> Variable.Type.Var
            TokenType.Let -> Variable.Type.Let
            TokenType.Const -> Variable.Type.Const
            else -> unreachable()
        }

        val declarations = mutableListOf<Declaration>()

        while (true) {
            if (match(TokenType.OpenCurly))
                TODO()

            val identifier = parseBindingIdentifier()
            if (match(TokenType.Equals)) {
                consume()
                declarations.add(Declaration(identifier, parseExpression(2)))
            } else {
                if (!isForEachLoop && type == Variable.Type.Const)
                    reportError(ParserErrors.ConstMissingInitializer)
                declarations.add(Declaration(identifier, null))
            }

            if (match(TokenType.Comma)) {
                consume()
            } else break
        }

        if (!isForEachLoop)
            asi()

        return if (type == Variable.Type.Var) {
            VariableDeclarationNode(declarations)
        } else {
            LexicalDeclarationNode(isConst = type == Variable.Type.Const, declarations)
        }
    }

    /*
     * DoWhileStatement :
     *     do Statement while ( Expression ) ;
     */
    private fun parseDoWhileStatement(): StatementNode = nps {
        inBreakContinueContext {
            consume(TokenType.Do)
            val statement = parseStatement()
            consume(TokenType.While)
            consume(TokenType.OpenParen)
            val condition = parseExpression()
            consume(TokenType.CloseParen)
            asi()
            DoWhileStatementNode(condition, statement)
        }
    }

    /*
     * WhileStatement :
     *     while ( Expression ) Statement
     */
    private fun parseWhileStatement(): StatementNode = nps {
        inBreakContinueContext {
            consume(TokenType.While)
            consume(TokenType.OpenParen)
            val condition = parseExpression(0)
            consume(TokenType.CloseParen)
            val statement = parseStatement()
            asi()
            WhileStatementNode(condition, statement)
        }
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
        inBreakContinueContext(isContinue = false) {
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
    }

    /*
     * ContinueStatement :
     *     continue ;
     *     continue [no LineTerminator here] LabelIdentifier ;
     */
    private fun parseContinueStatement(): StatementNode = nps {
        if (!inContinueContext)
            reportError(ParserErrors.ContinueOutsideOfLoop)

        consume(TokenType.Continue)

        if (match(TokenType.Semicolon)) {
            consume()
            return@nps ContinueStatementNode(null)
        }

        if (token.afterNewline)
            return@nps ContinueStatementNode(null)

        if (matchIdentifier()) {
            val identifier = parseIdentifier()
            if (!labelStack.isContinueLabel(identifier))
                reportError(ParserErrors.InvalidContinueTarget(identifier))
            return@nps ContinueStatementNode(identifier).also { asi() }
        }

        reportError(ParserErrors.Expected("identifier", tokenType.string))
    }

    /*
     * BreakStatement :
     *     break ;
     *     break [no LineTerminator here] LabelIdentifier ;
     */
    private fun parseBreakStatement(): StatementNode = nps {
        if (!inBreakContext)
            reportError(ParserErrors.BreakOutsideOfLoopOrSwitch)

        consume(TokenType.Break)

        if (match(TokenType.Semicolon)) {
            consume()
            return@nps BreakStatementNode(null)
        }

        if (token.afterNewline)
            return@nps BreakStatementNode(null)

        if (matchIdentifier()) {
            val identifier = parseIdentifier()
            if (!labelStack.isBreakLabel(identifier))
                reportError(ParserErrors.InvalidBreakTarget(identifier))
            return@nps BreakStatementNode(identifier).also { asi() }
        }

        reportError(ParserErrors.Expected("identifier", tokenType.string))
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

        reportError(ParserErrors.Expected("expression", tokenType.string))
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
            reportError(ParserErrors.Expected("expression", tokenType.string))
        if (token.afterNewline)
            reportError(ParserErrors.ThrowStatementNewLine)
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

        if (catchBlock != null && finallyBlock != null)
            reportError(ParserErrors.ExpectedToken(TokenType.Finally, tokenType))

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

    /*
     * LabelledStatement :
     *     LabelIdentifier : LabelledItem
     *
     * LabelIdentifier :
     *     Identifier
     *     [~Yield] yield
     *     [~Await] await
     *
     * LabelledItem :
     *     Statement
     *     FunctionDeclaration
     *
     * TODO: FunctionDeclaration is included specifically in the
     * LabelledItem production as it has some specific functionality
     */
    private fun tryParseLabelledStatement(): StatementNode? = nps<StatementNode?> {
        expect(matchIdentifier())
        if (peek().type != TokenType.Colon)
            return@nps null

        // We parse multiple labels in a row here
        val labels = mutableListOf<String>()

        while (matchIdentifier() && peek().type == TokenType.Colon) {
            labels.add(parseIdentifier())
            consume(TokenType.Colon)
        }

        var isBreakLabel = false
        var isContinueLabel = false

        if (matchAny(TokenType.Switch, TokenType.OpenCurly, TokenType.If, TokenType.Try))
            isBreakLabel = true

        if (matchAny(TokenType.Do, TokenType.While, TokenType.For)) {
            isBreakLabel = true
            isContinueLabel = true
        }

        if (isBreakLabel)
            labels.forEach(labelStack::addBreakLabel)
        if (isContinueLabel)
            labels.forEach(labelStack::addContinueLabel)

        LabelledStatementNode(labels, parseStatement())
    }

    private fun matchSwitchClause() = matchAny(TokenType.Case, TokenType.Default)

    private fun parseNormalForAndForEachStatement(): StatementNode = nps {
        consume(TokenType.For)
        if (match(TokenType.Await))
            TODO()

        consume(TokenType.OpenParen)

        var initializer: StatementNode? = null
        var initRequiresOwnScope = false

        if (!match(TokenType.Semicolon)) {
            if (tokenType.isExpressionToken) {
                initializer = nps { ExpressionStatementNode(parseExpression(0, false, setOf(TokenType.In))) }
                if (matchForEach())
                    return parseForEachStatement(initializer)
            } else if (tokenType.isVariableDeclarationToken) {
                if (match(TokenType.Var)) {
                    initRequiresOwnScope = true
                    scope = HoistingScope(scope)
                }
                initializer = parseVariableDeclaration(isForEachLoop = true)
                if (matchForEach())
                    return parseForEachStatement(initializer).also { scope = scope.outer!! }
            } else {
                reportError(ParserErrors.UnexpectedToken(tokenType))
            }
        }

        consume(TokenType.Semicolon)
        val condition = if (match(TokenType.Semicolon)) null else parseExpression(0)

        consume(TokenType.Semicolon)
        val update = if (match(TokenType.Semicolon)) null else parseExpression(0)

        consume(TokenType.CloseParen)

        val body = inBreakContinueContext {
            parseStatement()
        }

        if (initRequiresOwnScope)
            scope = scope.outer!!

        return ForStatementNode(initializer, condition, update, body)
    }

    private fun parseForEachStatement(initializer: ASTNode): StatementNode = nps {
        if ((initializer is VariableDeclarationNode && initializer.declarations.size > 1) ||
            (initializer is LexicalDeclarationNode && initializer.declarations.size > 1)
        ) {
            reportError(ParserErrors.ForEachMultipleDeclarations)
        }

        val isIn = consume() == TokenType.In
        val rhs = parseExpression(0)
        consume(TokenType.CloseParen)


        val body = inBreakContinueContext {
            parseStatement()
        }

        if (isIn) {
            ForInNode(initializer, rhs, body)
        } else {
            ForOfNode(initializer, rhs, body)
        }
    }

    fun matchForEach() = match(TokenType.In) || (match(TokenType.Identifier) && token.literals == "of")

    /*
     * FunctionDeclaration :
     *     function BindingIdentifier ( FormalParameters ) { FunctionBody }
     *     [+Default] function ( FormalParameters ) { FunctionBody }
     */
    private fun parseFunctionDeclaration(): StatementNode = nps {
        val (identifier, params, body) = parseFunctionHelper(isDeclaration = true)
        FunctionDeclarationNode(identifier, params, body)
    }

    /*
     * FunctionExpression :
     *     function BindingIdentifier? ( FormalParameters ) { FunctionBody }
     */
    private fun parseFunctionExpression(): ExpressionNode = nps {
        val (identifier, params, body) = parseFunctionHelper(isDeclaration = false)
        FunctionExpressionNode(identifier, params, body)
    }

    data class FunctionTemp(
        val identifier: BindingIdentifierNode?,
        val params: ParameterList,
        val body: BlockNode,
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

        if (isGenerator || isAsync)
            TODO()

        val identifier = when {
            matchIdentifier() -> parseBindingIdentifier()
            isDeclaration && !inDefaultContext -> reportError(ParserErrors.FunctionStatementNoName)
            else -> null
        }

        val newScope = HoistingScope(scope)
        scope = newScope
        val params = parseFunctionParameters()

        // TODO: Static Semantics
        // TODO: Check params initializers for possible direct eval call

        val body = functionBoundary(isAsync, isGenerator) {
            parseBlock(isFunctionBlock = true)
        }

        FunctionTemp(identifier, params, body)
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
                reportError(ParserErrors.ClassDeclarationNoName)
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

    private fun initializeParamVariables(parameters: ParameterList) {
        for (parameter in parameters) {
            val variable = Variable(
                parameter.identifier.identifierName,
                Variable.Type.Var,
                Variable.Mode.Parameter
            )
            parameter.variable = variable
            scope.addDeclaredVariable(variable)
        }
    }

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
            return ParameterList()
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
                        reportError(ParserErrors.ParamAfterRest)
                    Parameter(identifier, null, true)
                }.also(parameters::add)
                break
            } else if (!matchIdentifier()) {
                reportError(ParserErrors.Expected("expression", tokenType.string))
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
        return ParameterList(parameters).also(::initializeParamVariables)
    }

    private fun matchIdentifier() = match(TokenType.Identifier) ||
            (!inYieldContext && !scope.isStrict && match(TokenType.Yield)) ||
            (!inAsyncContext && !scope.isStrict && match(TokenType.Await))

    private fun parseIdentifier(): String {
        expect(matchAny(TokenType.Identifier, TokenType.Await, TokenType.Yield))
        val identifier = token.literals
        consume()
        return identifier
    }

    private fun parseIdentifierReference(): IdentifierReferenceNode = nps {
        IdentifierReferenceNode(parseIdentifier()).also {
            scope.addReference(it)
        }
    }

    private fun parseBindingIdentifier(): BindingIdentifierNode = nps {
        BindingIdentifierNode(parseIdentifier())
    }

    private fun checkForAndConsumeUseStrict(): Boolean {
        return if (match(TokenType.StringLiteral) && token.literals == "use strict") {
            consume()
            true
        } else false
    }

    private fun parseBlock(isFunctionBlock: Boolean = false): BlockNode = nps {
        consume(TokenType.OpenCurly)
        if (!isFunctionBlock)
            scope = Scope(scope)

        val statements = parseStatementList()
        consume(TokenType.CloseCurly)
        BlockNode(statements).also {
            if (!isFunctionBlock)
                scope = scope.outer!!
        }
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
            while (match(TokenType.Comma))
                expressions.add(parseExpression(2))
            return CommaExpressionNode(expressions)
        }

        return expression
    }

    private fun parseSecondaryExpression(
        lhs: ExpressionNode,
        minPrecedence: Int,
        leftAssociative: Boolean,
    ): ExpressionNode {
        fun makeBinaryExpr(op: BinaryOperator): ExpressionNode {
            consume()
            return BinaryExpressionNode(lhs, parseExpression(minPrecedence, leftAssociative), op)
                .withPosition(lhs.sourceStart, sourceEnd)
        }

        fun makeAssignExpr(op: AssignmentOperator): ExpressionNode {
            consume()
            if (lhs !is IdentifierReferenceNode && lhs !is MemberExpressionNode && lhs !is CallExpressionNode)
                reportError(ParserErrors.InvalidLhsInAssignment)
            if (scope.isStrict && lhs is IdentifierReferenceNode) {
                val name = lhs.identifierName
                if (name == "eval")
                    reportError(ParserErrors.StrictAssignToEval)
                if (name == "arguments")
                    reportError(ParserErrors.StrictAssignToArguments)
            } else if (scope.isStrict && lhs is CallExpressionNode) {
                reportError(ParserErrors.InvalidLhsInAssignment)
            }

            return AssignmentExpressionNode(lhs, parseExpression(minPrecedence, leftAssociative), op)
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
            TokenType.Equals -> makeAssignExpr(AssignmentOperator.Equals)
            TokenType.MulEquals -> makeAssignExpr(AssignmentOperator.Mul)
            TokenType.DivEquals -> makeAssignExpr(AssignmentOperator.Div)
            TokenType.ModEquals -> makeAssignExpr(AssignmentOperator.Mod)
            TokenType.AddEquals -> makeAssignExpr(AssignmentOperator.Add)
            TokenType.SubEquals -> makeAssignExpr(AssignmentOperator.Sub)
            TokenType.ShlEquals -> makeAssignExpr(AssignmentOperator.Shl)
            TokenType.ShrEquals -> makeAssignExpr(AssignmentOperator.Shr)
            TokenType.UShrEquals -> makeAssignExpr(AssignmentOperator.UShr)
            TokenType.BitwiseAndEquals -> makeAssignExpr(AssignmentOperator.BitwiseAnd)
            TokenType.BitwiseOrEquals -> makeAssignExpr(AssignmentOperator.BitwiseOr)
            TokenType.BitwiseXorEquals -> makeAssignExpr(AssignmentOperator.BitwiseXor)
            TokenType.ExpEquals -> makeAssignExpr(AssignmentOperator.Exp)
            TokenType.AndEquals -> makeAssignExpr(AssignmentOperator.And)
            TokenType.OrEquals -> makeAssignExpr(AssignmentOperator.Or)
            TokenType.CoalesceEquals -> makeAssignExpr(AssignmentOperator.Coalesce)
            TokenType.Period -> {
                consume()
                if (!matchIdentifier())
                    reportError(ParserErrors.Expected("identifier", tokenType.string))
                MemberExpressionNode(lhs, IdentifierNode(parseIdentifier()), MemberExpressionNode.Type.NonComputed)
            }
            TokenType.OpenBracket -> {
                consume()
                return MemberExpressionNode(lhs, parseExpression(0), MemberExpressionNode.Type.Computed).also {
                    consume(TokenType.CloseBracket)
                }
            }
            TokenType.Inc -> {
                consume()
                UpdateExpressionNode(lhs, isIncrement = true, isPostfix = true)
            }
            TokenType.Dec -> {
                consume()
                UpdateExpressionNode(lhs, isIncrement = false, isPostfix = true)
            }
            TokenType.QuestionMark -> parseConditional(lhs)
            else -> unreachable()
        }
    }

    private fun parseConditional(lhs: ExpressionNode): ExpressionNode = nps {
        consume(TokenType.QuestionMark)
        val ifTrue = parseExpression(2)
        consume(TokenType.Colon)
        val ifFalse = parseExpression(2)
        return ConditionalExpressionNode(lhs, ifTrue, ifFalse)
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
            if (match(TokenType.TriplePeriod)) {
                consume()
                arguments.add(ArgumentNode(parseExpression(2), isSpread = true))
            } else {
                arguments.add(ArgumentNode(parseExpression(2), isSpread = false))
            }
            if (!match(TokenType.Comma))
                break
            consume()
        }

        consume(TokenType.CloseParen)

        ArgumentList(arguments)
    }

    private fun parsePrimaryExpression(): ExpressionNode = nps {
        if (tokenType.isUnaryToken)
            return@nps parseUnaryExpression()

        when (tokenType) {
            TokenType.OpenParen -> {
                val cpeaapl = parseCPEAAPLNode()
                tryParseArrowFunction(cpeaapl) ?: cpeaaplToParenthesizedExpression(cpeaapl)
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
            TokenType.RegexLiteral -> TODO()
            TokenType.TemplateLiteralStart -> parseTemplateLiteral()
            TokenType.New -> parseNewExpression()
            else -> reportError(ParserErrors.Expected("primary expression", tokenType.string))
        }
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
                // TODO: This probably isn't correct
                nps {
                    consume()
                    StringLiteralNode(token.literals)
                }.also(expressions::add)
            } else if (match(TokenType.TemplateLiteralExprStart)) {
                consume()
                if (match(TokenType.TemplateLiteralExprEnd))
                    reportError(ParserErrors.EmptyTemplateLiteralExpr)

                expressions.add(parseExpression(0))
                if (!match(TokenType.TemplateLiteralExprEnd))
                    reportError(ParserErrors.UnterminatedTemplateLiteralExpr)
                consume()
                if (!match(TokenType.TemplateLiteralString))
                    addEmptyString()
            } else {
                reportError(ParserErrors.Expected("template literal string or expression", tokenType.string))
            }
        }

        if (match(TokenType.UnterminatedTemplateLiteral))
            reportError(ParserErrors.UnterminatedTemplateLiteral)
        consume()

        TemplateLiteralNode(expressions)
    }
    private fun parseObjectLiteral(): ExpressionNode = nps {
        val coveredObject = parseCoveredObjectLiteral()
        for (property in coveredObject.list) {
            if (property is CoveredInitializerProperty) {
                throw ParsingException(
                    ParserErrors.UnexpectedToken(TokenType.Equals).message,
                    property.sourceStart,
                    property.sourceEnd,
                )
            }
        }

        coveredObject
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
     */
    private fun parseCoveredObjectLiteral(): ObjectLiteralNode = nps {
        consume(TokenType.OpenCurly)
        val properties = mutableListOf<Property>()

        while (!match(TokenType.CloseCurly)) {
            properties.add(parseProperty())
            if (match(TokenType.Comma)) {
                consume()
            } else break
        }

        consume(TokenType.CloseCurly)
        ObjectLiteralNode(PropertyDefinitionList(properties))
    }

    /*
     * PropertyDefinition :
     *     IdentifierReference
     *     CoverInitializedName
     *     PropertyName : AssignmentExpression
     *     MethodDefinition
     *     ... AssignmentExpression
     */
    private fun parseProperty(): Property = nps {
        if (match(TokenType.TriplePeriod)) {
            consume()
            return@nps SpreadProperty(parseExpression(2))
        }

        val name = parsePropertyName()

        if (match(TokenType.Colon)) {
            consume()
            KeyValueProperty(name, parseExpression(2))
        } else if (match(TokenType.Comma) || match(TokenType.CloseCurly)) {
            if (name.isComputed || name.expression !is IdentifierReferenceNode)
                reportError(ParserErrors.UnexpectedToken(tokenType))
            consume()
            ShorthandProperty(name.expression)
        } else if (match(TokenType.Equals)) {
            if (name.isComputed || name.expression !is IdentifierReferenceNode)
                reportError(ParserErrors.UnexpectedToken(tokenType))
            consume()
            CoveredInitializerProperty(name.expression, parseExpression(2))
        } else MethodProperty(parseMethodDefinition(name))
    }

    private fun parseMethodDefinition(propertyName: PropertyName): MethodDefinitionNode = nps {
        val expression = propertyName.expression
        val (isGet, isSet) = if (match(TokenType.Identifier) && !propertyName.isComputed && expression is IdentifierNode) {
            (expression.identifierName == "get") to (expression.identifierName == "set")
        } else false to false

        val name = if (isGet || isSet) {
            parsePropertyName()
        } else propertyName

        // TODO: These are unique parameters
        val parameters = parseFunctionParameters()
        val block = functionBoundary {
            parseBlock(isFunctionBlock = true)
        }

        val type = when {
            isGet -> MethodDefinitionNode.Type.Getter
            isSet -> MethodDefinitionNode.Type.Setter
            else -> MethodDefinitionNode.Type.Normal
        }

        MethodDefinitionNode(name, parameters, block, type)
    }

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
                return@nps PropertyName(expr, true)
            }
            match(TokenType.StringLiteral) -> {
                PropertyName(StringLiteralNode(token.literals), false).also { consume() }
            }
            match(TokenType.NumericLiteral) -> PropertyName(parseNumericLiteral(), false)
            match(TokenType.Identifier) -> PropertyName(IdentifierNode(parseIdentifier()), false)
            else -> reportError(ParserErrors.UnexpectedToken(tokenType))
        }
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
            return ArrayLiteralNode(emptyList())
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
                    reportError(ParserErrors.Expected("expression", tokenType.string))

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

        if (value.length >= 2 && value[0] == '0' && value[1].isDigit() && scope.isStrict)
            reportError(ParserErrors.StrictImplicitOctal)

        if (matchIdentifier()) {
            val nextToken = peek()
            if (!nextToken.afterNewline && numericToken.end.column == nextToken.start.column - 1)
                reportError(ParserErrors.IdentifierAfterNumericLiteral)
        }

        return NumericLiteralNode(numericToken.doubleValue())
    }

    private fun parseCPEAAPLNode(): CPEAAPLNode = nps {
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

        return CPEAAPLNode(parts, endsWithComma)
    }

    private fun tryParseArrowFunction(node: CPEAAPLNode): ArrowFunctionNode? = nps {
        if (!match(TokenType.Arrow))
            return@nps null

        if (token.afterNewline)
            reportError(ParserErrors.ArrowFunctionNewLine)

        // General note: because we initially parse all of the CPEAAPL parts
        // in a regular expression context, any identifier will be
        // IdentifierReferenceNodes instead of BindingIdentifierNodes.

        for (part in node.covered) {
            // TODO: Relax this to ExpressionNode when we add destructuring support
            if (part.node !is IdentifierReferenceNode) {
                throw ParsingException(
                    "expected identifier",
                    part.node.sourceStart,
                    part.node.sourceEnd,
                )
            }
        }

        val indexOfSpread = node.covered.indexOfFirst { it.isSpread }
        if (indexOfSpread != -1 && indexOfSpread != node.covered.size - 1) {
            val target = node.covered[indexOfSpread]
            throw ParsingException(
                ParserErrors.ParamAfterRest.message,
                target.node.sourceStart,
                target.node.sourceEnd,
            )
        }


        // TODO: Validate object cover grammar when that becomes necessary
        val parameters = ParameterList(node.covered.map { (node, isSpread) ->
            val (identifier, initializer) = if (node is AssignmentExpressionNode) {
                if (isSpread) {
                    throw ParsingException(
                        "rest parameter cannot have an initialer",
                        node.sourceStart,
                        node.sourceEnd,
                    )
                }

                if (node.op != AssignmentOperator.Equals) {
                    throw ParsingException(
                        ParserErrors.Expected("equals sign an initializer", node.op.symbol).message,
                        node.sourceStart,
                        node.sourceEnd,
                    )
                }

                node.lhs to node.rhs
            } else node to null

            // TODO: Relax when we support destructuring
            if (identifier !is IdentifierReferenceNode) {
                throw ParsingException(
                    "expected an identifier",
                    node.sourceStart,
                    node.sourceEnd,
                )
            }

            Parameter(BindingIdentifierNode(identifier.identifierName), initializer, isSpread)
        })

        consume()
        val body = parseStatement()

        ArrowFunctionNode(parameters, body)
    }

    private fun cpeaaplToParenthesizedExpression(node: CPEAAPLNode): ParenthesizedExpressionNode = nps {
        if (node.endsWithComma) {
            throw ParsingException(
                ParserErrors.UnexpectedToken(TokenType.Comma).message,
                node.sourceEnd.shiftColumn(-2),
                node.sourceEnd.shiftColumn(-1),
            )
        }

        val parts = node.covered
        for (part in parts) {
            if (part.node !is ExpressionNode) {
                throw ParsingException(
                    "expected expression",
                    part.node.sourceStart,
                    part.node.sourceEnd,
                )
            }

            if (part.isSpread) {
                throw ParsingException(
                    ParserErrors.UnexpectedToken(TokenType.TriplePeriod).message,
                    part.node.sourceStart,
                    part.node.sourceEnd,
                )
            }
        }

        if (parts.size == 1) {
            ParenthesizedExpressionNode(parts[0].node as ExpressionNode)
        } else {
            ParenthesizedExpressionNode(CommaExpressionNode(parts.map { it.node as ExpressionNode }))
        }
    }

    private fun parseUnaryExpression(): ExpressionNode = nps {
        val type = consume()
        val expression = parseExpression(type.operatorPrecedence, type.leftAssociative)

        when (type) {
            TokenType.Inc -> UpdateExpressionNode(expression, isIncrement = true, isPostfix = false)
            TokenType.Dec -> UpdateExpressionNode(expression, isIncrement = false, isPostfix = false,)
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

    private inline fun <T> functionBoundary(isAsync: Boolean = false, isGenerator: Boolean = false, block: () -> T): T {
        labelStack.pushFunctionBoundary()
        val previousFunctionCtx = inFunctionContext
        val previousYieldCtx = inYieldContext
        val previewAsyncCtx = inAsyncContext
        val previousDefaultCtx = inDefaultContext
        val previousBreakContext = inBreakContext
        val previousContinueContext = inContinueContext

        inFunctionContext = true
        inYieldContext = isGenerator
        inAsyncContext = isAsync
        inDefaultContext = false
        inBreakContext = false
        inContinueContext = false

        val result = block()

        labelStack.popFunctionBoundary()
        inFunctionContext = previousFunctionCtx
        inYieldContext = previousYieldCtx
        inAsyncContext = previewAsyncCtx
        inDefaultContext = previousDefaultCtx
        inBreakContext = previousBreakContext
        inContinueContext = previousContinueContext

        return result
    }

    private inline fun <T> inBreakContinueContext(isBreak: Boolean = true, isContinue: Boolean = true, block: () -> T): T {
        val previousBreakContext = inBreakContext
        val previousContinueContext = inContinueContext
        inBreakContext = inBreakContext || isBreak
        inContinueContext = inContinueContext || isContinue
        val result = block()
        inBreakContext = previousBreakContext
        inContinueContext = previousContinueContext
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
        node.sourceEnd = sourceEnd
        return node
    }

    private fun ExpressionNode?.expect(): ExpressionNode {
        return this ?: reportError(ParserErrors.Expected("expression", tokenType.toString()))
    }

    private fun StatementNode?.expect(): StatementNode {
        return this ?: reportError(ParserErrors.Expected("statement", tokenType.toString()))
    }

    private fun List<StatementNode>?.expect(): List<StatementNode> {
        return this ?: reportError(ParserErrors.Expected("statement list", tokenType.toString()))
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
        token = if (receivedTokenList.isNotEmpty()) {
            receivedTokenList.removeFirst()
        } else tokenQueue.take()
        return old
    }

    private fun consume(type: TokenType) {
        if (tokenType != type)
            reportError(ParserErrors.ExpectedToken(type, tokenType))
        consume()
    }

    private fun ensureCachedTokenCount(n: Int) {
        repeat(n - receivedTokenList.size) {
            receivedTokenList.addLast(tokenQueue.take())
        }
    }

    fun reportError(error: ParserError): Nothing {
        throw ParsingException(error.message, token.start, token.end)
    }

    class ParsingException(
        message: String,
        val start: TokenLocation,
        val end: TokenLocation,
    ) : Throwable(message)

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

        data class State(
            val breakableLabels: MutableSet<String> = mutableSetOf(),
            val continuableLabels: MutableSet<String> = mutableSetOf(),
        )
    }
}
