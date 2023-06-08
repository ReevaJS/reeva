package com.reevajs.reeva.parsing

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.ast.expressions.*
import com.reevajs.reeva.ast.literals.*
import com.reevajs.reeva.ast.statements.*
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.lifecycle.ModuleRecord
import com.reevajs.reeva.core.lifecycle.SourceInfo
import com.reevajs.reeva.parsing.lexer.*
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.utils.*
import com.reevajs.regexp.parser.RegExpSyntaxError
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
class Parser(val sourceInfo: SourceInfo) {
    private val source: String
        get() = sourceInfo.sourceText

    private var inDefaultContext = false
    private var inFunctionContext = false
    private var inYieldContext = false
    private var inAsyncContext = false
    private var disableAutoScoping = false

    private val labelStateStack = mutableListOf(LabelState())

    internal val reporter = ErrorReporter(this)

    private val tokens = mutableListOf<Token>()
    private var tokenCursor = 0
    private val token: Token
        get() = tokens[tokenCursor]
    private val lastToken: Token
        get() = tokens[(tokenCursor - 1).coerceAtLeast(0)]

    private val tokenType: TokenType
        inline get() = token.type
    private val isDone: Boolean
        get() = tokenType == TokenType.Eof

    internal val sourceStart: TokenLocation
        inline get() = token.start
    internal val sourceEnd: TokenLocation
        inline get() = token.end

    private val nonArrowFunctionParens = mutableSetOf<Int>()

    private var isStrict = false

    private fun initLexer(isModule: Boolean) {
        tokens.addAll(Lexer(source, isModule).getTokens())
    }

    fun parseScript(): Result<ParsingError, ParsedSource> {
        return parseImpl(isModule = false) {
            if (tokens.size == 1) {
                // The script is empty
                val newlineCount = source.count { it == '\n' }
                val indexOfLastNewline = source.indexOfLast { it == '\n' }
                val start = TokenLocation(0, 0, 0)
                val end = TokenLocation(source.lastIndex, newlineCount, source.lastIndex - indexOfLastNewline)
                ScriptNode(emptyList(), false).withPosition(start, end)
            } else parseScriptImpl()
        }
    }

    fun parseModule(): Result<ParsingError, ParsedSource> {
        return parseImpl(isModule = true) {
            if (tokens.size == 1) {
                // The script is empty
                val newlineCount = source.count { it == '\n' }
                val indexOfLastNewline = source.indexOfLast { it == '\n' }
                val start = TokenLocation(0, 0, 0)
                val end = TokenLocation(source.lastIndex, newlineCount, source.lastIndex - indexOfLastNewline)
                ModuleNode(emptyList()).withPosition(start, end)
            } else parseModuleImpl()
        }
    }

    fun parseFunction(expectedKind: AOs.FunctionKind): Result<ParsingError, ParsedSource> {
        return parseImpl(isModule = false) {
            parseFunctionDeclaration().also {
                expect(it.kind == expectedKind)
            }
        }
    }

    private fun parseImpl(isModule: Boolean, block: () -> NodeWithScope): Result<ParsingError, ParsedSource> {
        return try {
            initLexer(isModule)

            val result = block()
            if (!isDone)
                reporter.at(token).unexpectedToken(tokenType)

            ScopeResolver().resolve(result)
            EarlyErrorDetector(reporter).visit(result)

            if (Agent.activeAgent.printAst)
                println(result.debugPrint())

            Result.success(ParsedSource(sourceInfo, result))
        } catch (e: LexingException) {
            Result.error(ParsingError(e.message!!, e.source, e.source.shiftColumn(1)))
        } catch (e: ParsingException) {
            Result.error(ParsingError(e.message!!, e.start, e.end))
        }
    }

    /*
     * Script :
     *     ScriptBody?
     *
     * ScriptBody :
     *     StatementList
     */
    private fun parseScriptImpl(): ScriptNode = nps {
        isStrict = checkForAndConsumeUseStrict() != null
        ScriptNode(parseStatementList(), isStrict)
    }

    /*
     * Module :
     *     ModuleBody?
     *
     * ModuleBody :
     *     ModuleItemList
     */
    private fun parseModuleImpl(): ModuleNode = nps {
        isStrict = true
        ModuleNode(parseModuleItemList())
    }

    /*
     * ModuleItemList :
     *     ModuleItem
     *     ModuleItemList ModuleItem
     *
     * ModuleItem :
     *     ImportDeclaration
     *     ExportDeclaration
     *     StatementListItem
     */
    private fun parseModuleItemList(): List<AstNode> {
        val list = mutableListOf<AstNode>()

        while (tokenType.isStatementToken) {
            if (match(TokenType.Export)) {
                list.add(parseExportDeclaration())
            } else if (match(TokenType.Import)) {
                list.add(parseImportDeclaration())
            } else {
                list.add(parseStatement(orDecl = true))
            }
        }

        return list
    }

    /*
     * StatementList :
     *     StatementListItem
     *     StatementList StatementListItem
     */
    private fun parseStatementList(): List<AstNode> {
        val list = mutableListOf<AstNode>()

        while (tokenType.isStatementToken)
            list.add(parseStatement(orDecl = true))

        return list
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
    private fun matchLabellableStatement() = matches(
        TokenType.If,
        TokenType.For,
        TokenType.While,
        TokenType.Try,
        TokenType.Switch,
        TokenType.Do,
        TokenType.OpenCurly,
    )

    private fun parseLabellableStatement(): AstNode = nps {
        val labels = mutableSetOf<String>()
        val currentLabelState = labelStateStack.last()

        while (matchIdentifier()) {
            val peeked = peek() ?: break
            if (peeked.type != TokenType.Colon)
                break
            val label = parseIdentifier()
            if (!currentLabelState.canDeclareLabel(label.processedName))
                reporter.at(label).duplicateLabel(label.processedName)
            labels.add(label.processedName)
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

        currentLabelState.pushBlock(type, labels)

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

    private fun parseStatement(orDecl: Boolean = false): AstNode {
        if (isDone)
            reporter.at(lastToken).expected("statement", "eof")

        if (matchIdentifier() || matchLabellableStatement())
            return parseLabellableStatement()

        return when (tokenType) {
            TokenType.Var -> parseVariableDeclaration(false)
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
            else -> {
                if (orDecl)
                    parseDeclaration()?.also { return it }

                if (tokenType.isExpressionToken) {
                    if (match(TokenType.Function))
                        reporter.functionInExpressionContext()
                    if (match(TokenType.Async) &&
                        peek(1)?.let { it.type == TokenType.Function && !it.afterNewline } == true
                    ) {
                        reporter.functionInExpressionContext()
                    }

                    if (match(TokenType.Class))
                        reporter.functionInExpressionContext()

                    return nps {
                        ExpressionStatementNode(parseExpression().also { asi() })
                    }
                }

                reporter.expected("statement", tokenType)
            }
        }
    }

    private fun parseDeclaration(): DeclarationNode? = nps {
        when (tokenType) {
            TokenType.Function, TokenType.Async -> parseFunctionDeclaration()
            TokenType.Class -> parseClassDeclaration()
            TokenType.Let, TokenType.Const -> parseVariableDeclaration(false)
            else -> null
        }
    }

    /*
     * ImportDeclaration :
     *     import ImportClause FromClause ;
     *     import ModuleSpecifier ;
     *
     * ImportClause :
     *     ImportedDefaultBinding
     *     NameSpaceImport
     *     NamedImports
     *     ImportedDefaultBinding , NameSpaceImport
     *     ImportedDefaultBinding , NamedImports
     *
     * ModuleSpecifier :
     *     StringLiteral
     */
    private fun parseImportDeclaration(): ImportNode = nps {
        consume(TokenType.Import)

        if (tokenType == TokenType.StringLiteral) {
            return@nps ImportNode(parseStringLiteral().value).also { asi() }
        }

        val defaultImport = parseDefaultImport()

        if (defaultImport != null) {
            if (!match(TokenType.Comma))
                return@nps ImportNode(defaultImport, parseImportExportFrom())

            consume()

            val namespaceImport = parseNamespaceImport()
            if (namespaceImport != null)
                return@nps ImportNode(namespaceImport, parseImportExportFrom())

            val namedImports = parseNamedImports()
            if (namedImports != null)
                return@nps ImportNode(namedImports, parseImportExportFrom())

            reporter.at(token).expected("named import list")
        }

        val namespaceImport = parseNamespaceImport()
        if (namespaceImport != null)
            return@nps ImportNode(namespaceImport, parseImportExportFrom())

        val namedImports = parseNamedImports()
        if (namedImports != null)
            return@nps ImportNode(namedImports, parseImportExportFrom())

        reporter.at(token).expected("namespace import or named import list")
    }

    /*
     * ImportedDefaultBinding :
     *     ImportedBinding
     *
     * ImportedBinding :
     *     BindingIdentifier
     */
    private fun parseDefaultImport(): Import.Default? = nps {
        if (matchIdentifierName()) {
            Import.Default(parseBindingIdentifier())
        } else null
    }

    /*
     * NameSpaceImport :
     *     * as ImportedBinding
     *
     * ImportedBinding :
     *     BindingIdentifier
     */
    private fun parseNamespaceImport(): Import.Namespace? = nps {
        if (!match(TokenType.Mul))
            return@nps null

        consume()
        if (!match(TokenType.Identifier) || token.rawLiterals != "as")
            reporter.at(token).expected("\"as\"")
        consume()

        Import.Namespace(parseBindingIdentifier())
    }

    /*
     * NamedImports :
     *     { }
     *     { ImportsList }
     *     { ImportsList , }
     *
     * ImportsList:
     *     ImportSpecifier
     *     ImportsList , ImportSpecifier
     *
     * ImportSpecifier :
     *     ImportedBinding
     *     IdentifierName as ImportedBinding
     *
     * ImportedBinding :
     *     BindingIdentifier
     */
    private fun parseNamedImports(): List<Import.Named>? {
        if (!match(TokenType.OpenCurly))
            return null

        consume()

        val imports = mutableListOf<Import.Named>()

        while (!match(TokenType.CloseCurly)) {
            val peeked = peek(1) ?: reporter.at(token).error("unexpected eol")

            val namedImport = nps {
                val identifier = parseBindingIdentifier()
                val alias = if (peeked.type == TokenType.Identifier && peeked.rawLiterals == "as") {
                    consume(TokenType.Identifier)
                    parseBindingIdentifier()
                } else identifier

                Import.Named(identifier, alias)
            }

            imports.add(namedImport)

            if (!match(TokenType.Comma))
                break

            consume()
        }

        consume(TokenType.CloseCurly)

        return imports
    }

    /*
     * ExportDeclaration :
     *     export ExportFromClause FromClause ;
     *     export NamedExports ;
     *     export VariableStatement
     *     export Declaration
     *     export default HoistableDeclaration
     *     export default ClassDeclaration
     *     export default [lookahead ∉ { function, async [no LineTerminator here] function, class }]
     *         AssignmentExpression ;
     */
    private fun parseExportDeclaration(): ExportNode = nps {
        consume(TokenType.Export)

        parseExportFromOrNamedExportsClause()?.let { return@nps it }

        if (match(TokenType.Var) || match(TokenType.Let) || match(TokenType.Const))
            return@nps ExportNode(Export.Node(parseVariableDeclaration(), default = false))

        if (match(TokenType.Function) || match(TokenType.Async))
            return@nps ExportNode(Export.Node(parseFunctionDeclaration(), default = false))

        if (match(TokenType.Class))
            return@nps ExportNode(Export.Node(parseClassDeclaration(), default = false))

        if (!match(TokenType.Default))
            reporter.at(token).expected("\"default\"")

        consume()

        inDefaultContext = true

        if (match(TokenType.Function) || match(TokenType.Async))
            return@nps ExportNode(Export.Node(parseFunctionDeclaration(), default = true))

        if (match(TokenType.Class))
            return@nps ExportNode(Export.Node(parseClassDeclaration(), default = true))

        // TODO: AssignmentExpression, not Expression
        ExportNode(nps { Export.Expr(parseExpression()) }).also {
            inDefaultContext = false
        }
    }

    /*
     * ExportDeclaration :
     *     export ExportFromClause FromClause ;
     *
     * ExportFromClause :
     *     *
     *     * as IdentifierName
     *     NamedExports
     *
     * FromClause :
     *     from ModuleSpecifier
     *
     * ModuleSpecifier :
     *     StringLiteral
     */
    private fun parseExportFromOrNamedExportsClause(): ExportNode? = nps {
        if (match(TokenType.Mul)) {
            consume()
            if (match(TokenType.Identifier) && token.rawLiterals == "as") {
                consume()
                val export = Export.Namespace(parseIdentifier(), parseImportExportFrom())
                return@nps ExportNode(listOf(export))
            }

            val export = Export.Namespace(null, parseImportExportFrom())
            return@nps ExportNode(listOf(export))
        }

        parseNamedExports()?.let { exports ->
            val from = maybeParseImportExportFrom()
            if (from != null) {
                exports.forEach { it.moduleName = from }
            }
            ExportNode(exports)
        }
    }

    /*
     * NamedExports :
     *     { }
     *     { ExportsList }
     *     { ExportsList , }
     *
     * ExportsList :
     *     ExportSpecifier
     *     ExportsList , ExportSpecifier
     *
     * ExportSpecifier :
     *     IdentifierName
     *     IdentifierName as IdentifierName
     */
    private fun parseNamedExports(): List<Export.Named>? {
        if (!match(TokenType.OpenCurly))
            return null

        consume()

        val list = mutableListOf<Export.Named>()

        while (!match(TokenType.CloseCurly)) {
            val name = parseIdentifierReference().identifierNode
            if (tokenType == TokenType.Identifier && token.rawLiterals == "as") {
                consume()
                val ident = parseIdentifier().let {
                    if (it.processedName == "default") {
                        IdentifierNode(ModuleRecord.DEFAULT_SPECIFIER).withPosition(it)
                    } else it
                }
                list.add(Export.Named(name, ident).withPosition(name, ident))
            } else {
                list.add(Export.Named(name).withPosition(name))
            }

            if (!match(TokenType.Comma))
                break

            consume()
        }

        consume(TokenType.CloseCurly)

        return list
    }

    private fun parseImportExportFrom(): String =
        maybeParseImportExportFrom() ?: reporter.at(token).expected("\"from\"")

    private fun maybeParseImportExportFrom(): String? {
        if (!match(TokenType.Identifier) || token.rawLiterals != "from")
            return null
        consume()
        return parseStringLiteral().value
    }

    private fun asi(afterDoWhile: Boolean = false) {
        if (isDone)
            return

        if (match(TokenType.Semicolon)) {
            consume()
            return
        }
        if (token.afterNewline)
            return
        if (matches(TokenType.CloseCurly, TokenType.Eof))
            return

        if (!afterDoWhile)
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
    private fun parseVariableDeclaration(isForEachLoop: Boolean = false): DeclarationNode = nps {
        val type = consume()
        expect(type == TokenType.Var || type == TokenType.Let || type == TokenType.Const)

        val declarations = mutableListOf<Declaration>()

        while (true) {
            val start = sourceStart

            val target = if (matchBindingPattern()) {
                parseBindingPattern()
            } else parseBindingIdentifier()

            val initializer = if (match(TokenType.Equals)) {
                consume()
                parseExpression(2)
            } else if (!isForEachLoop && type == TokenType.Const) {
                reporter.constMissingInitializer()
            } else null

            val declaration = if (target is BindingPatternNode) {
                DestructuringDeclaration(target, initializer)
            } else {
                expect(target is IdentifierNode)
                NamedDeclaration(target, initializer)
            }.withPosition(start, lastToken.end)

            declarations.add(declaration)

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

    private fun matchBindingPattern() = matches(TokenType.OpenCurly, TokenType.OpenBracket)

    private fun parseBindingPattern(): BindingPatternNode = nps {
        if (match(TokenType.OpenCurly)) {
            parseObjectBindingPattern()
        } else parseArrayBindingPattern()
    }

    private fun parseBindingDeclaration(): BindingDeclaration = nps {
        BindingDeclaration(parseBindingIdentifier())
    }

    private fun parseObjectBindingPattern(): BindingPatternNode = nps {
        consume(TokenType.OpenCurly)

        val bindingEntries = mutableListOf<BindingProperty>()

        while (!match(TokenType.CloseCurly)) {
            if (match(TokenType.TriplePeriod)) {
                consume()
                if (!matchIdentifier())
                    reporter.at(token).expected("identifier")

                val identifier = parseBindingIdentifier()
                val declaration = BindingDeclaration(identifier).withPosition(identifier)

                bindingEntries.add(BindingRestProperty(declaration).withPosition(declaration))
                break
            }

            if (matchIdentifier()) {
                val bindingProperty = nps {
                    val identifier = parseBindingDeclaration()

                    val alias = if (match(TokenType.Colon)) {
                        consume()

                        if (matchBindingPattern()) {
                            parseBindingPattern()
                        } else {
                            parseBindingDeclaration()
                        }.let(::BindingDeclarationOrPattern)
                    } else null

                    SimpleBindingProperty(identifier, alias, parseInitializer())
                }

                bindingEntries.add(bindingProperty)
            } else if (matchPropertyName()) {
                val bindingProperty = nps {
                    val identifier = parsePropertyName()

                    val alias = if (match(TokenType.Colon)) {
                        consume()

                        if (matchBindingPattern()) {
                            parseBindingPattern()
                        } else {
                            parseBindingDeclaration()
                        }.let(::BindingDeclarationOrPattern)
                    } else {
                        reporter.at(token).missingBindingElement()
                    }

                    ComputedBindingProperty(identifier, alias, parseInitializer())
                }

                bindingEntries.add(bindingProperty)
            } else {
                reporter.at(token).expected("property name or rest property", tokenType)
            }

            if (match(TokenType.Comma)) {
                consume()
            } else break
        }

        consume(TokenType.CloseCurly)

        BindingPatternNode(BindingKind.Object, bindingEntries)
    }

    private fun parseArrayBindingPattern(): BindingPatternNode = nps {
        consume(TokenType.OpenBracket)

        val bindingEntries = mutableListOf<BindingElement>()

        while (!match(TokenType.CloseBracket)) {
            while (match(TokenType.Comma)) {
                val element = BindingElisionElement().withPosition(token)
                consume()
                bindingEntries.add(element)
            }

            if (match(TokenType.TriplePeriod)) {
                consume()
                val declaration = if (matchIdentifier()) {
                    parseBindingDeclaration()
                } else if (!matchBindingPattern()) {
                    reporter.at(token).expected("binding pattern or identifier", tokenType)
                } else {
                    parseBindingPattern()
                }
                val declOrPattern = BindingDeclarationOrPattern(declaration).withPosition(declaration)
                val bindingElement = BindingRestElement(declOrPattern).withPosition(declOrPattern)
                bindingEntries.add(bindingElement)
                break
            }

            val bindingElement = nps {
                val declOrPattern = nps {
                    val alias = if (matchIdentifier()) {
                        parseBindingDeclaration()
                    } else if (!matchBindingPattern()) {
                        reporter.at(token).expected("binding pattern or identifier", tokenType)
                    } else {
                        parseBindingPattern()
                    }
                    BindingDeclarationOrPattern(alias)
                }

                SimpleBindingElement(declOrPattern, parseInitializer())
            }


            bindingEntries.add(bindingElement)

            if (match(TokenType.Comma)) {
                consume()
            } else break
        }

        consume(TokenType.CloseBracket)

        BindingPatternNode(BindingKind.Array, bindingEntries)
    }

    private fun parseInitializer() = if (match(TokenType.Equals)) {
        consume()
        parseExpression(2)
    } else null

    /*
     * DoWhileStatement :
     *     do Statement while ( Expression ) ;
     */
    private fun parseDoWhileStatement(): AstNode = nps {
        consume(TokenType.Do)
        val statement = parseStatement()
        consume(TokenType.While)
        consume(TokenType.OpenParen)
        val condition = parseExpression()
        consume(TokenType.CloseParen)
        asi(afterDoWhile = true)
        DoWhileStatementNode(condition, statement)
    }

    /*
     * WhileStatement :
     *     while ( Expression ) Statement
     */
    private fun parseWhileStatement(): AstNode = nps {
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
    private fun parseSwitchStatement(): AstNode = nps {
        consume(TokenType.Switch)
        consume(TokenType.OpenParen)
        val target = parseExpression(0)
        consume(TokenType.CloseParen)
        consume(TokenType.OpenCurly)

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

        consume(TokenType.CloseCurly)

        SwitchStatementNode(target, clauses)
    }

    /*
     * ContinueStatement :
     *     continue ;
     *     continue [no LineTerminator here] LabelIdentifier ;
     */
    private fun parseContinueStatement(): AstNode = nps {
        consume(TokenType.Continue)

        val continueToken = lastToken

        if (match(TokenType.Semicolon) || token.afterNewline) {
            if (match(TokenType.Semicolon))
                consume()

            labelStateStack.last().validateContinue(continueToken, null)
            return@nps ContinueStatementNode(null)
        }

        if (matchIdentifier()) {
            val identifier = parseIdentifier().processedName
            labelStateStack.last().validateContinue(continueToken, lastToken)
            return@nps ContinueStatementNode(identifier).also { asi() }
        }

        reporter.expected("identifier", tokenType)
    }

    /*
     * BreakStatement :
     *     break ;
     *     break [no LineTerminator here] LabelIdentifier ;
     */
    private fun parseBreakStatement(): AstNode = nps {
        consume(TokenType.Break)

        val breakToken = lastToken

        if (match(TokenType.Semicolon) || token.afterNewline) {
            if (match(TokenType.Semicolon))
                consume()

            labelStateStack.last().validateBreak(breakToken, null)
            return@nps BreakStatementNode(null)
        }

        if (matchIdentifier()) {
            val identifier = parseIdentifier().processedName
            labelStateStack.last().validateBreak(breakToken, lastToken)
            return@nps BreakStatementNode(identifier).also { asi() }
        }

        reporter.expected("identifier", tokenType)
    }

    /*
     * ReturnStatement :
     *     return ;
     *     return [no LineTerminator here] Expression ;
     */
    private fun parseReturnStatement(): AstNode = nps {
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
    private fun parseWithStatement(): AstNode = nps {
        reporter.at(token).unsupportedFeature("with statements")
        // consume(TokenType.With)
        // consume(TokenType.OpenParen)
        // val expression = parseExpression()
        // consume(TokenType.CloseParen)
        // val body = parseStatement()
        // WithStatementNode(expression, body)
    }

    /*
     * ThrowStatement :
     *     throw [no LineTerminator here] Expression ;
     */
    private fun parseThrowStatement(): AstNode = nps {
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
    private fun parseTryStatement(): AstNode = nps {
        consume(TokenType.Try)
        val tryBlock = parseBlock()

        val catchBlock = if (match(TokenType.Catch)) {
            consume()
            val catchParam = if (match(TokenType.OpenParen)) {
                consume()
                nps {
                    val declaration = if (matchIdentifier()) {
                        nps { BindingDeclaration(parseIdentifier()) }
                    } else if (matchBindingPattern()) {
                        parseBindingPattern()
                    } else {
                        reporter.at(token).expected("identifier or binding pattern")
                    }
                    CatchParameter(BindingDeclarationOrPattern(declaration))
                }.also {
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
    private fun parseDebuggerStatement(): AstNode = nps {
        consume(TokenType.Debugger)
        asi()
        DebuggerStatementNode()
    }

    private fun matchSwitchClause() = matches(TokenType.Case, TokenType.Default)

    private fun parseNormalForAndForEachStatement(): AstNode = nps {
        consume(TokenType.For)
        if (match(TokenType.Await))
            TODO()

        consume(TokenType.OpenParen)

        var initializer: AstNode? = null

        if (!match(TokenType.Semicolon)) {
            if (tokenType.isExpressionToken) {
                initializer = nps { parseExpression(0, false, setOf(TokenType.In)) }.let {
                    ExpressionStatementNode(it).withPosition(it)
                }
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

    private fun parseForEachStatement(initializer: AstNode): AstNode = nps {
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
    private fun parseFunctionExpression(): AstNode = nps {
        val (identifier, params, body, isGenerator) = parseFunctionHelper(isDeclaration = false)
        FunctionExpressionNode(identifier, params, body, isGenerator)
    }

    private data class FunctionTemp(
        val identifier: IdentifierNode?,
        val params: ParameterList,
        val body: BlockNode,
        val type: AOs.FunctionKind,
    ) : AstNodeBase() {
        override val children get() = emptyList<AstNode>()
    }

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
            isAsync && isGenerator -> AOs.FunctionKind.AsyncGenerator
            isAsync -> AOs.FunctionKind.Async
            isGenerator -> AOs.FunctionKind.Generator
            else -> AOs.FunctionKind.Normal
        }

        // TODO: Allow no identifier in default export
        val identifier = when {
            matchIdentifier() -> parseBindingIdentifier()
            inDefaultContext -> IdentifierNode("default")
            isDeclaration -> reporter.functionStatementNoName()
            else -> null
        }

        val params = parseFunctionParameters()

        val body = parseFunctionBody(isAsync, isGenerator)

        if (body.hasUseStrict && !params.isSimple())
            reporter.at(body.useStrict!!).invalidUseStrict()

        FunctionTemp(identifier, params, body, type)
    }

    /*
     * ClassDeclaration :
     *     class BindingIdentifier ClassTail
     *     [+Default] class ClassTail
     */
    private fun parseClassDeclaration(): ClassDeclarationNode = nps {
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
    private fun parseClassExpression(): AstNode = nps {
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
        val superClass = if (match(TokenType.Extends)) {
            consume()
            parseExpression(2)
        } else null

        consume(TokenType.OpenCurly)

        val elements = mutableListOf<ClassElementNode>()

        while (!match(TokenType.CloseCurly))
            elements.add(parseClassElement() ?: continue)

        val constructors = elements.filterIsInstance<ClassMethodNode>().filter { it.isConstructor() }
        if (constructors.size > 1)
            reporter.at(constructors.last()).duplicateClassConstructor()

        consume(TokenType.CloseCurly)

        ClassNode(superClass, elements)
    }

    private fun parseClassElement(): ClassElementNode? = nps {
        if (match(TokenType.Semicolon)) {
            consume()
            return@nps null
        }

        var name: PropertyName
        var kind = MethodDefinitionNode.Kind.Normal

        val isStaticToken = if (match(TokenType.Static)) {
            consume()
            lastToken
        } else null
        var isStatic = isStaticToken != null

        val isAsyncToken = if (match(TokenType.Async)) {
            consume()
            lastToken
        } else null
        val isAsync = isAsyncToken != null

        val isGeneratorToken = if (match(TokenType.Mul)) {
            consume()
            lastToken
        } else null
        val isGenerator = isGeneratorToken != null

        if (match(TokenType.Equals) || match(TokenType.OpenParen)) {
            if (isGenerator)
                reporter.at(isGeneratorToken!!).expected("property name", "*")

            name = when {
                isAsync -> {
                    PropertyName(IdentifierNode("async"), PropertyName.Type.Identifier)
                }
                isStatic -> {
                    isStatic = false
                    PropertyName(IdentifierNode("static"), PropertyName.Type.Identifier)
                }
                else -> reporter.expected("property name", token.literals)
            }

            return@nps parseClassFieldOrMethod(name, MethodDefinitionNode.Kind.Normal, isStatic)
        }

        name = parsePropertyName()

        if (!match(TokenType.Equals) && !match(TokenType.OpenParen)) {
            if (name.type == PropertyName.Type.Identifier) {
                val identifier = (name.expression as IdentifierNode).processedName
                val isGetter = identifier == "get"
                val isSetter = identifier == "set"

                kind = when {
                    isGetter -> MethodDefinitionNode.Kind.Getter
                    isSetter -> MethodDefinitionNode.Kind.Setter
                    else -> kind
                }

                if (isGetter || isSetter) {
                    if (isAsync)
                        reporter.at(isAsyncToken!!).classAsyncAccessor(isGetter)
                    if (isGenerator)
                        reporter.at(isGeneratorToken!!).classGeneratorAccessor(isGetter)

                    name = parsePropertyName()
                }
            }
        }

        kind = when {
            isAsync && isGenerator -> MethodDefinitionNode.Kind.AsyncGenerator
            isAsync -> MethodDefinitionNode.Kind.Async
            isGenerator -> MethodDefinitionNode.Kind.Generator
            else -> kind
        }

        parseClassFieldOrMethod(name, kind, isStatic)
    }

    private fun parseClassField(name: PropertyName, isStatic: Boolean): ClassFieldNode = nps {
        consume(TokenType.Equals)
        ClassFieldNode(name, parseExpression(0), isStatic)
    }

    private fun parseClassMethod(
        name: PropertyName,
        kind: MethodDefinitionNode.Kind,
        isStatic: Boolean
    ): ClassMethodNode = nps {
        ClassMethodNode(parseMethodDefinition(name, kind), isStatic)
    }

    private fun parseClassFieldOrMethod(
        name: PropertyName,
        kind: MethodDefinitionNode.Kind,
        isStatic: Boolean
    ): ClassElementNode = nps {
        if (match(TokenType.Equals)) {
            parseClassField(name, isStatic)
        } else if (match(TokenType.OpenParen)) {
            parseClassMethod(name, kind, isStatic)
        } else if (match(TokenType.Semicolon) || token.afterNewline) {
            // Must be a class field with no initializer
            ClassFieldNode(name, null, isStatic)
        } else {
            reporter.at(token).expected("class field initializer or semicolon")
        }
    }

    private fun parseMethodDefinition(name: PropertyName, kind: MethodDefinitionNode.Kind): MethodDefinitionNode = nps {
        val params = parseFunctionParameters()
        val body = parseFunctionBody(kind.isAsync, kind.isGenerator)

        if (!params.isSimple() && body.hasUseStrict)
            reporter.at(body.useStrict!!).invalidUseStrict()

        MethodDefinitionNode(name, params, body, kind)
    }

    private fun parseSuperExpression(): AstNode = nps {
        consume(TokenType.Super)

        when (tokenType) {
            TokenType.Period -> {
                consume()
                if (match(TokenType.Hash))
                    reporter.at(token).error("Reeva does not support private identifier")
                if (!match(TokenType.Identifier))
                    reporter.at(token).expected("identifier", tokenType)
                val identifier = parseIdentifier()
                SuperPropertyExpressionNode(identifier, isComputed = false)
            }
            TokenType.OpenBracket -> {
                consume()
                val expression = parseExpression(0)
                consume(TokenType.CloseBracket)
                SuperPropertyExpressionNode(expression, isComputed = true)
            }
            TokenType.OpenParen -> SuperCallExpressionNode(parseArguments())
            else -> reporter.at(token).expected("super property or super call", tokenType)
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
            return@nps ParameterList(emptyList())
        }

        val parameters = mutableListOf<Parameter>()

        while (true) {
            if (match(TokenType.CloseParen))
                break

            if (match(TokenType.TriplePeriod)) {
                nps {
                    consume()
                    val declaration = if (matchIdentifier()) {
                        nps { BindingDeclaration(parseBindingIdentifier()) }
                    } else parseBindingPattern()
                    if (!match(TokenType.CloseParen))
                        reporter.paramAfterRest()

                    RestParameter(BindingDeclarationOrPattern(declaration))
                }.also(parameters::add)
                break
            }

            if (matchBindingPattern()) {
                val parameter = nps {
                    BindingParameter(parseBindingPattern(), parseInitializer())
                }
                parameters.add(parameter)
            } else if (!matchIdentifier()) {
                reporter.at(token).expected("identifier")
            } else {
                parameters.add(nps {
                    SimpleParameter(parseBindingIdentifier(), parseInitializer())
                })
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

    private fun matchIdentifierName(): Boolean {
        return match(TokenType.Identifier) || tokenType.category == TokenType.Category.Keyword
    }

    private fun parseIdentifierReference(): IdentifierReferenceNode = nps {
        IdentifierReferenceNode(parseIdentifier())
    }

    private val strictProtectedNames = setOf(
        "implements", "interface", "let", "package", "private", "protected", "public", "static", "yield"
    )

    private fun parseIdentifier(): IdentifierNode = nps {
        expect(matchIdentifierName())
        val token = this.token
        val identifier = token.literals
        consume()

        val unescaped = unescapeString(identifier)

        if (!unescaped[0].let { it == '$' || it == '_' || it.isIdStart() })
            reporter.at(token).identifierInvalidEscapeSequence(identifier)

        if (!unescaped.drop(1).all { it == '$' || it.isIdContinue() || it == '\u200c' || it == '\u200d' })
            reporter.at(token).identifierInvalidEscapeSequence(identifier)

        IdentifierNode(unescaped, identifier)
    }

    private fun validateBindingIdentifier(identifier: IdentifierNode) {
        if (isStrict && identifier.processedName in strictProtectedNames)
            reporter.at(identifier).identifierStrictReservedWord(identifier.rawName)

        val matchedToken =
            TokenType.values().firstOrNull { it.isIdentifierNameToken && it.string == identifier.processedName }
        if (matchedToken != null)
            reporter.at(identifier).identifierReservedWord(identifier.rawName)
    }

    private fun parseBindingIdentifier(): IdentifierNode {
        if (!matchIdentifierName())
            reporter.at(token).expected("identifier")

        return parseIdentifier().also(::validateBindingIdentifier)
    }

    private fun consumeStringLiteral(): StringLiteralNode? = nps {
        if (match(TokenType.StringLiteral)) {
            val node = StringLiteralNode(token.literals)
            consume()
            node
        } else null
    }.also {
        if (match(TokenType.Semicolon))
            consume()
    }

    private fun checkForAndConsumeUseStrict(): AstNode? {
        var useStrictDirective: StringLiteralNode? = null

        while (true) {
            val stringLiteral = consumeStringLiteral() ?: return useStrictDirective
            if (stringLiteral.value == "use strict")
                useStrictDirective = stringLiteral
        }
    }

    private fun parseBlock(): BlockNode = nps {
        consume(TokenType.OpenCurly)
        val prevIsStrict = isStrict
        val useStrict = checkForAndConsumeUseStrict()

        if (useStrict != null)
            this.isStrict = true

        val statements = parseStatementList()
        consume(TokenType.CloseCurly)
        this.isStrict = prevIsStrict

        BlockNode(statements, useStrict)
    }

    private fun matchSecondaryExpression() = tokenType.isSecondaryToken && if (matches(TokenType.Inc, TokenType.Dec)) {
        !token.afterNewline
    } else true

    private fun parseExpression(
        minPrecedence: Int = 0,
        leftAssociative: Boolean = false,
        excludedTokens: Set<TokenType> = emptySet(),
    ): AstNode = nps {
        if (isDone)
            reporter.at(lastToken).expected("expression", "eof")

        // TODO: Template literal handling

        var expression = parsePrimaryExpression()

        while (matchSecondaryExpression() && tokenType !in excludedTokens) {
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
        lhs: AstNode,
        minPrecedence: Int,
        leftAssociative: Boolean,
    ): AstNode {
        fun makeBinaryExpr(op: BinaryOperator): AstNode {
            consume()
            return BinaryExpressionNode(lhs, parseExpression(minPrecedence, leftAssociative), op)
                .withPosition(lhs.sourceLocation.start, lastToken.end)
        }

        fun makeAssignExpr(op: BinaryOperator?): AstNode {
            consume()
            validateAssignmentTarget(lhs)

            return AssignmentExpressionNode(lhs, parseExpression(minPrecedence, leftAssociative), op)
                .withPosition(lhs.sourceLocation.start, lastToken.end)
        }

        return when (tokenType) {
            TokenType.OpenParen -> parseCallExpression(lhs, isOptional = false)
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
                    parseIdentifier(),
                    MemberExpressionNode.Type.NonComputed,
                ).withPosition(lhs.sourceLocation.start, lastToken.end)
            }
            TokenType.OpenBracket -> {
                consume()
                MemberExpressionNode(lhs, parseExpression(), MemberExpressionNode.Type.Computed).also {
                    consume(TokenType.CloseBracket)
                }.withPosition(lhs.sourceLocation.start, lastToken.end)
            }
            TokenType.Inc -> {
                consume()
                validateAssignmentTarget(lhs)
                UpdateExpressionNode(lhs, isIncrement = true, isPostfix = true)
                    .withPosition(lhs.sourceLocation.start, lastToken.end)
            }
            TokenType.Dec -> {
                consume()
                validateAssignmentTarget(lhs)
                UpdateExpressionNode(lhs, isIncrement = false, isPostfix = true)
                    .withPosition(lhs.sourceLocation.start, lastToken.end)
            }
            TokenType.QuestionMark -> parseConditional(lhs).withPosition(lhs.sourceLocation.start, lastToken.end)
            TokenType.OptionalChain -> {
                // TODO: Disallow "new Foo?.a"
                parseOptionalChain(lhs).withPosition(lhs.sourceLocation.start, lastToken.end)
            }
            else -> unreachable()
        }
    }

    private fun parseConditional(lhs: AstNode): AstNode = nps {
        consume(TokenType.QuestionMark)
        val ifTrue = parseExpression(2)
        consume(TokenType.Colon)
        val ifFalse = parseExpression(2)
        ConditionalExpressionNode(lhs, ifTrue, ifFalse)
    }

    private fun parseOptionalChain(base_: AstNode): AstNode = nps {
        val (base, parts) = if (base_ is OptionalChainNode) {
            base_.base to base_.parts.toMutableList()
        } else base_ to mutableListOf()

        do {
            when (tokenType) {
                TokenType.OptionalChain -> {
                    consume()
                    when (tokenType) {
                        TokenType.OpenParen -> parts.add(nps { OptionalCallChain(parseArguments(), isOptional = true) })
                        TokenType.OpenBracket -> {
                            consume()
                            parts.add(nps { OptionalComputedAccessChain(parseExpression(), isOptional = true) })
                            consume(TokenType.CloseBracket)
                        }
                        TokenType.TemplateLiteralStart -> reporter.at(token).templateLiteralAfterOptionalChain()
                        else -> {
                            if (!matchIdentifierName())
                                reporter.at(token).expected("identifier", tokenType)
                            parts.add(nps { OptionalAccessChain(parseIdentifier(), isOptional = true) })
                        }
                    }
                }
                TokenType.OpenParen -> parts.add(nps { OptionalCallChain(parseArguments(), isOptional = false) })
                TokenType.Period -> {
                    consume()
                    if (!matchIdentifierName())
                        reporter.at(token).expected("identifier", tokenType)
                    parts.add(nps { OptionalAccessChain(parseIdentifier(), isOptional = false) })
                }
                TokenType.TemplateLiteralStart -> reporter.at(token).templateLiteralAfterOptionalChain()
                TokenType.OpenBracket -> {
                    consume()
                    parts.add(nps { OptionalComputedAccessChain(parseExpression(), isOptional = false) })
                    consume(TokenType.CloseBracket)
                }
                else -> break
            }
        } while (!isDone)

        OptionalChainNode(base, parts)
    }

    private fun parseCallExpression(lhs: AstNode, isOptional: Boolean): AstNode = nps {
        CallExpressionNode(lhs, parseArguments(), isOptional)
    }

    private fun parseNewExpression(): AstNode = nps {
        consume(TokenType.New)
        if (has(2) && match(TokenType.Period) && peek()?.type == TokenType.Identifier) {
            consume()
            consume()
            if (lastToken.literals != "target")
                reporter.at(lastToken).invalidNewMetaProperty()
            return@nps NewTargetNode()
        }
        val target = parseExpression(TokenType.New.operatorPrecedence, excludedTokens = setOf(TokenType.OpenParen))
        val arguments = if (match(TokenType.OpenParen)) parseArguments() else emptyList()
        NewExpressionNode(target, arguments)
    }

    private fun parseArguments(): List<ArgumentNode> {
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
                    .withPosition(start, lastToken.end)
            }
            arguments.add(node)
            if (!match(TokenType.Comma))
                break
            consume()
        }

        consume(TokenType.CloseParen)

        return arguments
    }

    private fun parseYieldExpression(): AstNode = nps {
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

        if (isYieldStar)
            reporter.expected("expression", tokenType)

        YieldExpressionNode(null, isYieldStar)
    }

    private fun parseParenthesizedExpression(): AstNode = nps {
        consume(TokenType.OpenParen)
        val expr = parseExpression(0)
        consume(TokenType.CloseParen)
        expr
    }

    private fun parsePrimaryExpression(): AstNode = nps {
        if (tokenType.isUnaryToken)
            return@nps parseUnaryExpression()

        when (tokenType) {
            TokenType.OpenParen -> {
                val index = token.start.index
                if (index !in nonArrowFunctionParens) {
                    val arrow = tryParseArrowFunction()
                    if (arrow != null)
                        return@nps arrow
                    nonArrowFunctionParens.add(index)
                }

                parseParenthesizedExpression()
            }
            TokenType.This -> {
                consume()
                ThisLiteralNode()
            }
            TokenType.Class -> parseClassExpression()
            TokenType.Super -> parseSuperExpression()
            TokenType.Identifier -> {
                if (peek()?.type == TokenType.Arrow) {
                    val arrow = tryParseArrowFunction()
                    if (arrow != null)
                        return@nps arrow
                }
                parseIdentifierReference()
            }
            TokenType.NumericLiteral -> parseNumericLiteral()
            TokenType.BigIntLiteral -> parseBigIntLiteral()
            TokenType.True -> {
                consume()
                TrueNode()
            }
            TokenType.False -> {
                consume()
                FalseNode()
            }
            TokenType.Async, TokenType.Function -> parseFunctionExpression()
            TokenType.Await -> parseAwaitExpression()
            TokenType.StringLiteral -> parseStringLiteral()
            TokenType.NullLiteral -> {
                consume()
                NullLiteralNode()
            }
            TokenType.OpenCurly -> parseObjectLiteral()
            TokenType.OpenBracket -> parseArrayLiteral()
            TokenType.RegExpLiteral -> parseRegExpLiteral()
            TokenType.TemplateLiteralStart -> parseTemplateLiteral()
            TokenType.New -> parseNewExpression().also {
                if (it is NewTargetNode && !inFunctionContext)
                    reporter.at(it).newTargetOutsideOfFunction()
            }
            TokenType.Yield -> parseYieldExpression()
            else -> reporter.expected("primary expression", tokenType)
        }
    }

    private fun parseRegExpLiteral(): RegExpLiteralNode = nps {
        val source = token.literals.drop(1).dropLast(1)
        consume(TokenType.RegExpLiteral)

        val flags = if (match(TokenType.RegexFlags)) {
            token.literals.also { consume() }
        } else ""

        try {
            RegExpLiteralNode(source, flags, AOs.makeRegExp(source, flags))
        } catch (e: RegExpSyntaxError) {
            reporter.at(token).error(e.message!!)
        }
    }

    private fun parseAwaitExpression(): AstNode = nps {
        consume(TokenType.Await)
        AwaitExpressionNode(parseExpression(2))
    }

    private fun parseStringLiteral(): StringLiteralNode = nps {
        consume(TokenType.StringLiteral)
        StringLiteralNode(unescapeString(lastToken.literals))
    }

    private fun unescapeString(string: String): String {
        if (string.isBlank())
            return string

        val builder = StringBuilder()
        var cursor = 0

        while (cursor < string.length) {
            var char = string[cursor++]
            if (char == '\\') {
                char = string[cursor++]
                // TODO: Octal escapes in non-string mode code
                when (char) {
                    '\'' -> builder.append('\'')
                    '"' -> builder.append('"')
                    '\\' -> builder.append('\\')
                    'b' -> builder.append('\b')
                    'n' -> builder.append('\n')
                    't' -> builder.append('\t')
                    'r' -> builder.append('\r')
                    'v' -> builder.append(11.toChar())
                    'f' -> builder.append(12.toChar())
                    'x' -> {
                        if (string.length - cursor < 2)
                            reporter.at(lastToken).stringInvalidHexEscape()

                        val char1 = string[cursor++]
                        val char2 = string[cursor++]

                        if (!char1.isHexDigit() || !char2.isHexDigit())
                            reporter.at(lastToken).stringInvalidHexEscape()

                        val digit1 = char1.hexValue()
                        val digit2 = char2.hexValue()

                        builder.append(((digit1 shl 4) or digit2).toChar())
                    }
                    'u' -> {
                        if (string[cursor] == '{') {
                            cursor++
                            if (string[cursor] == '}')
                                reporter.at(lastToken).stringEmptyUnicodeEscape()

                            var codePoint = 0

                            while (true) {
                                if (cursor > string.lastIndex)
                                    reporter.at(lastToken).stringUnicodeCodepointMissingBrace()
                                char = string[cursor++]
                                if (char == '}')
                                    break
                                if (char == '_')
                                    reporter.at(lastToken).stringInvalidUnicodeNumericSeparator()
                                if (!char.isHexDigit())
                                    reporter.at(lastToken).stringUnicodeCodepointMissingBrace()

                                val oldValue = codePoint
                                codePoint = (codePoint shl 4) or char.hexValue()
                                if (codePoint < oldValue)
                                    reporter.at(lastToken).stringBigUnicodeCodepointEscape()
                            }

                            if (codePoint > 0x10ffff)
                                reporter.at(lastToken).stringBigUnicodeCodepointEscape()

                            builder.appendCodePoint(codePoint)
                        } else {
                            var codePoint = 0

                            repeat(4) {
                                if (cursor > string.lastIndex)
                                    reporter.at(lastToken).stringUnicodeMissingDigits()
                                char = string[cursor++]
                                if (!char.isHexDigit())
                                    reporter.at(lastToken).stringUnicodeMissingDigits()
                                codePoint = (codePoint shl 4) or char.hexValue()
                            }

                            builder.appendCodePoint(codePoint)
                        }
                    }
                    else -> if (!char.isLineSeparator()) {
                        builder.append(char)
                    }
                }
            } else if (char.isLineSeparator()) {
                reporter.at(lastToken).stringUnescapedLineBreak()
            } else {
                builder.append(char)
            }
        }

        return builder.toString()
    }

    private fun parseTemplateLiteral(): AstNode = nps {
        consume(TokenType.TemplateLiteralStart)

        val expressions = mutableListOf<AstNode>()
        fun addEmptyString() {
            expressions.add(StringLiteralNode("").withPosition(sourceStart, sourceStart))
        }

        if (!match(TokenType.TemplateLiteralString))
            addEmptyString()

        while (!matches(TokenType.TemplateLiteralEnd, TokenType.UnterminatedTemplateLiteral)) {
            if (match(TokenType.TemplateLiteralString)) {
                nps {
                    StringLiteralNode(unescapeString(token.literals)).also { consume() }
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
    private fun parseObjectLiteral(): AstNode = nps {
        val objectStart = sourceStart
        consume(TokenType.OpenCurly)

        val properties = mutableListOf<Property>()

        while (!match(TokenType.CloseCurly)) {
            properties.add(parseObjectProperty())
            if (match(TokenType.Comma)) {
                consume()
            } else break
        }

        consume(TokenType.CloseCurly)

        return@nps ObjectLiteralNode(properties).withPosition(objectStart, lastToken.end)
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

        val isGeneratorToken = if (match(TokenType.Mul)) {
            consume()
            lastToken
        } else null

        val name = parsePropertyName()

        if (matchPropertyName() || match(TokenType.OpenParen)) {
            val (type, needsNewName) = if (matchPropertyName()) {
                if (name.type != PropertyName.Type.Identifier || isGeneratorToken != null)
                    reporter.unexpectedToken(tokenType)

                val identifier = (name.expression as IdentifierNode).rawName
                if (identifier != "get" && identifier != "set")
                    reporter.unexpectedToken(tokenType)

                val type = if (identifier == "get") {
                    MethodDefinitionNode.Kind.Getter
                } else MethodDefinitionNode.Kind.Setter

                type to true
            } else if (isGeneratorToken != null) {
                MethodDefinitionNode.Kind.Generator to false
            } else MethodDefinitionNode.Kind.Normal to false

            val methodName = if (needsNewName) parsePropertyName() else name

            val params = parseFunctionParameters()

            // TODO: Async/Generator methods
            val body = parseFunctionBody(isAsync = false, isGenerator = isGeneratorToken != null)
            val methoDefinitionNode = MethodDefinitionNode(methodName, params, body, type)
                .withPosition(methodName, body)
            return@nps MethodProperty(methoDefinitionNode)
        }

        if (matches(TokenType.Comma, TokenType.CloseCurly)) {
            if (name.type != PropertyName.Type.Identifier)
                reporter.at(name).invalidShorthandProperty()

            val identifierNode = name.expression as IdentifierNode

            // As this is a shorthand property, it is also a binding identifier, and must
            // be validated as such
            validateBindingIdentifier(identifierNode)

            val node = IdentifierReferenceNode(identifierNode).withPosition(name)

            return@nps ShorthandProperty(node)
        }

        consume(TokenType.Colon)
        val expression = parseExpression(2)

        KeyValueProperty(name, expression)
    }

    private fun matchPropertyName() = matchIdentifierName() ||
        matches(TokenType.OpenBracket, TokenType.StringLiteral, TokenType.NumericLiteral)

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
                PropertyName(parseStringLiteral(), PropertyName.Type.String)
            }
            match(TokenType.NumericLiteral) -> {
                PropertyName(parseNumericLiteral(), PropertyName.Type.Number)
            }
            matchIdentifierName() -> {
                PropertyName(parseIdentifier(), PropertyName.Type.Identifier)
            }
            else -> reporter.unexpectedToken(tokenType)
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
            return@nps ArrayLiteralNode(emptyList())
        }

        val elements = mutableListOf<ArrayElementNode>()

        while (!match(TokenType.CloseBracket)) {
            if (match(TokenType.Comma)) {
                elements.add(
                    nps {
                        consume()
                        ArrayElementNode(null, ArrayElementNode.Type.Elision)
                    }
                )
                continue
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
                    expression,
                    if (isSpread) {
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
            if (nextToken != null && !nextToken.afterNewline && numericToken.end.column == nextToken.start.column - 1)
                reporter.identifierAfterNumericLiteral()
        }

        NumericLiteralNode(numericToken.doubleValue())
    }

    private fun parseBigIntLiteral(): BigIntLiteralNode = nps {
        val numericToken = token
        var value = token.literals
        consume(TokenType.BigIntLiteral)

        if (value.length >= 2 && value[0] == '0' && value[1].isDigit() && isStrict)
            reporter.strictImplicitOctal()

        if (matchIdentifier()) {
            val nextToken = peek()
            if (nextToken != null && !nextToken.afterNewline && numericToken.end.column == nextToken.start.column - 1)
                reporter.identifierAfterNumericLiteral()
        }

        val mode = if (value.length >= 2 && value[0] == '0') {
            when (value[1].lowercaseChar()) {
                'o' -> BigIntLiteralNode.Type.Octal
                'b' -> BigIntLiteralNode.Type.Binary
                'x' -> BigIntLiteralNode.Type.Hex
                else -> BigIntLiteralNode.Type.Normal
            }.also {
                if (it != BigIntLiteralNode.Type.Normal) {
                    // Drop the first two prefix chars
                    value = value.drop(2)
                }
            }
        } else BigIntLiteralNode.Type.Normal

        BigIntLiteralNode(value.dropLast(1).replace("_", ""), mode)
    }

    private fun tryParseArrowFunction(): ArrowFunctionNode? = nps {
        val savedCursor = tokenCursor

        val parameters = try {
            val parameters = if (match(TokenType.OpenParen)) {
                parseFunctionParameters()
            } else {
                nps {
                    val parameter = nps { SimpleParameter(parseBindingIdentifier(), null) }
                    ParameterList(listOf(parameter))
                }
            }

            if (!match(TokenType.Arrow)) {
                tokenCursor = savedCursor
                return@nps null
            }

            parameters
        } catch (e: ParsingException) {
            tokenCursor = savedCursor
            return@nps null
        }

        if (token.afterNewline)
            reporter.arrowFunctionNewLine()

        consume()

        // TODO: Async/Generator functions
        val body = if (match(TokenType.OpenCurly)) {
            parseFunctionBody(isAsync = false, isGenerator = false)
        } else parseExpression(2)

        ArrowFunctionNode(parameters, body, AOs.FunctionKind.Normal)
    }

    private fun parseUnaryExpression(): AstNode = nps {
        val type = consume()
        val expression = parseExpression(type.operatorPrecedence, type.leftAssociative)

        when (type) {
            TokenType.Inc -> {
                validateAssignmentTarget(expression)
                UpdateExpressionNode(expression, isIncrement = true, isPostfix = false)
            }
            TokenType.Dec -> {
                validateAssignmentTarget(expression)
                UpdateExpressionNode(expression, isIncrement = false, isPostfix = false)
            }
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

    private fun parseIfStatement(): AstNode = nps {
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

        val result = parseBlock()

        inFunctionContext = previousFunctionCtx
        inYieldContext = previousYieldCtx
        inAsyncContext = previewAsyncCtx
        inDefaultContext = previousDefaultCtx
        labelStateStack.removeLast()

        return result
    }

    private fun validateAssignmentTarget(node: AstNode) {
        if (node !is IdentifierReferenceNode && node !is MemberExpressionNode && node !is CallExpressionNode)
            reporter.at(node).expressionNotAssignable()
        if (node is OptionalChainNode)
            reporter.at(node).expressionNotAssignable()
        if (isStrict && node is IdentifierReferenceNode) {
            val name = node.processedName
            if (name == "eval")
                reporter.at(node).strictAssignToEval()
            if (name == "arguments")
                reporter.at(node).strictAssignToArguments()
        } else if (isStrict && node is CallExpressionNode) {
            reporter.at(node).expressionNotAssignable()
        }
    }

    // Helper for setting source positions of AST. Stands for
    // node parsing scope; the name is short to prevent long
    // non-local return labels (i.e. "return@nodeParsingScope")
    private inline fun <T : AstNode?> nps(crossinline block: () -> T): T {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        val start = sourceStart
        val node = block()
        if (node == null) {
            // Weird compiler bug: we have to return node here instead of 'null',
            // because null apparently doesn't satisfy the non-null T even though
            // it extends a nullable type.
            return node
        }
        node.sourceLocation = SourceLocation(start, lastToken.end)
        return node
    }

    private fun match(type: TokenType): Boolean {
        return tokenType == type
    }

    private fun matches(vararg types: TokenType): Boolean {
        return types.any { it == tokenType }
    }

    private fun has(n: Int) = tokenCursor + n - 1 <= tokens.lastIndex

    private fun peek(n: Int = 1): Token? {
        if (!has(n))
            return null
        return tokens[tokenCursor + n]
    }

    private fun consume(): TokenType {
        val old = tokenType
        tokenCursor++
        if (!isDone) {
            token.error()?.also {
                throw ParsingException(it, token.start, token.end)
            }
        }
        return old
    }

    private fun consume(type: TokenType) {
        if (tokenType != type)
            reporter.expected(type, tokenType)
        consume()
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

        fun canDeclareLabel(label: String) = blocks.none { label in it.labels }

        fun pushBlock(type: LabellableBlockType, labels: Set<String>) {
            expect(blocks.none { it.labels.intersect(labels).isNotEmpty() })
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
}
