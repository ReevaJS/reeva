package me.mattco.reeva.parser

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.lexer.Lexer
import me.mattco.reeva.lexer.SourceLocation
import me.mattco.reeva.lexer.Token
import me.mattco.reeva.lexer.TokenType
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.builtins.regexp.JSRegExpObject
import me.mattco.reeva.utils.*

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

    private var inBreakContext = false
    private var inContinueContext = false
    private var isStrict = false

    private var activeLabels = mutableListOf<String>()

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
        val statementList = parseStatementList(setStrict = true)
        if (!isDone)
            unexpected("token ${token.value}")
        if (stateStack.size != 1)
            throw IllegalStateException("parseScript ended with ${stateStack.size - 1} extra states on the stack")
        return ScriptNode(statementList)
    }

    fun parseModule(): ModuleNode {
        goalSymbol = GoalSymbol.Module
        isStrict = true

        val body = mutableListOf<StatementNode>()
        while (true) {
            val import = parseImportDeclaration()
            if (import != null) {
                body.add(import)
                continue
            }

            val export = parseExportDeclaration()
            if (export != null) {
                body.add(export)
                continue
            }

            val statement = parseStatementListItem()
            if (statement != null) {
                body.add(statement)
                continue
            }

            break
        }

        if (!isDone)
            unexpected("token ${token.value}")
        if (stateStack.size != 1)
            throw IllegalStateException("parseModule ended with ${stateStack.size - 1} extra states on the stack")

        return ModuleNode(StatementList(body))
    }

    fun parseDynamicFunction(kind: Operations.FunctionKind): GenericFunctionDeclarationNode? {
        val result = when (kind) {
            Operations.FunctionKind.Normal -> parseFunctionDeclaration()
            Operations.FunctionKind.Generator -> parseGeneratorDeclaration()
            Operations.FunctionKind.Async -> parseAsyncFunctionDeclaration()
            Operations.FunctionKind.AsyncGenerator -> parseAsyncGeneratorDeclaration()
        } ?: return null

        if (result.body.containsUseStrictDirective()) {
            if (!result.parameters.isSimple()) {
                syntaxError("dynamic strict Function must have a simple parameter list")
                return null
            }

            if (result.parameters.containsDuplicates()) {
                syntaxError("dynamic strict Function cannot contain duplicate parameter bindings")
                return null
            }
        }

        if (result.parameters.containsAny<SuperCallExpressionNode>() || result.body.containsAny<SuperCallExpressionNode>()) {
            syntaxError("dynamic Function object cannot contain a super call")
            return null
        }

        if (result.parameters.containsAny<SuperPropertyExpressionNode>() || result.body.containsAny<SuperPropertyExpressionNode>()) {
            syntaxError("dynamic Function object cannot contain a super property access")
            return null
        }

        val isGenerator = kind == Operations.FunctionKind.Generator || kind == Operations.FunctionKind.AsyncGenerator
        val isAsync = kind == Operations.FunctionKind.Async || kind == Operations.FunctionKind.AsyncGenerator

        if (isGenerator && result.parameters.containsAny<YieldExpressionNode>()) {
            syntaxError("dynamic generator Function cannot contain a yield expression in its parameters")
            return null
        }

        if (isAsync && result.parameters.containsAny<AwaitExpressionNode>()) {
            syntaxError("dynamic async Function cannot contain an await expression in its parameters")
            return null
        }

        return result
    }

    private fun parseStatementList(setStrict: Boolean = false): StatementList {
        val statements = mutableListOf<StatementNode>()

        var statement = parseStatementListItem() ?: return StatementList()
        statements.add(statement)

        if (setStrict && !isStrict && statement is ExpressionStatementNode &&
            statement.node.let { it is StringLiteralNode && it.value == "use strict" }
        ) {
            isStrict = true
        }

        while (true) {
            statement = parseStatementListItem() ?: break
            statements.add(statement)
        }

        return StatementList(statements)
    }

    private fun parseStatementListItem(): StatementNode? {
        return parseStatement() ?: parseDeclarationNode()
    }

    private fun parseStatement(): StatementNode? {
        return parseLabelledStatement() ?:
            parseBlockStatement() ?:
            parseVariableStatement() ?:
            parseEmptyStatement() ?:
            parseExpressionStatement() ?:
            parseIfStatement() ?:
            parseBreakableStatement() ?:
            parseContinueStatement() ?:
            parseBreakStatement() ?:
            (if (inReturnContext) parseReturnStatement() else null) ?:
            parseWithStatement() ?:
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

        if (statements.isNotEmpty()) {
            val varNames = statements.declaredVarNames()
            val lexNames = statements.declaredLexNames()

            val lexDuplicates = lexNames.duplicates()
            if (lexDuplicates.isNotEmpty()) {
                syntaxError("cannot redeclare lexical declaration \"${lexDuplicates.first()}\"")
                return null
            }

            val intersection = lexDuplicates.intersect(varNames)
            if (intersection.isNotEmpty()) {
                syntaxError("cannot redeclare non-lexical declaration \"${intersection.first()}\" as lexical")
                return null
            }
        }

        return BlockNode(statements)
    }

    private fun parseVariableStatement(): VariableDeclarationNode? {
        if (tokenType != TokenType.Var)
            return null
        consume()

        val list = withIn { parseDeclarationList(isConst = false) }

        if (list.isEmpty()) {
            expected("one or more variable declaration")
            consume()
            return null
        }

        automaticSemicolonInsertion()

        return VariableDeclarationNode(list)
    }

    private fun parseDeclaration(isConst: Boolean): Declaration? {
        // TODO(BindingPattern)
        val identifier = parseBindingIdentifier() ?: return null
        val initializer = parseInitializer()
        return Declaration(isConst, identifier, initializer)
    }

    private fun parseDeclarationList(isConst: Boolean): List<Declaration> {
        val list = mutableListOf<Declaration>()
        list.add(parseDeclaration(isConst) ?: return list)

        while (tokenType == TokenType.Comma) {
            saveState()
            consume()
            val decl = parseDeclaration(isConst)
            if (decl == null) {
                loadState()
                break
            }
            discardState()
            list.add(decl)
        }

        return list
    }

    private fun parseInitializer(): ExpressionNode? {
        if (tokenType != TokenType.Equals)
            return null

        saveState()
        consume()
        val expr = parseAssignmentExpression() ?: run {
            loadState()
            return null
        }
        discardState()
        return expr
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

                val restoreBreakContext = ::inBreakContext.temporaryChange(true)
                val restoreContinueContext = ::inContinueContext.temporaryChange(true)

                val statement = parseStatement() ?: run {
                    expected("statement")
                    consume()
                    return null
                }

                restoreBreakContext()
                restoreContinueContext()

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
                val restoreBreakContext = ::inBreakContext.temporaryChange(true)
                val restoreContinueContext = ::inContinueContext.temporaryChange(true)
                val statement = parseStatement() ?: run {
                    expected("statement")
                    consume()
                    return null
                }
                restoreBreakContext()
                restoreContinueContext()

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
        val initializer = if (tokenType == TokenType.Semicolon) {
            consume()
            null
        } else (parseStatement() ?: return null).also {
            consume(TokenType.Semicolon)
        }

        val condition = withIn { parseExpression() }
        consume(TokenType.Semicolon)
        val incrementer = withIn { parseExpression() }
        consume(TokenType.CloseParen)
        val restoreBreakContext = ::inBreakContext.temporaryChange(true)
        val restoreContinueContext = ::inContinueContext.temporaryChange(true)
        val body = parseStatement() ?: run {
            expected("statement")
            consume()
            return null
        }
        restoreBreakContext()
        restoreContinueContext()

        if (initializer is LexicalDeclarationNode) {
            val intersection = initializer.declaredLexNames().intersect(body.declaredVarNames())
            if (intersection.isNotEmpty()) {
                syntaxError("for-loop initializer's lexical declaration of \"${intersection.first()}\" clashes with a " +
                    "variable declaration in the for-loop's body")
                return null
            }
        }

        return ForStatementNode(initializer, condition, incrementer, body)
    }

    private fun parseForInOfStatement(): StatementNode? {
        // TODO
        return null

//        val initializer = when (tokenType) {
//            TokenType.Var -> {
//                consume()
//                parseBindingIdentifier()?.let(::ForBindingNode) ?: run {
//                    expected("identifier")
//                    consume()
//                    return null
//                }
//            }
//            TokenType.Let, TokenType.Const -> {
//                val isConst = consume().type == TokenType.Const
//                val identifier = parseBindingIdentifier() ?: run {
//                    expected("identifier")
//                    consume()
//                    return null
//                }
//                ForDeclarationNode(isConst, ForBindingNode(identifier))
//            }
//            else -> {
//                saveState()
//                parseLeftHandSideExpression()?.also { discardState() } ?: run {
//                    loadState()
//                    return null
//                }
//            }
//        }
//
//        val isIn = when (tokenType) {
//            TokenType.In -> {
//                consume()
//                true
//            }
//            TokenType.Of -> {
//                consume()
//                false
//            }
//            else -> {
//                expected("'in' or 'of'")
//                consume()
//                return null
//            }
//        }
//
//        val expression = if (isIn) {
//            withIn { parseExpression() } ?: run {
//                expected("expression")
//                consume()
//                return null
//            }
//        } else {
//            withIn { parseAssignmentExpression() } ?: run {
//                expected("expression")
//                consume()
//                return null
//            }
//        }
//
//        consume(TokenType.CloseParen)
//        val restoreBreakContext = ::inBreakContext.temporaryChange(true)
//        val restoreContinueContext = ::inContinueContext.temporaryChange(true)
//        val body = parseStatement() ?: run {
//            expected("statement")
//            consume()
//            return null
//        }
//        restoreBreakContext()
//        restoreContinueContext()
//
//        if (initializer.let { it !is ObjectLiteralNode && it !is ArrayLiteralNode && it !is ForBindingNode &&
//                it !is ForDeclarationNode && it.assignmentTargetType() != ASTNode.AssignmentTargetType.Simple }
//        ) {
//            syntaxError("for-${if (isIn) "in" else "of"} loop has an invalid left-hand-side")
//            return null
//        }
//
//        return if (isIn) {
//            ForInNode(initializer, expression, body)
//        } else {
//            ForOfNode(initializer, expression, body)
//        }
    }

    private fun parseSwitchStatement(): StatementNode? {
        if (tokenType != TokenType.Switch)
            return null

        consume()
        consume(TokenType.OpenParen)

        val target = withIn {
            parseExpression() ?: run {
                expected("expression")
                consume()
                return null
            }
        }

        consume(TokenType.CloseParen)
        consume(TokenType.OpenCurly)

        val clauses = mutableListOf<SwitchClause>()

        while (true) {
            if (tokenType == TokenType.Case) {
                consume()
                val expr = parseExpression() ?: run {
                    expected("expression")
                    consume()
                    return null
                }
                consume(TokenType.Colon)
                val restoreBreakContext = ::inBreakContext.temporaryChange(true)
                clauses.add(SwitchClause(expr, parseStatementList()))
                restoreBreakContext()
            } else if (tokenType == TokenType.Default) {
                consume()
                consume(TokenType.Colon)
                val restoreBreakContext = ::inBreakContext.temporaryChange(true)
                clauses.add(SwitchClause(null, parseStatementList()))
                restoreBreakContext()
            } else break
        }

        consume(TokenType.CloseCurly)

        val switchClauses = SwitchClauseList(clauses)
        val lexicalNames = switchClauses.declaredLexNames()
        val lexicalDuplicates = lexicalNames.duplicates()
        if (lexicalDuplicates.isNotEmpty()) {
            syntaxError("cannot redeclare lexical declaration \"${lexicalDuplicates.first()}\"")
            return null
        }

        val varNames = switchClauses.declaredVarNames()
        val intersection = lexicalNames.intersect(varNames)
        if (intersection.isNotEmpty()) {
            syntaxError("cannot redeclare non-lexical declaration \"${intersection.first()}\" as lexical")
            return null
        }

        return SwitchStatementNode(target, switchClauses)
    }

    private fun parseContinueStatement(): StatementNode? {
        if (tokenType != TokenType.Continue)
            return null
        consume()

        if (!inContinueContext) {
            syntaxError("cannot continue outside of a loop")
            return null
        }

        if (Lexer.lineTerminators.any { it in token.trivia}) {
            automaticSemicolonInsertion()
            return ContinueStatementNode(null)
        }

        val label = parseLabelIdentifier()
        automaticSemicolonInsertion()
        if (label != null && label !in activeLabels) {
            syntaxError("label \"$label\" not found in the current scope")
            return null
        }
        return ContinueStatementNode(label)
    }

    private fun parseBreakStatement(): StatementNode? {
        if (tokenType != TokenType.Break)
            return null

        consume()

        if (!inBreakContext) {
            syntaxError("cannot break outside of a loop or switch statement")
            return null
        }

        if (Lexer.lineTerminators.any { it in token.trivia}) {
            automaticSemicolonInsertion()
            return BreakStatementNode(null)
        }
        val label = parseLabelIdentifier()
        automaticSemicolonInsertion()
        if (label != null && label !in activeLabels) {
            syntaxError("label \"$label\" not found in the current scope")
            return null
        }
        return BreakStatementNode(label)
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
        if (tokenType != TokenType.Identifier || !has(1) || peek(1).type != TokenType.Colon)
            return null

        val label = parseBindingIdentifier()?.identifierName ?: return null
        activeLabels.add(label)
        consume(TokenType.Colon)
        val statement = parseStatement() ?: run {
            expected("statement")
            consume()
            return null
        }
        activeLabels.remove(label)
        return LabelledStatementNode(label, statement)
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

        if (parameter != null) {
            // TODO(BindingPattern): will require more static rules; check spec here
            val blockNames = block.declaredVarNames() + block.declaredLexNames()
            if (parameter.identifierName in blockNames) {
                syntaxError("catch block redefines the catch parameter name \"${parameter.identifierName}\"")
                return null
            }
        }

        return CatchNode(parameter, block)
    }

    private fun parseDebuggerStatement(): StatementNode? {
        if (tokenType != TokenType.Debugger)
            return null
        consume()
        automaticSemicolonInsertion()
        return DebuggerStatementNode
    }

    private fun parseDeclarationNode(): StatementNode? {
        return parseFunctionDeclaration() ?:
            parseGeneratorDeclaration() ?:
            parseAsyncFunctionDeclaration() ?:
            parseAsyncGeneratorDeclaration() ?:
            parseClassDeclaration() ?:
            withIn { parseLexicalDeclaration() }
    }

    private fun parseFunctionDeclaration(): FunctionDeclarationNode? {
        if (tokenType != TokenType.Function)
            return null

        consume()
        val identifier = if (tokenType == TokenType.OpenParen) {
            // TODO(Export): Function inside default exports are considered declarations,
            // and do not have to have a name
            expected("identifier")
            return null
        } else {
            parseBindingIdentifier().also { consume(TokenType.OpenParen) } ?: run {
                expected("identifier")
                consume()
                return null
            }
        }

        val parameters = parseFunctionParameters()

        consume(TokenType.CloseParen)
        consume(TokenType.OpenCurly)
        val body = functionBoundary(::parseFunctionBody)
        consume(TokenType.CloseCurly)

        return FunctionDeclarationNode(identifier, parameters, StatementList(body))
    }

    private fun parseFunctionParameters(): ParameterList {
        if (tokenType == TokenType.CloseParen)
            return ParameterList()

        val parameters = mutableListOf<Parameter>()

        while (true) {
            val param = parseFunctionParameter() ?: break
            parameters.add(param)
            if (param.isRest)
                break
            if (tokenType != TokenType.Comma)
                break
            consume()
        }

        return ParameterList(parameters)
    }

    private fun parseFunctionParameter(): Parameter? {
        val isRest = if (tokenType == TokenType.TripleDot) {
            consume()
            true
        } else false

        // TODO(BindingPattern)
        val identifier = parseBindingIdentifier() ?: run {
            if (isRest)
                expected("identifier")
            return null
        }

        val initializer = if (tokenType == TokenType.Equals) {
            if (isRest) {
                syntaxError("function rest parameter cannot have initializer")
                return null
            }
            consume()
            parseExpression() ?: run {
                expected("expression")
                return null
            }
        } else null

        return Parameter(identifier, initializer, isRest)
    }

    private fun parseFunctionBody(): StatementList {
        return withReturn { parseStatementList(setStrict = true) }
    }

    private fun parseGeneratorDeclaration(): GenericFunctionDeclarationNode? {
        // TODO
        return null
    }

    private fun parseAsyncFunctionDeclaration(): GenericFunctionDeclarationNode? {
        // TODO
        return null
    }

    private fun parseAsyncGeneratorDeclaration(): GenericFunctionDeclarationNode? {
        // TODO
        return null
    }

    private fun parseClassDeclaration(): ClassDeclarationNode? {
        return ClassDeclarationNode(parseClassNode(isDeclaration = true) ?: return null)
    }

    private fun parseClassNode(isDeclaration: Boolean): ClassNode? {
        if (tokenType != TokenType.Class)
            return null

        val identifier = parseBindingIdentifier() ?: run {
            if (!inDefaultContext || !isDeclaration) {
                expected("identifier")
                consume()
                return null
            }
            null
        }

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
            if (element == EmptyClassElementNode)
                continue
            elements.add(element)
        }

        consume(TokenType.CloseCurly)

        return ClassNode(identifier, heritage, elements)
    }

    private fun parseClassElement(): ClassElementNode? {
        if (tokenType == TokenType.Semicolon) {
            consume()
            return EmptyClassElementNode
        }

        val isStatic = tokenType == TokenType.Identifier && token.value == "static" && peek(1).type.let {
            it != TokenType.OpenParen && it != TokenType.Equals && it != TokenType.Semicolon
        }
        if (isStatic)
            consume()

        val method = parseMethodDefinition() ?: run {
            val name = parsePropertyNameNode() ?: run {
                expected("class element")
                consume()
                return null
            }
            val initializer = parseInitializer()
            return ClassFieldNode(name, initializer, isStatic)
        }

        return ClassMethodNode(method, isStatic)
    }

    private fun parseImportDeclaration(): ImportDeclarationNode? {
        if (tokenType != TokenType.Import)
            return null
        consume()

        if (tokenType == TokenType.StringLiteral) {
            return ImportDeclarationNode(
                listOf(Import(null, null, Import.Type.OnlyFile)),
                parseStringLiteral()
            ).also {
                automaticSemicolonInsertion()
            }
        }

        val clause = parseImports()
        if (clause != null) {
            if (tokenType != TokenType.Identifier || token.value != "from") {
                expected("'from'")
                consume()
                return null
            }
            consume()
            val fromClause = parseStringLiteral() ?: run {
                expected("string literal")
                consume()
                return null
            }
            automaticSemicolonInsertion()
            return ImportDeclarationNode(clause, fromClause)
        }

        val moduleSpecifier = parseStringLiteral() ?: run {
            expected("string literal")
            consume()
            return null
        }

        return ImportDeclarationNode(listOf(Import(null, null, Import.Type.OnlyFile)), moduleSpecifier)
    }

    private fun parseImports(): List<Import>? {
        val imports = mutableListOf<Import>()

        fun parseOtherImportTypes(): Boolean {
            val namespaceImport = parseNameSpaceImport()
            if (namespaceImport != null) {
                imports.add(namespaceImport)
            } else {
                val namedImports = parseNamedImports()
                if (namedImports == null) {
                    expected("namespace import or named imports")
                    consume()
                    return false
                }
                imports.addAll(namedImports)
            }
            return true
        }

        val defaultBinding = parseBindingIdentifier()

        if (defaultBinding != null) {
            imports.add(Import(null, defaultBinding.identifierName, Import.Type.Default))

            if (tokenType == TokenType.Comma) {
                consume()
                if (!parseOtherImportTypes())
                    return null
            }
        } else if (!parseOtherImportTypes()) return null

        return imports
    }

    private fun parseNameSpaceImport(): Import? {
        if (tokenType != TokenType.Asterisk)
            return null

        consume()
        if (tokenType != TokenType.Identifier && token.value != "as") {
            expected("'as'")
            consume()
            return null
        }
        consume()
        val binding = parseBindingIdentifier() ?: run {
            expected("identifier")
            consume()
            return null
        }

        return Import(null, binding.identifierName, Import.Type.Namespace)
    }

    private fun parseNamedImports(): List<Import>? {
        if (tokenType != TokenType.OpenCurly)
            return null
        consume()

        val imports = mutableListOf<Import>()

        while (tokenType != TokenType.CloseCurly) {
            val import = parseImportSpecifier()

            if (import != null)
                imports.add(import)

            if (import != null && tokenType == TokenType.Comma) {
                consume()
            } else break
        }

        consume(TokenType.CloseCurly)

        return imports
    }

    private fun parseImportSpecifier(): Import? {
        val binding =
            parseBindingIdentifier()?.identifierName ?:
            parseIdentifierName()?.identifierName ?:
            return null

        if (tokenType == TokenType.Identifier && token.value == "as") {
            consume()
            val targetName = parseBindingIdentifier() ?: run {
                expected("identifier")
                consume()
                return null
            }

            return Import(binding, targetName.identifierName, Import.Type.NormalAliased)
        } else {
            if (isReserved(binding)) {
                expected("non-keyword identifier")
                consume()
                return null
            }

            return Import(binding, null, Import.Type.Normal)
        }
    }

    private fun parseExportDeclaration(): ExportNode? {
        if (tokenType != TokenType.Export)
            return null
        consume()

        return when (tokenType) {
            TokenType.Default -> {
                consume()

                if (tokenType == TokenType.Class) {
                    return parseClassDeclaration()?.let(::DefaultClassExportNode) ?: run {
                        expected("class declaration")
                        consume()
                        return null
                    }
                }

                if (tokenType == TokenType.Function) {
                    return parseFunctionDeclaration()?.let(::DefaultFunctionExportNode) ?: run {
                        expected("function declaration")
                        consume()
                        return null
                    }
                }

                parseAssignmentExpression()?.let(::DefaultExpressionExportNode) ?: run {
                    expected("expression")
                    consume()
                    return null
                }
            }
            TokenType.Asterisk -> {
                consume()

                if (tokenType == TokenType.Identifier && token.value == "as") {
                    consume()
                    val name = parseIdentifierName() ?: run {
                        expected("identifier")
                        consume()
                        return null
                    }

                    FromExportNode(parseFromClause() ?: return null, name, FromExportNode.Type.NamedWildcard).also {
                        automaticSemicolonInsertion()
                    }
                } else FromExportNode(parseFromClause() ?: return null, null, FromExportNode.Type.Wildcard).also {
                    automaticSemicolonInsertion()
                }
            }
            TokenType.Var -> {
                val statement = parseVariableStatement() ?: run {
                    expected("variable declaration")
                    consume()
                    return null
                }
                DeclarationExportNode(statement)
            }
            TokenType.OpenCurly -> {
                val namedExports = parseNamedExports() ?: return null

                if (tokenType == TokenType.Identifier && token.value == "from") {
                    FromExportNode(parseFromClause() ?: return null, namedExports, FromExportNode.Type.NamedList)
                } else namedExports.also {
                    automaticSemicolonInsertion()
                }
            }
            else -> {
                return parseDeclarationNode()?.let { DeclarationExportNode(it) } ?: run {
                    expected("valid export statement")
                    consume()
                    null
                }
            }
        }
    }

    fun parseFromClause(): StringLiteralNode? {
        if (tokenType != TokenType.Identifier || token.value != "from") {
            expected("'from' clause")
            consume()
            return null
        }

        consume()
        return parseStringLiteral() ?: run {
            expected("string literal")
            consume()
            return null
        }
    }

    private fun parseNamedExports(): NamedExports? {
        consume(TokenType.OpenCurly)
        val exports = mutableListOf<NamedExport>()

        while (tokenType != TokenType.CloseCurly) {
            val localName = parseIdentifierName() ?: run {
                expected("identifier name")
                consume()
                return null
            }

            if (tokenType == TokenType.Identifier && token.value == "as") {
                consume()
                val targetName = parseIdentifierName() ?: run {
                    expected("identifier name")
                    consume()
                    return null
                }

                exports.add(NamedExport(localName.identifierName, targetName.identifierName))
            } else {
                exports.add(NamedExport(localName.identifierName, null))
            }

            if (tokenType != TokenType.Comma)
                break
            consume()
        }

        consume(TokenType.CloseCurly)

        return NamedExports(exports)
    }

    private fun parseLexicalDeclaration(
        forceSemicolon: Boolean = false,
        generateErrors: Boolean = true
    ): LexicalDeclarationNode? {
        if (!matchAny(TokenType.Let, TokenType.Const))
            return null

        saveState()

        val isConst = when (consume().type) {
            TokenType.Let -> false
            TokenType.Const -> true
            else -> unreachable()
        }

        val declarations = parseDeclarationList(isConst)
        if (declarations.isEmpty()) {
            expected("one or more lexical declarations")
            consume()
            return null
        }

        val names = declarations.map { it.identifier.identifierName }
        if (names.any { it == "let" }) {
            if (generateErrors) {
                syntaxError("cannot use \"let\" as a binding in a lexical declaration")
                discardState()
            } else loadState()
            return null
        }

        val duplicates = names.duplicates()
        if (duplicates.isNotEmpty()) {
            if (generateErrors) {
                syntaxError("cannot redeclare lexical declaration \"${duplicates.first()}\"")
                discardState()
            } else loadState()
            return null
        }

        if (isConst) {
            for (binding in declarations) {
                if (binding.initializer == null) {
                    if (generateErrors) {
                        syntaxError("const declaration must include an initializer")
                        discardState()
                    } else loadState()
                    return null
                }
            }
        }

        if (forceSemicolon) {
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

        return LexicalDeclarationNode(isConst, declarations)
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
        parseIdentifier()?.identifierName?.let { identifier ->
            if (isStrict && identifier.let { it == "arguments" || it == "eval" }) {
                syntaxError("cannot use \"$identifier\" as an identifier in strict-mode code")
                return null
            }

            return BindingIdentifierNode(identifier)
        }

        return when (tokenType) {
            TokenType.Await -> {
                if (goalSymbol == GoalSymbol.Module || inAwaitContext)
                    return null
                consume()
                BindingIdentifierNode("await")
            }
            TokenType.Yield -> {
                if (isStrict)
                    return null
                consume()
                BindingIdentifierNode("yield")
            }
            else -> null
        }
    }

    private fun parseLabelIdentifier(): String? {
        return parseIdentifierReference()?.let { it.identifierName }
    }

    private fun parseIdentifier(): IdentifierNode? {
        return when {
            tokenType != TokenType.Identifier -> null
            isReserved(token.value) -> null
            else -> {
                val name = consume().identifierValue()
                if (isReserved(name)) {
                    syntaxError("\"$name\" is not a valid identifier")
                    return null
                }
                if (isStrict && name in strictModeReserved) {
                    syntaxError("cannot use \"$name\" as an identifier in strict-mode code")
                    return null
                }
                IdentifierNode(name)
            }
        }
    }

    private fun parseIdentifierName(): IdentifierNode? {
        if (!token.isIdentifierName)
            return null
        return IdentifierNode(consume().identifierValue())
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
        val body = functionBoundary {
            parseConciseBody() ?: run {
                expected("arrow function body")
                return null
            }
        }

        return if (body is ExpressionNode) {
            ArrowFunctionNode.fromExpressionBody(parameters, body)
        } else {
            @Suppress("UNCHECKED_CAST")
            ArrowFunctionNode(parameters, body as StatementList)
        }
    }

    private fun parseArrowParameters(): ParameterList? {
        val singleParam = parseBindingIdentifier()
        if (singleParam != null)
            return ParameterList(listOf(Parameter(singleParam, null, isRest = false)))

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

        val parameters = elements.flatMap {
            if (it.node is CommaExpressionNode) {
                it.node.expressions.map { expr -> CPEAPPLPart(expr, false) }
            } else listOf(it)
        }.map {
            val (identifier, initializer) = when (it.node) {
                is AssignmentExpressionNode -> {
                    expect(it.node.lhs is IdentifierReferenceNode)
                    BindingIdentifierNode(it.node.lhs.identifierName) to it.node.rhs
                }
                is BindingIdentifierNode -> {
                    BindingIdentifierNode(it.node.identifierName) to null
                }
                else -> {
                    loadState()
                    return null
                }
            }

            Parameter(identifier, initializer, it.isSpread)
        }

        discardState()

        return ParameterList(parameters)
    }

    private fun parseConciseBody(): ASTNode? {
        return if (tokenType == TokenType.OpenCurly) {
            consume()
            val body = withReturn { parseFunctionBody() }
            consume(TokenType.CloseCurly)
            StatementList(body)
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
        return NewExpressionNode(expr, ArgumentList())
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
        if (tokenType != TokenType.Super)
            return null
        if (has(1) && peek(1).type != TokenType.OpenParen)
            return null
        consume()
        val args = parseArguments() ?: run {
            expected("arguments list")
            consume()
            return null
        }
        return SuperCallExpressionNode(args)
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
                    expected("expression")
                    return null
                }
                val args = parseArguments()
                // Spec deviation: MemberExpression does not have any alternatives
                // that involve arguments or new, as it makes the AST tree way more
                // confusing
                return NewExpressionNode(expr, args ?: ArgumentList())
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

    private fun parseMetaProperty(): ExpressionNode? {
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
                NewTargetExpressionNode
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
                ImportMetaExpressionNode
            }
            else -> null
        }
    }

    private fun parseSuperProperty(): ExpressionNode? {
        if (tokenType != TokenType.Super)
            return null
        saveState()
        consume()
        return when (tokenType) {
            TokenType.OpenBracket -> {
                discardState()
                consume()
                val expression = withIn { parseExpression() } ?: run {
                    expected("expression")
                    consume()
                    return null
                }
                consume(TokenType.CloseBracket)
                SuperPropertyExpressionNode(expression, true)
            }
            TokenType.Period -> {
                discardState()
                consume()
                val identifier = parseIdentifierName() ?: run {
                    expected("identifier")
                    consume()
                    return null
                }
                SuperPropertyExpressionNode(identifier, false)
            }
            else -> {
                loadState()
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

    private fun parseArguments(): ArgumentList? {
        if (tokenType != TokenType.OpenParen)
            return null
        consume()

        val argumentsList = parseArgumentsList()
        if (argumentsList != null && tokenType == TokenType.Comma) {
            consume()
        }
        consume(TokenType.CloseParen)
        return argumentsList?.let(::ArgumentList) ?: ArgumentList()
    }

    private fun parseArgumentsList(): ArgumentList? {
        val arguments = mutableListOf<ArgumentNode>()
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
                arguments.add(ArgumentNode(expr, true))
            } else {
                val expr = withIn { parseAssignmentExpression() } ?: run {
                    loadState()
                    return null
                }
                arguments.add(ArgumentNode(expr, false))
            }

            discardState()
            first = false
        } while (true)

        return ArgumentList(arguments)
    }

    private fun parsePrimaryExpression(): ExpressionNode? {
        if (tokenType == TokenType.This) {
            consume()
            return ThisLiteralNode
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

    private fun parseLiteral(): ExpressionNode? {
        return parseNullLiteral() ?:
            parseBooleanLiteral() ?:
            parseNumericLiteral() ?:
            parseBigIntLiteral() ?:
            parseStringLiteral()
    }

    private fun parseNullLiteral(): NullLiteralNode? {
        return if (tokenType == TokenType.NullLiteral) {
            consume()
            NullLiteralNode
        } else null
    }

    private fun parseBooleanLiteral(): BooleanLiteralNode ? {
        return when (tokenType) {
            TokenType.True -> TrueNode
            TokenType.False -> FalseNode
            else -> null
        }?.also {
            consume()
        }
    }

    private fun parseNumericLiteral(): NumericLiteralNode? {
        return if (tokenType == TokenType.NumericLiteral) {
            NumericLiteralNode(consume().doubleValue())
        } else null
    }

    private fun parseBigIntLiteral(): BigIntLiteralNode? {
        return if (tokenType == TokenType.BigIntLiteral) {
            val value = consume().value.toLowerCase()
            val type = when {
                value.startsWith("0x") -> BigIntLiteralNode.Type.Hex
                value.startsWith("0b") -> BigIntLiteralNode.Type.Binary
                value.startsWith("0o") -> BigIntLiteralNode.Type.Octal
                else -> BigIntLiteralNode.Type.Normal
            }
            BigIntLiteralNode(value.dropLast(1).let {
                if (type != BigIntLiteralNode.Type.Normal) it.drop(2) else it
            }, type)
        } else null
    }

    private fun parseStringLiteral(): StringLiteralNode? {
        return if (tokenType == TokenType.StringLiteral) {
            StringLiteralNode(consume().stringValue())
        } else null
    }

    private fun parseArrayLiteral(): ExpressionNode? {
        if (tokenType != TokenType.OpenBracket)
            return null

        consume()

        val elements = mutableListOf<ArrayElementNode>()

        fun getElement(): ArrayElementNode? {
            if (tokenType == TokenType.CloseBracket) {
                return null
            } else if (tokenType == TokenType.Comma) {
                return ArrayElementNode(null, ArrayElementNode.Type.Elision)
            }
            val type = if (tokenType == TokenType.TripleDot) {
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

        if (elements.size > 1 && elements.last().type == ArrayElementNode.Type.Elision)
            elements.removeLast()

        return ArrayLiteralNode(elements)
    }

    private fun parseObjectLiteral(isCoverForDestructure: Boolean = false): ObjectLiteralNode? {
        if (tokenType != TokenType.OpenCurly)
            return null

        consume()
        val list = parsePropertyDefinitionList()
        if (list != null && tokenType == TokenType.Comma)
            consume()

        consume(TokenType.CloseCurly)

        if (!isCoverForDestructure) {
            list?.properties?.forEach {
                if (it.type == PropertyDefinitionNode.Type.Initializer)
                    unexpected("object destructuring expression")
            }
        }

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
            if (method != null) {
                if (method.containsSuperCall()) {
                    syntaxError("\"super\" can only be used in derived class constructors")
                    return null
                }
                return PropertyDefinitionNode(method, null, PropertyDefinitionNode.Type.Method)
            }

            val propertyName = parsePropertyNameNode()
            if (propertyName != null) {
                if (propertyName.expr is IdentifierNode && tokenType == TokenType.Equals) {
                    consume()
                    val expr = withIn { parseAssignmentExpression() } ?: run {
                        expected("expression")
                        consume()
                        return null
                    }
                    return PropertyDefinitionNode(propertyName.expr, expr, PropertyDefinitionNode.Type.Initializer)
                }
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

        val params = withoutYield { withoutAwait { parseFunctionParameters() } }

        consume(TokenType.CloseParen)

        if (type == MethodDefinitionNode.Type.Getter && params.isNotEmpty()) {
            syntaxError("expected object literal getter to have zero parameters")
            return null
        }

        if (type == MethodDefinitionNode.Type.Setter && params.size != 1) {
            syntaxError("expected object literal setter to have one parameter")
            return null
        }

        consume(TokenType.OpenCurly)
        val body = functionBoundary(::parseFunctionBody)
        consume(TokenType.CloseCurly)

        return MethodDefinitionNode(name, params, StatementList(body), type)
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

    private fun parseFunctionExpression(): ExpressionNode? {
        if (tokenType != TokenType.Function)
            return null

        consume()
        val id = parseBindingIdentifier()
        consume(TokenType.OpenParen)
        val args = parseFunctionParameters() ?: return null
        consume(TokenType.CloseParen)
        consume(TokenType.OpenCurly)
        val body = functionBoundary(::parseFunctionBody)
        consume(TokenType.CloseCurly)

        return FunctionExpressionNode(id, args, StatementList(body))
    }

    private fun parseClassExpression(): ExpressionNode? {
        return ClassExpressionNode(parseClassNode(isDeclaration = false) ?: return null)
    }

    private fun parseGeneratorExpression(): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseAsyncFunctionExpression(): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseAsyncGeneratorExpression(): ExpressionNode? {
        // TODO
        return null
    }

    private fun parseRegularExpressionLiteral(): ExpressionNode? {
        if (tokenType == TokenType.UnterminatedRegexLiteral) {
            consume()
            unexpected("unterminated regex literal")
            return null
        }

        if (tokenType != TokenType.RegexLiteral)
            return null

        val source = consume().value.drop(1).dropLast(1)
        val flags = if (tokenType == TokenType.RegexFlags) consume().value else ""
        if (flags.toCharArray().distinct().size != flags.length) {
            syntaxError("RegExp literal contains duplicate flags")
            consume()
            return null
        }
        val invalidFlag = flags.firstOrNull { JSRegExpObject.Flag.values().none { flag -> flag.char == it } }
        if (invalidFlag != null) {
            syntaxError("RegExp literal contains invalid flag \"$invalidFlag\"")
            consume()
            return null
        }
        return RegExpLiteralNode(source, flags)
    }

    private fun parseTemplateLiteral(): ExpressionNode? {
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
                TokenType.TemplateLiteralString -> parts.add(StringLiteralNode(consume().stringValue()))
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
        val expr = parseArrowFunction() ?:
            parseConditionalExpression() ?:
            (if (inYieldContext) parseYieldExpression() else null) ?:
            parseAsyncArrowFunction() ?:
            return null
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

            val lhs = node ?: expr

            if (lhs !is ObjectLiteralNode && lhs !is ArrayLiteralNode && lhs.isInvalidAssignmentTarget) {
                syntaxError("invalid left-hand-side assignment")
                return null
            }

            node = AssignmentExpressionNode(lhs, rhs, op)
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
        } else {
            if (isStrict && op == UnaryExpressionNode.Operator.Delete) {
                var theExprToCheck = expr
                while (theExprToCheck is ParenthesizedExpressionNode)
                    theExprToCheck = theExprToCheck.expression

                if (theExprToCheck is IdentifierReferenceNode) {
                    syntaxError("using the \"delete\" operator with an identifier is not allow in strict-mode code")
                    return null
                }
            }

            UnaryExpressionNode(expr, op)
        }
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

        if (expr.isInvalidAssignmentTarget) {
            syntaxError("invalid update operator target")
            return null
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

    private inline fun <T> functionBoundary(block: () -> T): T {
        val restoreBreakContext = ::inBreakContext.temporaryChange(false)
        val restoreContinueContext = ::inContinueContext.temporaryChange(false)
        val restoreLabels = ::activeLabels.temporaryChange(mutableListOf())
        val previousIsStrict = isStrict

        val result =  block()

        restoreBreakContext()
        restoreContinueContext()
        restoreLabels()
        isStrict = previousIsStrict

        return result
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
        if (Lexer.lineTerminators.any { it in token.trivia })
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

    private inline fun <T> withYield(block: () -> T): T {
        val prev = inYieldContext
        inYieldContext = true
        return block().also { inYieldContext = prev }
    }

    private inline fun <T> withAwait(block: () -> T): T {
        val prev = inAwaitContext
        inAwaitContext = true
        return block().also { inAwaitContext = prev }
    }

    private inline fun <T> withReturn(block: () -> T): T {
        val prev = inReturnContext
        inReturnContext = true
        return block().also { inReturnContext = prev }
    }

    private inline fun <T> withIn(block: () -> T): T {
        val prev = inInContext
        inInContext = true
        return block().also { inInContext = prev }
    }

    private inline fun <T> withDefault(block: () -> T): T {
        val prev = inDefaultContext
        inDefaultContext = true
        return block().also { inDefaultContext = prev }
    }

    private inline fun <T> withTagged(block: () -> T): T {
        val prev = inTaggedContext
        inTaggedContext = true
        return block().also { inTaggedContext = prev }
    }

    private inline fun <T> withoutYield(block: () -> T): T {
        val prev = inYieldContext
        inYieldContext = false
        return block().also { inYieldContext = prev }
    }

    private inline fun <T> withoutAwait(block: () -> T): T {
        val prev = inAwaitContext
        inAwaitContext = false
        return block().also { inAwaitContext = prev }
    }

    private inline fun <T> withoutReturn(block: () -> T): T {
        val prev = inReturnContext
        inReturnContext = false
        return block().also { inReturnContext = prev }
    }

    private inline fun <T> withoutIn(block: () -> T): T {
        val prev = inInContext
        inInContext = false
        return block().also { inInContext = prev }
    }

    private inline fun <T> withoutDefault(block: () -> T): T {
        val prev = inDefaultContext
        inDefaultContext = false
        return block().also { inDefaultContext = prev }
    }

    private inline fun <T> withoutTagged(block: () -> T): T {
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

        private val strictModeReserved = listOf(
            "implements",
            "interface",
            "let",
            "package",
            "private",
            "protected",
            "public",
            "static",
            "yield"
        )

        fun isReserved(identifier: String) = identifier in reservedWords
    }
}
