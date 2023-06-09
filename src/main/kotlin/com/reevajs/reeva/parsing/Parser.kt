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

class Parser(val sourceInfo: SourceInfo) {
    private val source: String
        get() = sourceInfo.sourceText

    private var inDefaultContext = false
    private var inFunctionContext = false
    private var inYieldContext = false
    private var inAsyncContext = false

    private val labelStateStack = mutableListOf(LabelState())

    internal val reporter = ErrorReporter(this)

    private val tokens = mutableListOf<Token>()
    private var tokenCursor = 0
    private val token: Token
        get() = tokens[tokenCursor]

    // TODO: This probably shouldn't coerce, if this becomes -1 it's a bug
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

    private val locationStack = mutableListOf<TokenLocation>()
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
                ScriptNode(emptyList(), false, SourceLocation(start, end))
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
                ModuleNode(emptyList(), SourceLocation(start, end))
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
            expect(locationStack.isEmpty())

            ScopeResolver().resolve(result)
            result.accept(EarlyErrorDetector(reporter))

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
    private fun parseScriptImpl(): ScriptNode {
        pushLocation()
        isStrict = checkForAndConsumeUseStrict() != null
        return ScriptNode(parseStatementList(), isStrict, popLocation())
    }

    /*
     * Module :
     *     ModuleBody?
     *
     * ModuleBody :
     *     ModuleItemList
     */
    private fun parseModuleImpl(): ModuleNode {
        isStrict = true
        pushLocation()
        return ModuleNode(parseModuleItemList(), popLocation())
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

    private fun parseLabellableStatement(): AstNode {
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
                    return parseStatement()

                expect(matchIdentifier())
                return ExpressionStatementNode(parseExpression()).also { asi() }
            }
        }

        currentLabelState.pushBlock(type, labels)

        return when (tokenType) {
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
            TokenType.Semicolon -> {
                pushLocation()
                consume()
                EmptyStatementNode(popLocation())
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

                    return ExpressionStatementNode(parseExpression()).also { asi() }
                }

                reporter.expected("statement", tokenType)
            }
        }
    }

    private fun parseDeclaration(): DeclarationNode? {
        return when (tokenType) {
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
    private fun parseImportDeclaration(): ImportNode {
        pushLocation()
        consume(TokenType.Import)

        if (tokenType == TokenType.StringLiteral) {
            return ImportNode(parseStringLiteral().value, popLocation()).also { asi() }
        }

        val defaultImport = parseDefaultImport()

        if (defaultImport != null) {
            if (!match(TokenType.Comma))
                return ImportNode(defaultImport, parseImportExportFrom(), popLocation())

            consume()

            val namespaceImport = parseNamespaceImport()
            if (namespaceImport != null)
                return ImportNode(namespaceImport, parseImportExportFrom(), popLocation())

            val namedImports = parseNamedImports()
            if (namedImports != null)
                return ImportNode(namedImports, parseImportExportFrom(), popLocation())

            reporter.at(token).expected("named import list")
        }

        val namespaceImport = parseNamespaceImport()
        if (namespaceImport != null)
            return ImportNode(namespaceImport, parseImportExportFrom(), popLocation())

        val namedImports = parseNamedImports()
        if (namedImports != null)
            return ImportNode(namedImports, parseImportExportFrom(), popLocation())

        reporter.at(token).expected("namespace import or named import list")
    }

    /*
     * ImportedDefaultBinding :
     *     ImportedBinding
     *
     * ImportedBinding :
     *     BindingIdentifier
     */
    private fun parseDefaultImport(): Import.Default? {
        return if (matchIdentifierName()) {
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
    private fun parseNamespaceImport(): Import.Namespace? {
        if (!match(TokenType.Mul))
            return null

        pushLocation()
        consume()
        if (!match(TokenType.Identifier) || token.rawLiterals != "as")
            reporter.at(token).expected("\"as\"")
        consume()

        return Import.Namespace(parseBindingIdentifier(), popLocation())
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

            pushLocation()
            val identifier = parseBindingIdentifier()
            val alias = if (peeked.type == TokenType.Identifier && peeked.rawLiterals == "as") {
                consume(TokenType.Identifier)
                parseBindingIdentifier()
            } else identifier

            imports.add(Import.Named(identifier, alias, popLocation()))

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
    private fun parseExportDeclaration(): ExportNode {
        pushLocation()
        consume(TokenType.Export)

        parseExportFromOrNamedExportsClause()?.let { return it }

        if (match(TokenType.Var) || match(TokenType.Let) || match(TokenType.Const))
            return ExportNode(Export.Node(parseVariableDeclaration(), default = false), popLocation())

        if (match(TokenType.Function) || match(TokenType.Async))
            return ExportNode(Export.Node(parseFunctionDeclaration(), default = false), popLocation())

        if (match(TokenType.Class))
            return ExportNode(Export.Node(parseClassDeclaration(), default = false), popLocation())

        if (!match(TokenType.Default))
            reporter.at(token).expected("\"default\"")

        consume()

        inDefaultContext = true

        if (match(TokenType.Function) || match(TokenType.Async))
            return ExportNode(Export.Node(parseFunctionDeclaration(), default = true), popLocation())

        if (match(TokenType.Class))
            return ExportNode(Export.Node(parseClassDeclaration(), default = true), popLocation())

        // TODO: AssignmentExpression, not Expression
        return ExportNode(Export.Expr(parseExpression()), popLocation()).also {
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
    // Expects caller to call pushLocation()
    private fun parseExportFromOrNamedExportsClause(): ExportNode? {
        if (match(TokenType.Mul)) {
            pushLocation()
            consume()
            if (match(TokenType.Identifier) && token.rawLiterals == "as") {
                consume()
                val export = Export.Namespace(parseIdentifier(), parseImportExportFrom(), popLocation())
                return ExportNode(listOf(export), popLocation())
            }

            val export = Export.Namespace(null, parseImportExportFrom(), popLocation())
            return ExportNode(listOf(export), popLocation())
        }

        return parseNamedExports()?.let { exports ->
            val from = maybeParseImportExportFrom()
            if (from != null)
                exports.forEach { it.moduleName = from }
            ExportNode(exports, popLocation())
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
            pushLocation()
            val name = parseIdentifierReference().identifierNode
            if (tokenType == TokenType.Identifier && token.rawLiterals == "as") {
                consume()
                val ident = parseIdentifier().let {
                    if (it.processedName == "default") {
                        IdentifierNode(ModuleRecord.DEFAULT_SPECIFIER, sourceLocation = it.sourceLocation)
                    } else it
                }
                list.add(Export.Named(name, ident, sourceLocation = popLocation()))
            } else {
                list.add(Export.Named(name, sourceLocation = popLocation()))
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
    private fun parseVariableDeclaration(isForEachLoop: Boolean = false): DeclarationNode {
        pushLocation()
        val type = consume()
        expect(type == TokenType.Var || type == TokenType.Let || type == TokenType.Const)

        val declarations = mutableListOf<Declaration>()

        while (true) {
            pushLocation()
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
                DestructuringDeclaration(target, initializer, popLocation())
            } else {
                // Compiler bug: Removing this expect() causes an error in the NamedDeclaration construction
                @Suppress("KotlinConstantConditions")
                expect(target is IdentifierNode)
                NamedDeclaration(target, initializer, popLocation())
            }

            declarations.add(declaration)

            if (match(TokenType.Comma)) {
                consume()
            } else break
        }

        return if (type == TokenType.Var) {
            VariableDeclarationNode(declarations, popLocation())
        } else {
            LexicalDeclarationNode(isConst = type == TokenType.Const, declarations, popLocation())
        }.also {
            if (!isForEachLoop)
                asi()
        }
    }

    private fun matchBindingPattern() = matches(TokenType.OpenCurly, TokenType.OpenBracket)

    private fun parseBindingPattern(): BindingPatternNode {
        return if (match(TokenType.OpenCurly)) {
            parseObjectBindingPattern()
        } else parseArrayBindingPattern()
    }

    private fun parseBindingDeclaration(): BindingDeclaration {
        return BindingDeclaration(parseBindingIdentifier())
    }

    private fun parseObjectBindingPattern(): BindingPatternNode {
        pushLocation()
        consume(TokenType.OpenCurly)

        val bindingEntries = mutableListOf<BindingProperty>()

        while (!match(TokenType.CloseCurly)) {
            if (match(TokenType.TriplePeriod)) {
                pushLocation()
                consume()
                if (!matchIdentifier())
                    reporter.at(token).expected("identifier")

                val identifier = parseBindingIdentifier()
                val declaration = BindingDeclaration(identifier)

                bindingEntries.add(BindingRestProperty(declaration, popLocation()))
                break
            }

            pushLocation()

            if (matchIdentifier()) {
                val identifier = parseBindingDeclaration()

                val alias = if (match(TokenType.Colon)) {
                    consume()

                    if (matchBindingPattern()) {
                        parseBindingPattern()
                    } else {
                        parseBindingDeclaration()
                    }.let(::BindingDeclarationOrPattern)
                } else null

                bindingEntries.add(SimpleBindingProperty(identifier, alias, parseInitializer(), popLocation()))
            } else if (matchPropertyName()) {
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

                bindingEntries.add(ComputedBindingProperty(identifier, alias, parseInitializer(), popLocation()))
            } else {
                reporter.at(token).expected("property name or rest property", tokenType)
            }

            if (match(TokenType.Comma)) {
                consume()
            } else break
        }

        consume(TokenType.CloseCurly)

        return BindingPatternNode(BindingKind.Object, bindingEntries, popLocation())
    }

    private fun parseArrayBindingPattern(): BindingPatternNode {
        pushLocation()
        consume(TokenType.OpenBracket)

        val bindingEntries = mutableListOf<BindingElement>()

        while (!match(TokenType.CloseBracket)) {
            while (match(TokenType.Comma)) {
                pushLocation()
                consume()
                bindingEntries.add(BindingElisionElement(popLocation()))
            }

            pushLocation()

            if (match(TokenType.TriplePeriod)) {
                consume()
                val declaration = if (matchIdentifier()) {
                    parseBindingDeclaration()
                } else if (!matchBindingPattern()) {
                    reporter.at(token).expected("binding pattern or identifier", tokenType)
                } else {
                    parseBindingPattern()
                }
                bindingEntries.add(BindingRestElement(BindingDeclarationOrPattern(declaration), popLocation()))
                break
            }

            val alias = if (matchIdentifier()) {
                parseBindingDeclaration()
            } else if (!matchBindingPattern()) {
                reporter.at(token).expected("binding pattern or identifier", tokenType)
            } else {
                parseBindingPattern()
            }
            val declOrPattern = BindingDeclarationOrPattern(alias)
            bindingEntries.add(SimpleBindingElement(declOrPattern, parseInitializer(), popLocation()))

            if (match(TokenType.Comma)) {
                consume()
            } else break
        }

        consume(TokenType.CloseBracket)

        return BindingPatternNode(BindingKind.Array, bindingEntries, popLocation())
    }

    private fun parseInitializer() = if (match(TokenType.Equals)) {
        consume()
        parseExpression(2)
    } else null

    /*
     * DoWhileStatement :
     *     do Statement while ( Expression ) ;
     */
    private fun parseDoWhileStatement(): AstNode {
        pushLocation()
        consume(TokenType.Do)
        val statement = parseStatement()
        consume(TokenType.While)
        consume(TokenType.OpenParen)
        val condition = parseExpression()
        consume(TokenType.CloseParen)
        return DoWhileStatementNode(condition, statement, popLocation()).also { asi(afterDoWhile = true) }
    }

    /*
     * WhileStatement :
     *     while ( Expression ) Statement
     */
    private fun parseWhileStatement(): AstNode {
        pushLocation()
        consume(TokenType.While)
        consume(TokenType.OpenParen)
        val condition = parseExpression(0)
        consume(TokenType.CloseParen)
        val statement = parseStatement()
        return WhileStatementNode(condition, statement, popLocation()).also { asi() }
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
    private fun parseSwitchStatement(): AstNode {
        pushLocation()

        consume(TokenType.Switch)
        consume(TokenType.OpenParen)
        val target = parseExpression(0)
        consume(TokenType.CloseParen)
        consume(TokenType.OpenCurly)

        val clauses = mutableListOf<SwitchClause>()

        while (matchSwitchClause()) {
            pushLocation()

            val clause = if (match(TokenType.Case)) {
                consume()
                val caseTarget = parseExpression(2)
                consume(TokenType.Colon)
                if (matchSwitchClause()) {
                    SwitchClause(caseTarget, null, popLocation())
                } else {
                    SwitchClause(caseTarget, parseStatementList(), popLocation())
                }
            } else {
                consume(TokenType.Default)
                consume(TokenType.Colon)
                if (matchSwitchClause()) {
                    SwitchClause(null, null, popLocation())
                } else {
                    SwitchClause(null, parseStatementList(), popLocation())
                }
            }

            clauses.add(clause)
        }

        consume(TokenType.CloseCurly)

        return SwitchStatementNode(target, clauses, popLocation())
    }

    /*
     * ContinueStatement :
     *     continue ;
     *     continue [no LineTerminator here] LabelIdentifier ;
     */
    private fun parseContinueStatement(): AstNode {
        pushLocation()
        consume(TokenType.Continue)

        val continueToken = lastToken

        if (match(TokenType.Semicolon) || token.afterNewline) {
            if (match(TokenType.Semicolon))
                consume()

            labelStateStack.last().validateContinue(continueToken, null)
            return ContinueStatementNode(null, popLocation())
        }

        if (matchIdentifier()) {
            val identifier = parseIdentifier().processedName
            labelStateStack.last().validateContinue(continueToken, lastToken)
            return ContinueStatementNode(identifier, popLocation()).also { asi() }
        }

        reporter.expected("identifier", tokenType)
    }

    /*
     * BreakStatement :
     *     break ;
     *     break [no LineTerminator here] LabelIdentifier ;
     */
    private fun parseBreakStatement(): AstNode {
        pushLocation()
        consume(TokenType.Break)

        val breakToken = lastToken

        if (match(TokenType.Semicolon) || token.afterNewline) {
            if (match(TokenType.Semicolon))
                consume()

            labelStateStack.last().validateBreak(breakToken, null)
            return BreakStatementNode(null, popLocation())
        }

        if (matchIdentifier()) {
            val identifier = parseIdentifier().processedName
            labelStateStack.last().validateBreak(breakToken, lastToken)
            return BreakStatementNode(identifier, popLocation()).also { asi() }
        }

        reporter.expected("identifier", tokenType)
    }

    /*
     * ReturnStatement :
     *     return ;
     *     return [no LineTerminator here] Expression ;
     */
    private fun parseReturnStatement(): AstNode {
        pushLocation()
        expect(inFunctionContext)
        consume(TokenType.Return)

        if (match(TokenType.Semicolon)) {
            consume()
            return ReturnStatementNode(null, popLocation())
        }

        if (token.afterNewline)
            return ReturnStatementNode(null, popLocation())

        if (tokenType.isExpressionToken)
            return ReturnStatementNode(parseExpression(0), popLocation()).also { asi() }

        reporter.expected("expression", tokenType)
    }

    /*
     * WithStatement :
     *     with ( Expression ) Statement
     */
    private fun parseWithStatement(): AstNode {
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
    private fun parseThrowStatement(): AstNode {
        pushLocation()
        consume(TokenType.Throw)
        if (!tokenType.isExpressionToken)
            reporter.expected("expression", tokenType)
        if (token.afterNewline)
            reporter.throwStatementNewLine()
        return ThrowStatementNode(parseExpression(), popLocation()).also { asi() }
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
    private fun parseTryStatement(): AstNode {
        pushLocation()
        consume(TokenType.Try)
        val tryBlock = parseBlock()

        val catchBlock = if (match(TokenType.Catch)) {
            pushLocation()
            consume()
            val catchParam = if (match(TokenType.OpenParen)) {
                consume()
                val declaration = if (matchIdentifier()) {
                    BindingDeclaration(parseIdentifier())
                } else if (matchBindingPattern()) {
                    parseBindingPattern()
                } else {
                    reporter.at(token).expected("identifier or binding pattern")
                }
                CatchParameter(BindingDeclarationOrPattern(declaration)).also {
                    consume(TokenType.CloseParen)
                }
            } else null
            CatchNode(catchParam, parseBlock(), popLocation())
        } else null

        val finallyBlock = if (match(TokenType.Finally)) {
            consume()
            parseBlock()
        } else null

        if (catchBlock == null && finallyBlock == null)
            reporter.expected(TokenType.Finally, tokenType)

        return TryStatementNode(tryBlock, catchBlock, finallyBlock, popLocation())
    }

    /*
     * DebuggerStatement :
     *     debugger ;
     */
    private fun parseDebuggerStatement(): AstNode {
        pushLocation()
        consume(TokenType.Debugger)
        return DebuggerStatementNode(popLocation()).also { asi() }
    }

    private fun matchSwitchClause() = matches(TokenType.Case, TokenType.Default)

    private fun parseNormalForAndForEachStatement(): AstNode {
        pushLocation()

        consume(TokenType.For)
        if (match(TokenType.Await))
            TODO()

        consume(TokenType.OpenParen)

        var initializer: AstNode? = null

        if (!match(TokenType.Semicolon)) {
            if (tokenType.isExpressionToken) {
                initializer = ExpressionStatementNode(parseExpression(0, false, setOf(TokenType.In)))
                if (matchForEach())
                    return parseForEachStatement(initializer)
            } else if (tokenType.isVariableDeclarationToken) {
                initializer = parseVariableDeclaration(isForEachLoop = true)

                if (matchForEach())
                    return parseForEachStatement(initializer)
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

        return ForStatementNode(initializer, condition, update, body, popLocation())
    }

    // Expects caller to call pushLocation()
    private fun parseForEachStatement(initializer: AstNode): AstNode {
        if ((initializer is VariableDeclarationNode && initializer.declarations.size > 1) ||
            (initializer is LexicalDeclarationNode && initializer.declarations.size > 1)
        ) {
            reporter.forEachMultipleDeclarations()
        }

        val isIn = consume() == TokenType.In
        val rhs = parseExpression(0)
        consume(TokenType.CloseParen)

        val body = parseStatement()

        return if (isIn) {
            ForInNode(initializer, rhs, body, popLocation())
        } else {
            ForOfNode(initializer, rhs, body, popLocation())
        }
    }

    private fun matchForEach() = match(TokenType.In) || (match(TokenType.Identifier) && token.literals == "of")

    /*
     * FunctionDeclaration :
     *     function BindingIdentifier ( FormalParameters ) { FunctionBody }
     *     [+Default] function ( FormalParameters ) { FunctionBody }
     */
    private fun parseFunctionDeclaration(): FunctionDeclarationNode {
        pushLocation()
        val (identifier, params, body, kind) = parseFunctionHelper(isDeclaration = true)
        return FunctionDeclarationNode(identifier!!, params, body, kind, popLocation())
    }

    /*
     * FunctionExpression :
     *     function BindingIdentifier? ( FormalParameters ) { FunctionBody }
     */
    private fun parseFunctionExpression(): AstNode {
        pushLocation()
        val (identifier, params, body, isGenerator) = parseFunctionHelper(isDeclaration = false)
        return FunctionExpressionNode(identifier, params, body, isGenerator, popLocation())
    }

    private data class FunctionTemp(
        val identifier: IdentifierNode?,
        val params: ParameterList,
        val body: BlockNode,
        val type: AOs.FunctionKind,
    )

    private fun parseFunctionHelper(isDeclaration: Boolean): FunctionTemp {
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
            inDefaultContext -> IdentifierNode("default", sourceLocation = SourceLocation(token.start, token.start))
            isDeclaration -> reporter.functionStatementNoName()
            else -> null
        }

        val params = parseFunctionParameters()

        val body = parseFunctionBody(isAsync, isGenerator)

        if (body.hasUseStrict && !params.isSimple())
            reporter.at(body.useStrict!!).invalidUseStrict()

        return FunctionTemp(identifier, params, body, type)
    }

    /*
     * ClassDeclaration :
     *     class BindingIdentifier ClassTail
     *     [+Default] class ClassTail
     */
    private fun parseClassDeclaration(): ClassDeclarationNode {
        pushLocation()
        consume(TokenType.Class)
        val identifier = if (!matchIdentifier()) {
            if (!inDefaultContext)
                reporter.classDeclarationNoName()
            null
        } else parseBindingIdentifier()

        return ClassDeclarationNode(identifier, parseClassNode(), popLocation())
    }

    /*
     * ClassExpression :
     *     class BindingIdentifier? ClassTail
     */
    private fun parseClassExpression(): AstNode {
        pushLocation()
        consume(TokenType.Class)
        val identifier = if (matchIdentifier()) {
            parseBindingIdentifier()
        } else null
        return ClassExpressionNode(identifier, parseClassNode(), popLocation())
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
    private fun parseClassNode(): ClassNode {
        pushLocation()
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

        return ClassNode(superClass, elements, popLocation())
    }

    private fun parseClassElement(): ClassElementNode? {
        if (match(TokenType.Semicolon)) {
            consume()
            return null
        }

        pushLocation()

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

            val identifier = when {
                isAsync -> IdentifierNode("async", sourceLocation = isAsyncToken!!.sourceLocation)
                isStatic -> IdentifierNode("static", sourceLocation = isStaticToken!!.sourceLocation)
                else -> reporter.expected("property name", token.literals)
            }

            name = PropertyName(identifier, PropertyName.Type.Identifier, identifier.sourceLocation)

            return parseClassFieldOrMethod(name, MethodDefinitionNode.Kind.Normal, isStatic)
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

        return parseClassFieldOrMethod(name, kind, isStatic)
    }

    // Expects the caller to call pushLocation()
    private fun parseClassField(name: PropertyName, isStatic: Boolean): ClassFieldNode {
        consume(TokenType.Equals)
        return ClassFieldNode(name, parseExpression(0), isStatic, popLocation())
    }

    // Expects the caller to call pushLocation()
    private fun parseClassMethod(
        name: PropertyName,
        kind: MethodDefinitionNode.Kind,
        isStatic: Boolean
    ): ClassMethodNode {
        return ClassMethodNode(parseMethodDefinition(name, kind), isStatic, popLocation())
    }

    // Expects the caller to call pushLocation()
    private fun parseClassFieldOrMethod(
        name: PropertyName,
        kind: MethodDefinitionNode.Kind,
        isStatic: Boolean
    ): ClassElementNode {
        return if (match(TokenType.Equals)) {
            parseClassField(name, isStatic)
        } else if (match(TokenType.OpenParen)) {
            parseClassMethod(name, kind, isStatic)
        } else if (match(TokenType.Semicolon) || token.afterNewline) {
            // Must be a class field with no initializer
            ClassFieldNode(name, null, isStatic, popLocation())
        } else {
            reporter.at(token).expected("class field initializer or semicolon")
        }
    }

    private fun parseMethodDefinition(name: PropertyName, kind: MethodDefinitionNode.Kind): MethodDefinitionNode {
        pushLocation()
        val params = parseFunctionParameters()
        val body = parseFunctionBody(kind.isAsync, kind.isGenerator)

        if (!params.isSimple() && body.hasUseStrict)
            reporter.at(body.useStrict!!).invalidUseStrict()

        return MethodDefinitionNode(name, params, body, kind, popLocation())
    }

    private fun parseSuperExpression(): AstNode {
        pushLocation()
        consume(TokenType.Super)

        return when (tokenType) {
            TokenType.Period -> {
                consume()
                if (match(TokenType.Hash))
                    reporter.at(token).error("Reeva does not support private identifier")
                if (!match(TokenType.Identifier))
                    reporter.at(token).expected("identifier", tokenType)
                val identifier = parseIdentifier()
                SuperPropertyExpressionNode(identifier, isComputed = false, popLocation())
            }
            TokenType.OpenBracket -> {
                consume()
                val expression = parseExpression(0)
                consume(TokenType.CloseBracket)
                SuperPropertyExpressionNode(expression, isComputed = true, popLocation())
            }
            TokenType.OpenParen -> SuperCallExpressionNode(parseArguments(), popLocation())
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
    private fun parseFunctionParameters(): ParameterList {
        pushLocation()
        consume(TokenType.OpenParen)
        if (match(TokenType.CloseParen)) {
            consume()
            return ParameterList(emptyList(), popLocation())
        }

        val parameters = mutableListOf<Parameter>()

        while (true) {
            if (match(TokenType.CloseParen))
                break

            if (match(TokenType.TriplePeriod)) {
                pushLocation()
                consume()
                val declaration = if (matchIdentifier()) {
                    BindingDeclaration(parseBindingIdentifier())
                } else parseBindingPattern()
                if (!match(TokenType.CloseParen))
                    reporter.paramAfterRest()

                parameters.add(RestParameter(BindingDeclarationOrPattern(declaration), popLocation()))
                break
            }

            if (matchBindingPattern()) {
                pushLocation()
                parameters.add(BindingParameter(parseBindingPattern(), parseInitializer(), popLocation()))
            } else if (!matchIdentifier()) {
                reporter.at(token).expected("identifier")
            } else {
                pushLocation()
                parameters.add(SimpleParameter(parseBindingIdentifier(), parseInitializer(), popLocation()))
            }

            if (!match(TokenType.Comma))
                break
            consume()
        }

        consume(TokenType.CloseParen)
        return ParameterList(parameters, popLocation())
    }

    private fun matchIdentifier() = match(TokenType.Identifier) ||
        (!inYieldContext && !isStrict && match(TokenType.Yield)) ||
        (!inAsyncContext && !isStrict && match(TokenType.Await))

    private fun matchIdentifierName(): Boolean {
        return match(TokenType.Identifier) || tokenType.category == TokenType.Category.Keyword
    }

    private fun parseIdentifierReference() = IdentifierReferenceNode(parseIdentifier())

    private fun parseIdentifier(): IdentifierNode {
        pushLocation()
        expect(matchIdentifierName())
        val token = this.token
        val identifier = token.literals
        consume()

        val unescaped = unescapeString(identifier)

        if (!unescaped[0].let { it == '$' || it == '_' || it.isIdStart() })
            reporter.at(token).identifierInvalidEscapeSequence(identifier)

        if (!unescaped.drop(1).all { it == '$' || it.isIdContinue() || it == '\u200c' || it == '\u200d' })
            reporter.at(token).identifierInvalidEscapeSequence(identifier)

        return IdentifierNode(unescaped, identifier, popLocation())
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

    private fun consumeStringLiteral(): StringLiteralNode? {
        val node = if (match(TokenType.StringLiteral)) {
            pushLocation()
            val str = token.literals
            consume()
            StringLiteralNode(str, popLocation())
        } else null

        // TODO: Why is this here? StringLiteral does not specify ASI
        if (match(TokenType.Semicolon))
            consume()

        return node
    }

    private fun checkForAndConsumeUseStrict(): AstNode? {
        var useStrictDirective: StringLiteralNode? = null

        while (true) {
            val stringLiteral = consumeStringLiteral() ?: return useStrictDirective
            if (stringLiteral.value == "use strict")
                useStrictDirective = stringLiteral
        }
    }

    private fun parseBlock(): BlockNode {
        pushLocation()
        consume(TokenType.OpenCurly)
        val prevIsStrict = isStrict
        val useStrict = checkForAndConsumeUseStrict()

        if (useStrict != null)
            this.isStrict = true

        val statements = parseStatementList()
        consume(TokenType.CloseCurly)
        this.isStrict = prevIsStrict

        return BlockNode(statements, useStrict, popLocation())
    }

    private fun matchSecondaryExpression() = tokenType.isSecondaryToken && if (matches(TokenType.Inc, TokenType.Dec)) {
        !token.afterNewline
    } else true

    private fun parseExpression(
        minPrecedence: Int = 0,
        leftAssociative: Boolean = false,
        excludedTokens: Set<TokenType> = emptySet(),
    ): AstNode {
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

        return if (match(TokenType.Comma) && minPrecedence <= 1) {
            pushLocation()
            val expressions = mutableListOf(expression)
            while (match(TokenType.Comma)) {
                consume()
                expressions.add(parseExpression(2))
            }
            CommaExpressionNode(expressions, popLocation())
        } else expression
    }

    private fun parseSecondaryExpression(
        lhs: AstNode,
        minPrecedence: Int,
        leftAssociative: Boolean,
    ): AstNode {
        fun getLocation() = SourceLocation(lhs.sourceLocation.start, lastToken.end)

        fun makeBinaryExpr(op: BinaryOperator): AstNode {
            consume()
            return BinaryExpressionNode(lhs, parseExpression(minPrecedence, leftAssociative), op, getLocation())
        }

        fun makeAssignExpr(op: BinaryOperator?): AstNode {
            consume()
            validateAssignmentTarget(lhs)
            return AssignmentExpressionNode(lhs, parseExpression(minPrecedence, leftAssociative), op, getLocation())
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
                    getLocation(),
                )
            }
            TokenType.OpenBracket -> {
                consume()
                MemberExpressionNode(
                    lhs,
                    parseExpression(),
                    MemberExpressionNode.Type.Computed,
                    getLocation(),
                ).also {
                    consume(TokenType.CloseBracket)
                }
            }
            TokenType.Inc -> {
                consume()
                validateAssignmentTarget(lhs)
                UpdateExpressionNode(lhs, isIncrement = true, isPostfix = true, getLocation())
            }
            TokenType.Dec -> {
                consume()
                validateAssignmentTarget(lhs)
                UpdateExpressionNode(lhs, isIncrement = false, isPostfix = true, getLocation())
            }
            TokenType.QuestionMark -> parseConditional(lhs)
            TokenType.OptionalChain -> {
                // TODO: Disallow "new Foo?.a"
                parseOptionalChain(lhs)
            }
            else -> unreachable()
        }
    }

    private fun parseConditional(lhs: AstNode): AstNode {
        consume(TokenType.QuestionMark)
        val ifTrue = parseExpression(2)
        consume(TokenType.Colon)
        val ifFalse = parseExpression(2)
        return ConditionalExpressionNode(lhs, ifTrue, ifFalse, SourceLocation(lhs.sourceLocation.start, lastToken.end))
    }

    private fun parseOptionalChain(base_: AstNode): AstNode {
        val (base, parts) = if (base_ is OptionalChainNode) {
            base_.base to base_.parts.toMutableList()
        } else base_ to mutableListOf()

        do {
            when (tokenType) {
                TokenType.OptionalChain -> {
                    consume()
                    when (tokenType) {
                        TokenType.OpenParen -> {
                            pushLocation()
                            parts.add(OptionalCallChain(parseArguments(), isOptional = true, popLocation()))
                        }
                        TokenType.OpenBracket -> {
                            pushLocation()
                            consume()
                            val expr = parseExpression()
                            consume(TokenType.CloseBracket)
                            parts.add(OptionalComputedAccessChain(expr, isOptional = true, popLocation()))
                        }
                        TokenType.TemplateLiteralStart -> reporter.at(token).templateLiteralAfterOptionalChain()
                        else -> {
                            if (!matchIdentifierName())
                                reporter.at(token).expected("identifier", tokenType)
                            pushLocation()
                            parts.add(OptionalAccessChain(parseIdentifier(), isOptional = true, popLocation()))
                        }
                    }
                }
                TokenType.OpenParen -> {
                    pushLocation()
                    parts.add(OptionalCallChain(parseArguments(), isOptional = false, popLocation()))
                }
                TokenType.Period -> {
                    pushLocation()
                    consume()
                    if (!matchIdentifierName())
                        reporter.at(token).expected("identifier", tokenType)
                    parts.add(OptionalAccessChain(parseIdentifier(), isOptional = false, popLocation()))
                }
                TokenType.TemplateLiteralStart -> reporter.at(token).templateLiteralAfterOptionalChain()
                TokenType.OpenBracket -> {
                    pushLocation()
                    consume()
                    val expr = parseExpression()
                    consume(TokenType.CloseBracket)
                    parts.add(OptionalComputedAccessChain(expr, isOptional = false, popLocation()))
                }
                else -> break
            }
        } while (!isDone)

        return OptionalChainNode(base, parts, SourceLocation(base.sourceLocation.start, lastToken.end))
    }

    private fun parseCallExpression(lhs: AstNode, isOptional: Boolean): AstNode {
        return CallExpressionNode(
            lhs,
            parseArguments(),
            isOptional,
            SourceLocation(lhs.sourceLocation.start, lastToken.end),
        )
    }

    private fun parseNewExpression(): AstNode {
        pushLocation()
        consume(TokenType.New)
        if (has(2) && match(TokenType.Period) && peek()?.type == TokenType.Identifier) {
            consume()
            consume()
            if (lastToken.literals != "target")
                reporter.at(lastToken).invalidNewMetaProperty()
            return NewTargetNode(popLocation())
        }
        val target = parseExpression(TokenType.New.operatorPrecedence, excludedTokens = setOf(TokenType.OpenParen))
        val arguments = if (match(TokenType.OpenParen)) parseArguments() else emptyList()
        return NewExpressionNode(target, arguments, popLocation())
    }

    private fun parseArguments(): List<ArgumentNode> {
        consume(TokenType.OpenParen)

        val arguments = mutableListOf<ArgumentNode>()

        while (tokenType.isExpressionToken || match(TokenType.TriplePeriod)) {
            pushLocation()
            val isSpread = if (match(TokenType.TriplePeriod)) {
                consume()
                true
            } else false
            arguments.add(ArgumentNode(parseExpression(2), isSpread, popLocation()))
            if (!match(TokenType.Comma))
                break
            consume()
        }

        consume(TokenType.CloseParen)

        return arguments
    }

    private fun parseYieldExpression(): AstNode {
        expect(inYieldContext)

        pushLocation()
        consume(TokenType.Yield)
        val isYieldStar = if (match(TokenType.Mul)) {
            consume()
            true
        } else false

        if (match(TokenType.Semicolon)) {
            consume()
            return YieldExpressionNode(null, isYieldStar, popLocation())
        }

        if (token.afterNewline)
            return YieldExpressionNode(null, isYieldStar, popLocation())

        if (tokenType.isExpressionToken)
            return YieldExpressionNode(parseExpression(0), isYieldStar, popLocation())

        if (isYieldStar)
            reporter.expected("expression", tokenType)

        return YieldExpressionNode(null, generatorYield = false, popLocation())
    }

    private fun parseParenthesizedExpression(): AstNode {
        consume(TokenType.OpenParen)
        val expr = parseExpression(0)
        consume(TokenType.CloseParen)
        return expr
    }

    private fun parsePrimaryExpression(): AstNode {
        if (tokenType.isUnaryToken)
            return parseUnaryExpression()

        return when (tokenType) {
            TokenType.OpenParen -> {
                val index = token.start.index
                if (index !in nonArrowFunctionParens) {
                    val arrow = tryParseArrowFunction()
                    if (arrow != null)
                        return arrow
                    nonArrowFunctionParens.add(index)
                }

                parseParenthesizedExpression()
            }
            TokenType.This -> {
                consume()
                ThisLiteralNode(lastToken.sourceLocation)
            }
            TokenType.Class -> parseClassExpression()
            TokenType.Super -> parseSuperExpression()
            TokenType.Identifier -> {
                if (peek()?.type == TokenType.Arrow) {
                    val arrow = tryParseArrowFunction()
                    if (arrow != null)
                        return arrow
                }
                parseIdentifierReference()
            }
            TokenType.NumericLiteral -> parseNumericLiteral()
            TokenType.BigIntLiteral -> parseBigIntLiteral()
            TokenType.True -> {
                consume()
                TrueNode(lastToken.sourceLocation)
            }
            TokenType.False -> {
                consume()
                FalseNode(lastToken.sourceLocation)
            }
            TokenType.Async, TokenType.Function -> parseFunctionExpression()
            TokenType.Await -> parseAwaitExpression()
            TokenType.StringLiteral -> parseStringLiteral()
            TokenType.NullLiteral -> {
                consume()
                NullLiteralNode(lastToken.sourceLocation)
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

    private fun parseRegExpLiteral(): RegExpLiteralNode {
        pushLocation()

        val source = token.literals.drop(1).dropLast(1)
        consume(TokenType.RegExpLiteral)

        val flags = if (match(TokenType.RegexFlags)) {
            token.literals.also { consume() }
        } else ""

        return try {
            RegExpLiteralNode(source, flags, AOs.makeRegExp(source, flags), popLocation())
        } catch (e: RegExpSyntaxError) {
            reporter.at(token).error(e.message!!)
        }
    }

    private fun parseAwaitExpression(): AstNode {
        pushLocation()
        consume(TokenType.Await)
        return AwaitExpressionNode(parseExpression(2), popLocation())
    }

    private fun parseStringLiteral(): StringLiteralNode {
        pushLocation()
        consume(TokenType.StringLiteral)
        return StringLiteralNode(unescapeString(lastToken.literals), popLocation())
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

    private fun parseTemplateLiteral(): AstNode {
        pushLocation()
        consume(TokenType.TemplateLiteralStart)

        val expressions = mutableListOf<AstNode>()
        fun addEmptyString() = expressions.add(StringLiteralNode("", token.sourceLocation))

        if (!match(TokenType.TemplateLiteralString))
            addEmptyString()

        while (!matches(TokenType.TemplateLiteralEnd, TokenType.UnterminatedTemplateLiteral)) {
            if (match(TokenType.TemplateLiteralString)) {
                consume()
                expressions.add(StringLiteralNode(unescapeString(lastToken.literals), token.sourceLocation))
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

        return TemplateLiteralNode(expressions, popLocation())
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
    private fun parseObjectLiteral(): AstNode {
        pushLocation()
        consume(TokenType.OpenCurly)

        val properties = mutableListOf<Property>()

        while (!match(TokenType.CloseCurly)) {
            properties.add(parseObjectProperty())
            if (match(TokenType.Comma)) {
                consume()
            } else break
        }

        consume(TokenType.CloseCurly)

        return ObjectLiteralNode(properties, popLocation())
    }

    /*
     * PropertyDefinition :
     *     IdentifierReference
     *     CoverInitializedName
     *     PropertyName : AssignmentExpression
     *     MethodDefinition
     *     ... AssignmentExpression
     */
    private fun parseObjectProperty(): Property {
        pushLocation()

        if (match(TokenType.TriplePeriod)) {
            consume()
            return SpreadProperty(parseExpression(2), popLocation())
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
            val methoDefinitionNode = MethodDefinitionNode(methodName, params, body, type, popLocation())
            return MethodProperty(methoDefinitionNode)
        }

        if (matches(TokenType.Comma, TokenType.CloseCurly)) {
            if (name.type != PropertyName.Type.Identifier)
                reporter.at(name).invalidShorthandProperty()

            val identifierNode = name.expression as IdentifierNode

            // As this is a shorthand property, it is also a binding identifier, and must
            // be validated as such
            validateBindingIdentifier(identifierNode)

            val node = IdentifierReferenceNode(identifierNode)

            return ShorthandProperty(node, popLocation())
        }

        consume(TokenType.Colon)
        val expression = parseExpression(2)

        return KeyValueProperty(name, expression, popLocation())
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
    private fun parsePropertyName(): PropertyName {
        pushLocation()
        return when {
            match(TokenType.OpenBracket) -> {
                consume()
                val expr = parseExpression(0)
                consume(TokenType.CloseBracket)
                PropertyName(expr, PropertyName.Type.Computed, popLocation())
            }
            match(TokenType.StringLiteral) -> {
                PropertyName(parseStringLiteral(), PropertyName.Type.String, popLocation())
            }
            match(TokenType.NumericLiteral) -> {
                PropertyName(parseNumericLiteral(), PropertyName.Type.Number, popLocation())
            }
            matchIdentifierName() -> {
                PropertyName(parseIdentifier(), PropertyName.Type.Identifier, popLocation())
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
    private fun parseArrayLiteral(): ArrayLiteralNode {
        pushLocation()
        consume(TokenType.OpenBracket)
        if (match(TokenType.CloseBracket)) {
            consume()
            return ArrayLiteralNode(emptyList(), popLocation())
        }

        val elements = mutableListOf<ArrayElementNode>()

        while (!match(TokenType.CloseBracket)) {
            pushLocation()
            if (match(TokenType.Comma)) {
                consume()
                elements.add(ArrayElementNode(null, ArrayElementNode.Type.Elision, popLocation()))
                continue
            }

            val isSpread = if (match(TokenType.TriplePeriod)) {
                consume()
                true
            } else false

            if (!tokenType.isExpressionToken)
                reporter.expected("expression", tokenType)

            val expression = parseExpression(2)

            elements.add(ArrayElementNode(
                expression,
                if (isSpread) {
                    ArrayElementNode.Type.Spread
                } else ArrayElementNode.Type.Normal,
                popLocation(),
            ))

            if (match(TokenType.Comma)) {
                consume()
            } else if (!match(TokenType.CloseBracket)) {
                break
            }
        }

        consume(TokenType.CloseBracket)
        return ArrayLiteralNode(elements, popLocation())
    }

    private fun parseNumericLiteral(): NumericLiteralNode {
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

        return NumericLiteralNode(numericToken.doubleValue(), lastToken.sourceLocation)
    }

    private fun parseBigIntLiteral(): BigIntLiteralNode {
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

        return BigIntLiteralNode(value.dropLast(1).replace("_", ""), mode, lastToken.sourceLocation)
    }

    private fun tryParseArrowFunction(): ArrowFunctionNode? {
        val savedCursor = tokenCursor
        val savedLocationDepth = locationStack.size
        pushLocation()

        val parameters = try {
            val parameters = if (match(TokenType.OpenParen)) {
                parseFunctionParameters()
            } else {
                pushLocation()
                val parameter = SimpleParameter(parseBindingIdentifier(), null, popLocation())
                ParameterList(listOf(parameter), parameter.sourceLocation)
            }

            if (!match(TokenType.Arrow)) {
                tokenCursor = savedCursor
                popLocation()
                return null
            }

            parameters
        } catch (e: ParsingException) {
            tokenCursor = savedCursor
            while (locationStack.size > savedLocationDepth)
                locationStack.removeLast()
            return null
        }

        if (token.afterNewline)
            reporter.arrowFunctionNewLine()

        consume()

        // TODO: Async/Generator functions
        val body = if (match(TokenType.OpenCurly)) {
            parseFunctionBody(isAsync = false, isGenerator = false)
        } else parseExpression(2)

        return ArrowFunctionNode(parameters, body, AOs.FunctionKind.Normal, popLocation())
    }

    private fun parseUnaryExpression(): AstNode {
        pushLocation()
        val type = consume()
        val expression = parseExpression(type.operatorPrecedence, type.leftAssociative)

        return when (type) {
            TokenType.Inc -> {
                validateAssignmentTarget(expression)
                UpdateExpressionNode(expression, isIncrement = true, isPostfix = false, popLocation())
            }
            TokenType.Dec -> {
                validateAssignmentTarget(expression)
                UpdateExpressionNode(expression, isIncrement = false, isPostfix = false, popLocation())
            }
            TokenType.Not -> UnaryExpressionNode(expression, UnaryOperator.Not, popLocation())
            TokenType.BitwiseNot -> UnaryExpressionNode(expression, UnaryOperator.BitwiseNot, popLocation())
            TokenType.Add -> UnaryExpressionNode(expression, UnaryOperator.Plus, popLocation())
            TokenType.Sub -> UnaryExpressionNode(expression, UnaryOperator.Minus, popLocation())
            TokenType.Typeof -> UnaryExpressionNode(expression, UnaryOperator.Typeof, popLocation())
            TokenType.Void -> UnaryExpressionNode(expression, UnaryOperator.Void, popLocation())
            TokenType.Delete -> UnaryExpressionNode(expression, UnaryOperator.Delete, popLocation())
            else -> unreachable()
        }
    }

    private fun parseIfStatement(): AstNode {
        pushLocation()
        consume(TokenType.If)
        consume(TokenType.OpenParen)
        val condition = parseExpression()
        consume(TokenType.CloseParen)

        val trueBlock = parseStatement()

        if (!match(TokenType.Else))
            return IfStatementNode(condition, trueBlock, null, popLocation())

        consume()
        if (match(TokenType.If))
            return IfStatementNode(condition, trueBlock, parseIfStatement(), popLocation())

        val falseBlock = parseStatement()

        return IfStatementNode(condition, trueBlock, falseBlock, popLocation())
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

    private fun pushLocation() {
        locationStack.add(sourceStart)
    }

    private fun popLocation() = SourceLocation(locationStack.removeLast(), lastToken.end)

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

    companion object {
        private val strictProtectedNames = setOf(
            "implements", "interface", "let", "package", "private", "protected", "public", "static", "yield"
        )
    }
}
