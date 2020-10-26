package me.mattco.reeva.interpreter

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.compiler.Completion
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.Agent
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.contexts.ExecutionContext
import me.mattco.reeva.runtime.environment.DeclarativeEnvRecord
import me.mattco.reeva.runtime.environment.EnvRecord
import me.mattco.reeva.runtime.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.arrays.JSArrayObject
import me.mattco.reeva.runtime.values.errors.JSErrorObject
import me.mattco.reeva.runtime.values.errors.JSReferenceErrorObject
import me.mattco.reeva.runtime.values.errors.JSSyntaxErrorObject
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.functions.JSInterpretedFunction
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.objects.PropertyKey
import me.mattco.reeva.runtime.values.primitives.*
import me.mattco.reeva.utils.*

class Interpreter(private val record: Realm.ScriptRecord) {
    fun interpret(scriptContext: ExecutionContext): Completion {
        val globalEnv = record.realm.globalEnv!!
        val result = globalDeclarationInstantiation(record.scriptOrModule, globalEnv).let {
            if (it.isNormal)
                interpretScript(record.scriptOrModule)
            else it
        }.let {
            if (it.isNormal && it.value == JSEmpty)
                normalCompletion(JSUndefined)
            else it
        }

        if (!Agent.hasError())
            expect(result.isNormal)

        return result
    }

    private fun globalDeclarationInstantiation(body: ScriptNode, env: GlobalEnvRecord): Completion {
        val lexNames = body.lexicallyDeclaredNames()
        val varNames = body.varDeclaredNames()
        lexNames.forEach {
            if (env.hasVarDeclaration(it))
                return syntaxError("TODO: message")
            if (env.hasLexicalDeclaration(it))
                return syntaxError("TODO: message")
        }
        varNames.forEach {
            if (env.hasLexicalDeclaration(it))
                return syntaxError("TODO: message")
        }
        val varDeclarations = body.varScopedDeclarations()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()
        val declaredFunctionNames = mutableListOf<String>()
        varDeclarations.asReversed().forEach {
            if (it !is VariableDeclarationNode && it !is ForBindingNode && it !is BindingIdentifierNode) {
                val functionName = it.boundNames()[0]
                if (functionName !in declaredFunctionNames) {
                    if (!env.canDeclareGlobalFunction(functionName))
                        return typeError("TODO: message")
                    declaredFunctionNames.add(functionName)
                    functionsToInitialize.add(0, it as FunctionDeclarationNode)
                }
            }
        }
        val declaredVarNames = mutableListOf<String>()
        varDeclarations.forEach {
            if (it !is VariableDeclarationNode && it !is ForBindingNode && it !is BindingIdentifierNode)
                return@forEach
            it.boundNames().forEach { name ->
                if (name !in declaredFunctionNames) {
                    if (!env.canDeclareGlobalVar(name))
                        return typeError("TODO: message")
                    if (name !in declaredVarNames)
                        declaredVarNames.add(name)
                }
            }
        }
        val lexDeclarations = body.lexicallyScopedDeclarations()
        lexDeclarations.forEach { decl ->
            decl.boundNames().forEach { name ->
                if (decl.isConstantDeclaration()) {
                    env.createImmutableBinding(name, true)
                } else {
                    env.createMutableBinding(name, false)
                }
            }
        }
        functionsToInitialize.forEach { func ->
            val functionName = func.boundNames()[0]
            val function = instantiateFunctionObject(func, env)
            env.createGlobalFunctionBinding(functionName, function, false)
        }
        declaredVarNames.forEach {
            env.createGlobalVarBinding(it, false)
        }
        return normalCompletion(JSEmpty)
    }

    @ECMAImpl("InstantiateFunctionObject", "14.1.22")
    private fun instantiateFunctionObject(functionNode: FunctionDeclarationNode, scope: EnvRecord): JSFunction {
        val sourceText = "TODO"
        val function = ordinaryFunctionCreate(
            record.realm.functionProto,
            sourceText,
            functionNode.parameters,
            functionNode.body,
            JSFunction.ThisMode.NonLexical,
            false,
            scope
        )
        if (functionNode.identifier != null)
            setFunctionName(function, functionNode.identifier.identifierName.key())
        return function
    }

    @ECMAImpl("OrdinaryFunctionCreate", "9.2.3")
    private fun ordinaryFunctionCreate(
        prototype: JSObject,
        sourceText: String,
        parameterList: FormalParametersNode,
        body: FunctionStatementList,
        thisMode: JSFunction.ThisMode,
        isStrict: Boolean,
        scope: EnvRecord
    ): JSFunction {
        val function = JSInterpretedFunction(
            Agent.runningContext.realm,
            when {
                thisMode == JSFunction.ThisMode.Lexical -> JSFunction.ThisMode.Lexical
                isStrict -> JSFunction.ThisMode.Strict
                else -> JSFunction.ThisMode.Global
            },
            scope,
            isStrict,
            false,
            JSUndefined,
            prototype,
            sourceText,
        ) { function, arguments ->
            functionDeclarationInstantiation(function, parameterList, arguments, body).ifAbrupt { return@JSInterpretedFunction it }
            if (body.statementList != null) {
                interpretStatementList(body.statementList)
            } else normalCompletion(JSUndefined)
        }

        Operations.definePropertyOrThrow(
            function,
            "length".toValue(),
            Descriptor(parameterList.functionParameters.parameters.size.toValue(), Descriptor.CONFIGURABLE)
        )

        return function
    }

    private fun functionDeclarationInstantiation(
        function: JSInterpretedFunction,
        formals: FormalParametersNode,
        arguments: JSArguments,
        body: FunctionStatementList,
    ): Completion {
        val calleeContext = Agent.runningContext
        val strict = function.isStrict
        val parameterNames = formals.boundNames()
        val hasDuplicates = parameterNames.groupBy { it }.size != parameterNames.size
        val simpleParameterList = formals.isSimpleParameterList()
        val hasParameterExpressions = formals.containsExpression()
        val varNames = body.varDeclaredNames()
        val varDeclarations = body.varScopedDeclarations()
        val lexicalNames = body.lexicallyDeclaredNames()
        val functionNames = mutableListOf<String>()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()

        varDeclarations.asReversed().forEach { decl ->
            if (decl is VariableDeclarationNode || decl is ForBindingNode || decl is BindingIdentifierNode)
                return@forEach
            expect(decl is FunctionDeclarationNode)
            val functionName = decl.boundNames()[0]
            if (functionName in functionNames)
                return@forEach
            functionNames.add(0, functionName)
            functionsToInitialize.add(0, decl)
        }

        val argumentsObjectNeeded = when {
            function.thisMode == JSFunction.ThisMode.Lexical -> false
            "arguments" in parameterNames -> false
            !hasParameterExpressions && ("arguments" in functionNames || "arguments" in lexicalNames) -> false
            else -> true
        }

        val env = if (strict || !hasParameterExpressions) {
            calleeContext.lexicalEnv!!
        } else {
            val calleeEnv = calleeContext.lexicalEnv
            val env = DeclarativeEnvRecord.create(calleeEnv)
            calleeContext.lexicalEnv = env
            env
        }

        parameterNames.forEach { name ->
            if (!env.hasBinding(name)) {
                env.createMutableBinding(name, false)
                if (hasDuplicates)
                    env.initializeBinding(name, JSUndefined)
            }
        }

        if (argumentsObjectNeeded) {
            // TODO
        }

        val parameterBindings = parameterNames

        if (formals.restParameter != null)
            TODO()

        formals.functionParameters.parameters.forEachIndexed { index, parameter ->
            val lhs = Operations.resolveBinding(parameter.bindingElement.binding.identifier.identifierName)
            ifError { return it }
            var value = if (index > arguments.lastIndex) {
                JSUndefined
            } else arguments[index]

            if (value == JSUndefined && parameter.bindingElement.binding.initializer != null) {
                val result = interpretExpression(parameter.bindingElement.binding.initializer.node).ifAbrupt { return it }
                val defaultValue = Operations.getValue(result.value)
                ifError { return it }
                value = defaultValue
            }

            if (hasDuplicates) {
                Operations.putValue(lhs, value)
            } else {
                Operations.initializeReferencedBinding(lhs, value)
            }
        }

        val varEnv = if (!hasParameterExpressions) {
            val instantiatedVarNames = parameterBindings.toMutableList()
            varNames.forEach { name ->
                if (name !in instantiatedVarNames) {
                    instantiatedVarNames.add(name)
                    env.createMutableBinding(name, false)
                    env.initializeBinding(name, JSUndefined)
                }
            }
            env
        } else {
            val varEnv = DeclarativeEnvRecord.create(env)
            calleeContext.variableEnv = varEnv
            val instantiatedVarNames = mutableListOf<String>()
            varNames.forEach { name ->
                if (name in instantiatedVarNames)
                    return@forEach
                instantiatedVarNames.add(name)
                varEnv.createMutableBinding(name, false)
                val initialValue = if (name !in parameterBindings || name in functionNames) {
                    JSUndefined
                } else env.getBindingValue(name, false)
                ifError { return it }
                varEnv.initializeBinding(name, initialValue)
            }
            varEnv
        }

        val lexEnv = if (!strict) {
            DeclarativeEnvRecord.create(varEnv)
        } else varEnv

        calleeContext.lexicalEnv = lexEnv

        body.lexicallyScopedDeclarations().forEach { decl ->
            decl.boundNames().forEach { name ->
                if (decl.isConstantDeclaration()) {
                    lexEnv.createImmutableBinding(name, true)
                } else {
                    lexEnv.createMutableBinding(name, false)
                }
            }
        }

        functionsToInitialize.forEach { decl ->
            val functionName = decl.boundNames()[0]
            val function = instantiateFunctionObject(decl, lexEnv)
            varEnv.setMutableBinding(functionName, function, false)
        }

        return normalCompletion()
    }

    @ECMAImpl("SetFunctionName", "9.2.8")
    private fun setFunctionName(function: JSFunction, name: PropertyKey, prefix: String? = null): Boolean {
        ecmaAssert(function.isExtensible())
        val nameString = when {
            name.isSymbol -> "[${name.asSymbol.description}]"
            name.isInt -> name.asInt.toString()
            name.isDouble -> name.asDouble.toString()
            else -> name.asString
        }.let {
            if (prefix != null) {
                "$prefix $it"
            } else it
        }
        return Operations.definePropertyOrThrow(function, "name".toValue(), Descriptor(nameString.toValue(), Descriptor.CONFIGURABLE))
    }

    private fun interpretScript(scriptNode: ScriptNode): Completion {
        return interpretStatementList(scriptNode.statementList)
    }

    private fun interpretStatementList(statementListNode: StatementListNode): Completion {
        statementListNode.statements.forEach { statement ->
            interpretStatement(statement as StatementNode).ifAbrupt { return it }
        }
        return normalCompletion(JSEmpty)
    }

    private fun interpretStatement(statement: StatementNode): Completion {
        return when (statement) {
            is BlockStatementNode -> interpretBlockStatement(statement)
            is VariableStatementNode -> interpretVariableStatement(statement)
            is EmptyStatementNode -> normalCompletion()
            is ExpressionStatementNode -> interpretExpressionStatement(statement)
            is IfStatementNode -> interpretIfStatement(statement)
            is BreakableStatement -> interpretBreakableStatement(statement)
            is IterationStatement -> interpretIterationStatement(statement, emptySet())
            is LabelledStatementNode -> labelledEvaluation(statement, emptySet())
            is LexicalDeclarationNode -> interpretLexicalDeclaration(statement)
            is FunctionDeclarationNode -> interpretFunctionDeclaration(statement)
            is ReturnStatementNode -> interpretReturnStatement(statement)
            is ThrowStatementNode -> interpretThrowStatement(statement)
            is TryStatementNode -> interpretTryStatement(statement)
            is BreakStatementNode -> interpretBreakStatement(statement)
            else -> TODO()
        }
    }

    private fun interpretBlockStatement(blockStatementNode: BlockStatementNode): Completion {
        return interpretBlock(blockStatementNode.block)
    }

    private fun interpretBlock(block: BlockNode): Completion {
        if (block.statements == null)
            return normalCompletion(JSEmpty)
        val oldEnv = Agent.runningContext.lexicalEnv
        val blockEnv = DeclarativeEnvRecord.create(Agent.runningContext.lexicalEnv)
        blockDeclarationInstantiation(block.statements, blockEnv)
        Agent.runningContext.lexicalEnv = blockEnv
        return interpretStatementList(block.statements)
    }

    private fun blockDeclarationInstantiation(statements: StatementListNode, env: DeclarativeEnvRecord) {
        statements.lexicallyScopedDeclarations().forEach { decl ->
            decl.boundNames().forEach { name ->
                if (decl.isConstantDeclaration()) {
                    env.createImmutableBinding(name, true)
                } else {
                    env.createMutableBinding(name, false)
                }
            }
            if (decl is FunctionDeclarationNode) {
                val name = decl.boundNames()[0]
                val function = instantiateFunctionObject(decl, env)
                env.initializeBinding(name, function)
            }
        }
    }

    private fun interpretVariableStatement(variableStatementNode: VariableStatementNode): Completion {
        variableStatementNode.declarations.declarations.forEach { decl ->
            if (decl.initializer == null)
                return@forEach
            val lhs = Operations.resolveBinding(decl.identifier.identifierName)
            val rhsResult = interpretExpression(decl.initializer.node).ifAbrupt { return it }
            val rhs = Operations.getValue(rhsResult.value)
            Operations.putValue(lhs, rhs)
        }
        return normalCompletion()
    }

    private fun interpretExpressionStatement(expressionStatement: ExpressionStatementNode): Completion {
        interpretExpression(expressionStatement.node).ifAbrupt { return it }
        return normalCompletion()
    }

    private fun interpretIfStatement(ifStatement: IfStatementNode): Completion {
        val exprRef = interpretExpression(ifStatement.condition).ifAbrupt { return it }
        val exprValue = Operations.toBoolean(Operations.getValue(exprRef.value))
        ifError { return it }
        if (exprValue.value) {
            return updateEmpty(interpretStatement(ifStatement.trueBlock), JSUndefined)
        } else if (ifStatement.falseBlock != null) {
            return updateEmpty(interpretStatement(ifStatement.falseBlock), JSUndefined)
        }
        return normalCompletion(JSUndefined)
    }

    private fun interpretBreakableStatement(breakableStatementNode: BreakableStatement): Completion {
        return labelledEvaluation(breakableStatementNode, emptySet())
    }

    private fun interpretIterationStatement(iterationStatement: IterationStatement, labelSet: Set<String>): Completion {
        return when (iterationStatement) {
            is DoWhileStatementNode -> interpretDoWhileStatement(iterationStatement, labelSet)
            is WhileStatementNode -> interpretWhileStatement(iterationStatement, labelSet)
            is ForStatementNode -> interpretForStatement(iterationStatement, labelSet)
            is ForInNode -> TODO()
            is ForOfNode -> TODO()
            else -> TODO()
        }
    }

    private fun interpretDoWhileStatement(doWhileStatementNode: DoWhileStatementNode, labelSet: Set<String>): Completion {
        var value: JSValue = JSUndefined
        while (true) {
            val result = interpretStatement(doWhileStatementNode.body)
            if (!loopContinues(result, labelSet))
                return updateEmpty(result, value)
            if (result.value != JSEmpty)
                value = result.value
            val exprRef = interpretExpression(doWhileStatementNode.condition).ifAbrupt { return it }
            val exprValue = Operations.getValue(exprRef.value)
            ifError { return it }
            if (Operations.toBoolean(exprValue) == JSFalse)
                return normalCompletion(value)
        }
    }

    private fun interpretWhileStatement(whileStatementNode: WhileStatementNode, labelSet: Set<String>): Completion {
        var value: JSValue = JSUndefined
        while (true) {
            val exprRef = interpretExpression(whileStatementNode.condition).ifAbrupt { return it }
            val exprValue = Operations.getValue(exprRef.value)
            ifError { return it }
            if (Operations.toBoolean(exprValue) == JSFalse)
                return normalCompletion(value)
            val result = interpretStatement(whileStatementNode.body)
            if (!loopContinues(result, labelSet))
                return updateEmpty(result, value)
            if (result.value != JSEmpty)
                value = result.value
        }
    }

    private fun interpretForStatement(forStatementNode: ForStatementNode, labelSet: Set<String>): Completion {
        when (forStatementNode.initializer) {
            is ExpressionNode -> {
                val exprRef = interpretExpression(forStatementNode.initializer).ifAbrupt { return it }
                Operations.getValue(exprRef.value)

                return forBodyEvaluation(
                    forStatementNode.condition,
                    forStatementNode.incrementer,
                    forStatementNode.body,
                    emptyList(),
                    labelSet
                )
            }
            is VariableStatementNode -> {
                interpretVariableStatement(forStatementNode.initializer).ifAbrupt { return it }

                return forBodyEvaluation(
                    forStatementNode.condition,
                    forStatementNode.incrementer,
                    forStatementNode.body,
                    emptyList(),
                    labelSet
                )
            }
            is LexicalDeclarationNode -> {
                val oldEnv = Agent.runningContext.lexicalEnv
                val loopEnv = DeclarativeEnvRecord.create(oldEnv)
                val isConst = forStatementNode.initializer.isConstantDeclaration()
                val boundNames = forStatementNode.initializer.boundNames()
                boundNames.forEach { name ->
                    if (isConst) {
                        loopEnv.createImmutableBinding(name, true)
                    } else {
                        loopEnv.createMutableBinding(name, false)
                    }
                }
                Agent.runningContext.lexicalEnv = loopEnv
                val forDcl = interpretLexicalDeclaration(forStatementNode.initializer)
                if (forDcl.isAbrupt) {
                    Agent.runningContext.lexicalEnv = oldEnv
                    return forDcl
                }
                val perIterationLets = if (isConst) emptyList() else boundNames
                val bodyResult = forBodyEvaluation(
                    forStatementNode.condition,
                    forStatementNode.incrementer,
                    forStatementNode.body,
                    perIterationLets,
                    labelSet,
                )
                Agent.runningContext.lexicalEnv = oldEnv
                return bodyResult
            }
            else -> TODO()
        }
    }

    private fun forBodyEvaluation(
        condition: ExpressionNode?,
        incrementer: ExpressionNode?,
        body: StatementNode,
        perIterationBindings: List<String>,
        labelSet: Set<String>
    ): Completion {
        var value: JSValue = JSUndefined
        createPerIterationEnvironment(perIterationBindings).ifAbrupt { return it }
        while (true) {
            if (condition != null) {
                val testRef = interpretExpression(condition).ifAbrupt { return it }
                val testValue = Operations.getValue(testRef.value)
                ifError { return it }
                if (Operations.toBoolean(testValue) == JSFalse)
                    return normalCompletion(value)
            }
            val result = interpretStatement(body).ifAbrupt { return it }
            if (!loopContinues(result, labelSet))
                return normalCompletion(value)
            if (result.value != JSEmpty)
                value = result.value
            createPerIterationEnvironment(perIterationBindings).ifAbrupt { return it }
            if (incrementer != null) {
                val incRef = interpretExpression(incrementer).ifAbrupt { return it }
                Operations.getValue(incRef.value)
                ifError { return it }
            }
        }
    }

    private fun createPerIterationEnvironment(perIterationBindings: List<String>): Completion {
        if (perIterationBindings.isEmpty())
            return normalCompletion()

        val lastIterationEnv = Agent.runningContext.lexicalEnv
        expect(lastIterationEnv != null)
        val outer = lastIterationEnv.outerEnv
        ecmaAssert(outer != null)
        val thisIterationEnv = DeclarativeEnvRecord.create(outer)
        perIterationBindings.forEach { binding ->
            thisIterationEnv.createMutableBinding(binding, false)
            val lastValue = lastIterationEnv.getBindingValue(binding, true)
            ifError { return it }
            thisIterationEnv.initializeBinding(binding, lastValue)
        }
        Agent.runningContext.lexicalEnv = thisIterationEnv

        return normalCompletion(JSUndefined)
    }

    @ECMAImpl("LoopContinues", "13.7.1.2")
    private fun loopContinues(completion: Completion, labelSet: Set<String>): Boolean {
        return when {
            completion.isNormal -> true
            !completion.isContinue -> false
            completion.target == null -> true
            completion.target in labelSet -> true
            else -> false
        }
    }

    private fun interpretBreakStatement(breakStatementNode: BreakStatementNode): Completion {
        return if (breakStatementNode.label == null) {
            breakCompletion()
        } else {
            breakCompletion(breakStatementNode.label.identifierName)
        }
    }

    private fun labelledEvaluation(node: StatementNode, labelSet: Set<String>): Completion {
        return when (node) {
            is LabelledStatementNode -> {
                val result = labelledEvaluation(node.item, labelSet + node.label.identifierName)
                if (result.isBreak && node.label.identifierName == result.target) {
                    normalCompletion(result.value)
                } else result
            }
            is FunctionDeclarationNode -> interpretFunctionDeclaration(node)
            is BreakableStatement -> {
                expect(node is IterationStatement)
                val result = interpretIterationStatement(node, labelSet)
                if (result.isBreak && result.target == null) {
                    if (result.value == JSEmpty) {
                        normalCompletion(JSUndefined)
                    } else normalCompletion(result.value)
                } else result
            }
            else -> TODO()
        }
    }

    private fun interpretLexicalDeclaration(lexicalDeclarationNode: LexicalDeclarationNode): Completion {
        lexicalDeclarationNode.bindingList.lexicalBindings.forEach {
            if (it.initializer == null) {
                expect(!lexicalDeclarationNode.isConst)
                val lhs = Operations.resolveBinding(it.identifier.identifierName)
                Operations.initializeReferencedBinding(lhs, JSUndefined)
                ifError { return it }
            } else {
                val lhs = Operations.resolveBinding(it.identifier.identifierName)
                val rhs = interpretExpression(it.initializer.node).ifAbrupt { return it }
                val value = Operations.getValue(rhs.value)
                ifError { return it }
                Operations.initializeReferencedBinding(lhs, value)
                ifError { return it }
            }
        }
        return normalCompletion()
    }

    private fun interpretFunctionDeclaration(functionDeclarationNode: FunctionDeclarationNode): Completion {
        return normalCompletion()
    }

    private fun interpretReturnStatement(returnStatementNode: ReturnStatementNode): Completion {
        if (returnStatementNode.node == null) {
            return returnCompletion()
        } else {
            val exprRef = interpretExpression(returnStatementNode.node).ifAbrupt { return it }
            val exprValue = Operations.getValue(exprRef.value)
            ifError { return it }
            return returnCompletion(exprValue)
        }
    }

    private fun interpretThrowStatement(throwStatementNode: ThrowStatementNode): Completion {
        val exprRef = interpretExpression(throwStatementNode.expr).ifAbrupt { return it }
        val exprValue = Operations.getValue(exprRef.value)
        ifError { return it }
        return throwCompletion(exprValue)
    }

    private fun interpretTryStatement(tryStatementNode: TryStatementNode): Completion {
        val blockResult = interpretBlock(tryStatementNode.tryBlock)
        val catchResult = if (blockResult.isThrow) {
            if (tryStatementNode.catchNode.catchParameter == null) {
                interpretBlock(tryStatementNode.catchNode.block)
            } else {
                val oldEnv = Agent.runningContext.lexicalEnv
                val catchEnv = DeclarativeEnvRecord.create(oldEnv)
                val parameter = tryStatementNode.catchNode.catchParameter
                parameter.boundNames().forEach { name ->
                    catchEnv.createMutableBinding(name, false)
                }
                Agent.runningContext.lexicalEnv = catchEnv
                val status = bindingInitialization(parameter.identifierName, blockResult.value, catchEnv)
                if (status.isAbrupt) {
                    Agent.runningContext.lexicalEnv = oldEnv
                    status
                } else {
                    val catchBlockResult = interpretBlock(tryStatementNode.catchNode.block).ifAbrupt { return it }
                    Agent.runningContext.lexicalEnv = oldEnv
                    catchBlockResult
                }
            }
        } else blockResult
        return updateEmpty(catchResult, JSUndefined)
    }

    private fun bindingInitialization(identifier: String, value: JSValue, env: EnvRecord?): Completion {
        return if (env == null) {
            val lhs = Operations.resolveBinding(identifier)
            ifError { return it }
            Operations.putValue(lhs, value)
            ifError { return it }
            normalCompletion()
        } else {
            env.initializeBinding(identifier, value)
            normalCompletion(JSUndefined)
        }
    }

    private fun interpretExpression(expression: ExpressionNode): Completion {
        return when (expression) {
            ThisNode -> interpretThis()
            is CommaExpressionNode -> interpretCommaExpressionNode(expression)
            is IdentifierReferenceNode -> interpretIdentifierReference(expression)
            is LiteralNode -> interpretLiteral(expression)
            is CPEAAPLNode -> interpretCPEAAPL(expression)
            is NewExpressionNode -> interpretNewExpression(expression)
            is CallExpressionNode -> interpretCallExpression(expression)
            is ObjectLiteralNode -> interpretObjectLiteral(expression)
            is ArrayLiteralNode -> interpretArrayLiteral(expression)
            is MemberExpressionNode -> interpretMemberExpression(expression)
            is OptionalExpressionNode -> interpretOptionalExpression(expression)
            is AssignmentExpressionNode -> interpretAssignmentExpression(expression)
            is ConditionalExpressionNode -> interpretConditionalExpression(expression)
            is CoalesceExpressionNode -> interpretCoalesceExpression(expression)
            is LogicalORExpressionNode -> interpretLogicalORExpression(expression)
            is LogicalANDExpressionNode -> interpretLogicalANDExpression(expression)
            is BitwiseORExpressionNode -> interpretBitwiseORExpression(expression)
            is BitwiseXORExpressionNode -> interpretBitwiseXORExpression(expression)
            is BitwiseANDExpressionNode -> interpretBitwiseANDExpression(expression)
            is EqualityExpressionNode -> interpretEqualityExpression(expression)
            is RelationalExpressionNode -> interpretRelationalExpression(expression)
            is ShiftExpressionNode -> interpretShiftExpression(expression)
            is AdditiveExpressionNode -> interpretAdditiveExpression(expression)
            is MultiplicativeExpressionNode -> interpretMultiplicationExpression(expression)
            is ExponentiationExpressionNode -> interpretExponentiationExpression(expression)
            is UnaryExpressionNode -> interpretUnaryExpression(expression)
            is UpdateExpressionNode -> interpretUpdateExpression(expression)
            else -> unreachable()
        }
    }

    private fun interpretThis(): Completion {
        val result = Operations.resolveThisBinding()
        ifError { return it }
        return normalCompletion(result)
    }

    private fun interpretCommaExpressionNode(commaExpression: CommaExpressionNode): Completion {
        var result: Completion? = null
        commaExpression.expressions.forEach {
            val exprResult = interpretExpression(it).ifAbrupt { return it }
            val value = Operations.getValue(exprResult.value)
            ifError { return it }
            result = normalCompletion(value)
        }
        return result!!
    }

    private fun interpretIdentifierReference(identifierReference: IdentifierReferenceNode): Completion {
        val value = Operations.resolveBinding(identifierReference.identifierName)
        ifError { return it }
        return normalCompletion(value)
    }

    private fun interpretLiteral(literalNode: LiteralNode): Completion {
        val value = when (literalNode) {
            is NullNode -> JSNull
            is BooleanNode -> literalNode.value.toValue()
            is NumericLiteralNode -> literalNode.value.toValue()
            is StringLiteralNode -> literalNode.value.toValue()
            else -> unreachable()
        }
        ifError { return it }
        return normalCompletion(value)
    }

    private fun interpretCPEAAPL(cpeaaplNode: CPEAAPLNode): Completion {
        return when (cpeaaplNode.context) {
            Parser.CPEAAPLContext.PrimaryExpression -> {
                interpretExpression(cpeaaplNode.node)
            }
        }
    }

    private fun interpretNewExpression(newExpressionNode: NewExpressionNode): Completion {
        val ref = interpretExpression(newExpressionNode.target).ifAbrupt { return it }
        val constructor = Operations.getValue(ref.value)
        ifError { return it }
        val arguments = if (newExpressionNode.arguments == null) {
            emptyList()
        } else {
            argumentsListEvaluation(newExpressionNode.arguments) ?: return throwCompletion()
        }
        if (!Operations.isConstructor(constructor)) {
            throwError<JSTypeErrorObject>("${Operations.toPrintableString(constructor)} is not a constructor")
            return throwCompletion()
        }
        val result = Operations.construct(constructor, arguments)
        ifError { return it }
        return normalCompletion(result)
    }

    // Null indicates an error
    private fun argumentsListEvaluation(argumentsNode: ArgumentsNode): List<JSValue>? {
        val argumentEntries = argumentsNode.arguments
        if (argumentEntries.isEmpty())
            return emptyList()
        val arguments = mutableListOf<JSValue>()
        argumentEntries.forEach { entry ->
            if (entry.isSpread)
                TODO()
            val ref = interpretExpression(entry.expression).ifAbrupt { return null }
            val value = Operations.getValue(ref.value)
            ifError { return null }
            arguments.add(value)
        }
        return arguments
    }

    private fun interpretCallExpression(callExpressionNode: CallExpressionNode): Completion {
        val ref = interpretExpression(callExpressionNode.target).ifAbrupt { return it }
        val func = Operations.getValue(ref.value)
        ifError { return it }
        val args = argumentsListEvaluation(callExpressionNode.arguments) ?: return throwCompletion()
        val result = Operations.evaluateCall(func, ref.value, args, false)
        ifError { return returnCompletion() }
        return normalCompletion(result)
    }

    private fun interpretObjectLiteral(objectLiteralNode: ObjectLiteralNode): Completion {
        val obj = JSObject.create(Agent.runningContext.realm)
        if (objectLiteralNode.list == null)
            return normalCompletion(obj)
        objectLiteralNode.list.properties.forEach { property ->
            when (property.type) {
                PropertyDefinitionNode.Type.KeyValue -> {
                    val propKey = evaluatePropertyName(property.first).ifAbrupt { return it }
                    val exprValueRef = interpretExpression(property.second!!).ifAbrupt { return it }
                    val propValue = Operations.getValue(exprValueRef.value)
                    ifError { return it }
                    Operations.createDataPropertyOrThrow(obj, propKey.value, propValue)
                    ifError { return it }
                }
                PropertyDefinitionNode.Type.Shorthand -> {
                    expect(property.first is IdentifierReferenceNode)
                    val propName = property.first.identifierName
                    val exprValue = interpretIdentifierReference(property.first).ifAbrupt { return it }
                    val propValue = Operations.getValue(exprValue.value)
                    ifError { return it }
                    Operations.createDataPropertyOrThrow(obj, propName.toValue(), propValue)
                    ifError { return it }
                }
                PropertyDefinitionNode.Type.Method -> TODO()
                PropertyDefinitionNode.Type.Spread -> TODO()
            }
        }
        return normalCompletion(obj)
    }

    private fun evaluatePropertyName(propertyName: ASTNode): Completion {
        return if (propertyName is PropertyNameNode) {
            if (propertyName.isComputed) {
                val exprValue = interpretExpression(propertyName.expr).ifAbrupt { return it }
                val propName = Operations.getValue(exprValue.value)
                ifError { return it }
                val key = Operations.toPropertyKey(propName).asValue
                normalCompletion(key)
            } else {
                when (val expr = propertyName.expr) {
                    is IdentifierNode -> expr.identifierName
                    is StringLiteralNode -> expr.value
                    is NumericLiteralNode -> Operations.toString(expr.value.toValue()).string
                    else -> unreachable()
                }.let {
                    normalCompletion(it.toValue())
                }
            }
        } else TODO()
    }

    private fun interpretArrayLiteral(arrayLiteralNode: ArrayLiteralNode): Completion {
        val array = JSArrayObject.create(Agent.runningContext.realm)
        if (arrayLiteralNode.elements.isEmpty())
            return normalCompletion(array)
        arrayLiteralNode.elements.forEachIndexed { index, element ->
            when (element.type) {
                ArrayElementNode.Type.Normal -> {
                    val initResult = interpretExpression(element.expression!!).ifAbrupt { return it }
                    val initValue = Operations.getValue(initResult.value)
                    ifError { return it }
                    Operations.createDataPropertyOrThrow(array, index.toValue(), initValue)
                    ifError { return it }
                }
                ArrayElementNode.Type.Spread -> TODO()
                ArrayElementNode.Type.Elision -> { }
            }
        }
        return normalCompletion(array)
    }

    private fun interpretMemberExpression(memberExpressionNode: MemberExpressionNode): Completion {
        return when (memberExpressionNode.type) {
            MemberExpressionNode.Type.Computed -> {
                val baseReference = interpretExpression(memberExpressionNode.lhs).ifAbrupt { return it }
                val baseValue = Operations.getValue(baseReference.value)
                ifError { return it }
                // TODO: Strict mode
                val exprRef = interpretExpression(memberExpressionNode.rhs).ifAbrupt { return it }
                val exprValue = Operations.getValue(exprRef.value)
                ifError { return it }
                val result = Operations.evaluatePropertyAccessWithExpressionKey(baseValue, exprValue, false)
                ifError { return it }
                normalCompletion(result)
            }
            MemberExpressionNode.Type.NonComputed -> {
                val baseReference = interpretExpression(memberExpressionNode.lhs).ifAbrupt { return it }
                val baseValue = Operations.getValue(baseReference.value)
                ifError { return it }
                // TODO: Strict mode
                val name = (memberExpressionNode.rhs as IdentifierNode).identifierName
                val result = Operations.evaluatePropertyAccessWithIdentifierKey(baseValue, name, false)
                ifError { return it }
                normalCompletion(result)
            }
            MemberExpressionNode.Type.Tagged -> TODO()
        }
    }

    private fun interpretOptionalExpression(optionalExpressionNode: OptionalExpressionNode): Completion {
        TODO()
    }

    private fun interpretAssignmentExpression(assignmentExpressionNode: AssignmentExpressionNode): Completion {
        if (assignmentExpressionNode.op != AssignmentExpressionNode.Operator.Equals)
            TODO()

        if (assignmentExpressionNode.lhs.let { it is ObjectLiteralNode && it is ArrayLiteralNode })
            TODO()

        val lref = interpretExpression(assignmentExpressionNode.lhs).ifAbrupt { return it }
        val rref = interpretExpression(assignmentExpressionNode.rhs).ifAbrupt { return it }
        val rval = Operations.getValue(rref.value)
        ifError { return it }
        Operations.putValue(lref.value, rval)
        ifError { return it }
        return normalCompletion(rval)
    }

    private fun interpretConditionalExpression(conditionalExpressionNode: ConditionalExpressionNode): Completion {
        val lref = interpretExpression(conditionalExpressionNode.predicate)
        val lval = Operations.getValue(lref.value).let { value ->
            ifError { return it }
            Operations.toBoolean(value)
        }
        ifError { return it }
        if (lval == JSTrue) {
            val trueRef = interpretExpression(conditionalExpressionNode.ifTrue).ifAbrupt { return it }
            val trueVal = Operations.getValue(trueRef.value)
            ifError { return it }
            return normalCompletion(trueVal)
        } else {
            val falseRef = interpretExpression(conditionalExpressionNode.ifFalse).ifAbrupt { return it }
            val falseVal = Operations.getValue(falseRef.value)
            ifError { return it }
            return normalCompletion(falseVal)
        }
    }

    private fun interpretCoalesceExpression(coalesceExpressionNode: CoalesceExpressionNode): Completion {
        val lref = interpretExpression(coalesceExpressionNode.lhs).ifAbrupt { return it }
        val lval = Operations.getValue(lref.value)
        ifError { return it }
        if (lval != JSUndefined && lval != JSNull)
            return normalCompletion(lval)
        val rref = interpretExpression(coalesceExpressionNode.rhs).ifAbrupt { return it }
        val rval = Operations.getValue(rref.value)
        ifError { return it }
        return normalCompletion(rval)
    }

    private fun interpretLogicalORExpression(logicalORExpressionNode: LogicalORExpressionNode): Completion {
        val lref = interpretExpression(logicalORExpressionNode.lhs).ifAbrupt { return it }
        val lval = Operations.getValue(lref.value)
        ifError { return it }
        val lbool = Operations.toBoolean(lval)
        ifError { return it }
        return if (lbool == JSTrue) {
            normalCompletion(lval)
        } else {
            val rref = interpretExpression(logicalORExpressionNode.rhs).ifAbrupt { return it }
            val rval = Operations.getValue(rref.value)
            ifError { return it }
            normalCompletion(rval)
        }
    }

    private fun interpretLogicalANDExpression(logicalANDExpressionNode: LogicalANDExpressionNode): Completion {
        val lref = interpretExpression(logicalANDExpressionNode.lhs).ifAbrupt { return it }
        val lval = Operations.getValue(lref.value)
        ifError { return it }
        val lbool = Operations.toBoolean(lval)
        ifError { return it }
        return if (lbool == JSFalse) {
            normalCompletion(lval)
        } else {
            val rref = interpretExpression(logicalANDExpressionNode.rhs).ifAbrupt { return it }
            val rval = Operations.getValue(rref.value)
            ifError { return it }
            normalCompletion(rval)
        }
    }

    private fun evaluateStringOrNumericBinaryExpression(lhs: ExpressionNode, rhs: ExpressionNode, op: String): Completion {
        val lref = interpretExpression(lhs).ifAbrupt { return it }
        val lval = Operations.getValue(lref.value)
        ifError { return it }
        val rref = interpretExpression(rhs).ifAbrupt { return it }
        val rval = Operations.getValue(rref.value)
        ifError { return it }
        val result = Operations.applyStringOrNumericBinaryOperator(lval, rval, op)
        ifError { return it }
        return normalCompletion(result)
    }

    private fun interpretBitwiseORExpression(bitwiseORExpressionNode: BitwiseORExpressionNode): Completion {
        return evaluateStringOrNumericBinaryExpression(bitwiseORExpressionNode.lhs, bitwiseORExpressionNode.rhs, "|")
    }

    private fun interpretBitwiseXORExpression(bitwiseXORExpressionNode: BitwiseXORExpressionNode): Completion {
        return evaluateStringOrNumericBinaryExpression(bitwiseXORExpressionNode.lhs, bitwiseXORExpressionNode.rhs, "^")
    }

    private fun interpretBitwiseANDExpression(bitwiseANDExpressionNode: BitwiseANDExpressionNode): Completion {
        return evaluateStringOrNumericBinaryExpression(bitwiseANDExpressionNode.lhs, bitwiseANDExpressionNode.rhs, "&")
    }

    private fun interpretEqualityExpression(equalityExpressionNode: EqualityExpressionNode): Completion {
        val lref = interpretExpression(equalityExpressionNode.lhs).ifAbrupt { return it }
        val lval = Operations.getValue(lref.value)
        ifError { return it }
        val rref = interpretExpression(equalityExpressionNode.rhs).ifAbrupt { return it }
        val rval = Operations.getValue(rref.value)
        ifError { return it }

        val result = when (equalityExpressionNode.op) {
            EqualityExpressionNode.Operator.StrictEquality -> Operations.strictEqualityComparison(rval, lval).also {
                ifError { return it }
            }
            EqualityExpressionNode.Operator.StrictInequality -> Operations.strictEqualityComparison(rval, lval).let { value ->
                ifError { return it }
                if (value is JSTrue) JSFalse else JSTrue
            }
            EqualityExpressionNode.Operator.NonstrictEquality -> Operations.abstractEqualityComparison(rval, lval).also {
                ifError { return it }
            }
            EqualityExpressionNode.Operator.NonstrictInequality -> Operations.abstractEqualityComparison(rval, lval).let { value ->
                ifError { return it }
                if (value is JSTrue) JSFalse else JSTrue
            }
        }

        return normalCompletion(result)
    }

    private fun interpretRelationalExpression(relationalExpressionNode: RelationalExpressionNode): Completion {
        val lref = interpretExpression(relationalExpressionNode.lhs).ifAbrupt { return it }
        val lval = Operations.getValue(lref.value)
        ifError { return it }
        val rref = interpretExpression(relationalExpressionNode.rhs).ifAbrupt { return it }
        val rval = Operations.getValue(rref.value)
        ifError { return it }

        val result = when (relationalExpressionNode.op) {
            RelationalExpressionNode.Operator.LessThan -> {
                Operations.abstractRelationalComparison(lval, rval, true).let { value ->
                    ifError { return it }
                    if (value == JSUndefined) JSFalse else value
                }
            }
            RelationalExpressionNode.Operator.GreaterThan -> {
                Operations.abstractRelationalComparison(rval, lval, false).let { value ->
                    ifError { return it }
                    if (value == JSUndefined) JSFalse else value
                }
            }
            RelationalExpressionNode.Operator.LessThanEquals -> {
                Operations.abstractRelationalComparison(rval, lval, false).let { value ->
                    ifError { return it }
                    if (value == JSFalse) JSTrue else value
                }
            }
            RelationalExpressionNode.Operator.GreaterThanEquals -> {
                Operations.abstractRelationalComparison(lval, rval, true).let { value ->
                    ifError { return it }
                    if (value == JSFalse) JSTrue else value
                }
            }
            RelationalExpressionNode.Operator.Instanceof -> Operations.instanceofOperator(lval, rval).also {
                ifError { return it }
            }
            RelationalExpressionNode.Operator.In -> {
                if (rval !is JSObject) {
                    throwError<JSTypeErrorObject>("right-hand side of 'in' operator must be an object")
                    return throwCompletion()
                }
                val key = Operations.toPropertyKey(lval)
                ifError { return it }
                Operations.hasProperty(rval, key).also {
                    ifError { return it }
                }
            }
        }

        return normalCompletion(result)
    }

    private fun interpretShiftExpression(shiftExpressionNode: ShiftExpressionNode): Completion {
        return evaluateStringOrNumericBinaryExpression(
            shiftExpressionNode.lhs,
            shiftExpressionNode.rhs,
            when (shiftExpressionNode.op) {
                ShiftExpressionNode.Operator.ShiftLeft -> "<<"
                ShiftExpressionNode.Operator.ShiftRight -> ">>"
                ShiftExpressionNode.Operator.UnsignedShiftRight -> ">>>"
            },
        )
    }

    private fun interpretAdditiveExpression(additiveExpressionNode: AdditiveExpressionNode): Completion {
        return evaluateStringOrNumericBinaryExpression(
            additiveExpressionNode.lhs,
            additiveExpressionNode.rhs,
            if (additiveExpressionNode.isSubtraction) "-" else "+",
        )
    }

    private fun interpretMultiplicationExpression(multiplicativeExpressionNode: MultiplicativeExpressionNode): Completion {
        return evaluateStringOrNumericBinaryExpression(
            multiplicativeExpressionNode.lhs,
            multiplicativeExpressionNode.rhs,
            when (multiplicativeExpressionNode.op) {
                MultiplicativeExpressionNode.Operator.Multiply -> "*"
                MultiplicativeExpressionNode.Operator.Divide -> "/"
                MultiplicativeExpressionNode.Operator.Modulo -> "%"
            },
        )
    }

    private fun interpretExponentiationExpression(exponentiationExpressionNode: ExponentiationExpressionNode): Completion {
        return evaluateStringOrNumericBinaryExpression(
            exponentiationExpressionNode.lhs,
            exponentiationExpressionNode.rhs,
            "**",
        )
    }

    private fun interpretUnaryExpression(unaryExpressionNode: UnaryExpressionNode): Completion {
        val exprRef = interpretExpression(unaryExpressionNode.node).ifAbrupt { return it }

        val result = when (unaryExpressionNode.op) {
            UnaryExpressionNode.Operator.Delete -> Operations.deleteOperator(exprRef.value).also {
                ifError { return it }
            }
            UnaryExpressionNode.Operator.Void -> {
                val exprValue = Operations.getValue(exprRef.value).also { ifError { return it } }
                JSUndefined
            }
            UnaryExpressionNode.Operator.Typeof -> {
                val exprValue = Operations.getValue(exprRef.value).also { ifError { return it } }
                when (exprValue) {
                    is JSUndefined -> "undefined"
                    is JSNull -> "object"
                    is JSBoolean -> "boolean"
                    is JSNumber -> "number"
                    is JSString -> "string"
                    is JSSymbol -> "symbol"
                    is JSBigInt -> "bigint"
                    is JSFunction -> "function"
                    is JSObject -> "object"
                    else -> unreachable()
                }.toValue()
            }
            UnaryExpressionNode.Operator.Plus -> {
                val exprValue = Operations.getValue(exprRef.value).also { ifError { return it } }
                Operations.toNumber(exprValue).also { ifError { return it } }
            }
            UnaryExpressionNode.Operator.Minus -> {
                val exprValue = Operations.getValue(exprRef.value).also { ifError { return it } }
                val oldValue = Operations.toNumeric(exprValue)
                ifError { return it }
                if (oldValue is JSBigInt)
                    TODO()
                Operations.numericUnaryMinus(oldValue)
            }
            UnaryExpressionNode.Operator.BitwiseNot -> {
                val exprValue = Operations.getValue(exprRef.value).also { ifError { return it } }
                val oldValue = Operations.toNumeric(exprValue)
                ifError { return it }
                if (oldValue is JSBigInt)
                    TODO()
                Operations.numericBitwiseNOT(oldValue)
            }
            UnaryExpressionNode.Operator.Not -> {
                val exprValue = Operations.getValue(exprRef.value).also { ifError { return it } }
                val oldValue = Operations.toBoolean(exprValue)
                if (oldValue == JSFalse) JSTrue else JSFalse
            }
        }

        return normalCompletion(result)
    }

    private fun interpretUpdateExpression(updateExpressionNode: UpdateExpressionNode): Completion {
        val expr = interpretExpression(updateExpressionNode.target).ifAbrupt { return it }
        val oldValue = Operations.getValue(expr.value)
        ifError { return it }
        if (oldValue is JSBigInt)
            TODO()
        val newValue = if (updateExpressionNode.isIncrement) {
            Operations.numericAdd(oldValue, JSNumber(1.0))
        } else {
            Operations.numericSubtract(oldValue, JSNumber(1.0))
        }
        ifError { return it }
        Operations.putValue(expr.value, newValue)
        return if (updateExpressionNode.isPostfix) {
            oldValue
        } else {
            newValue
        }.let(::normalCompletion)
    }

    private inline fun Completion.ifNormal(block: (Completion) -> Unit): Completion {
        if (isNormal)
            block(this)
        return this
    }

    private inline fun Completion.ifReturn(block: (Completion) -> Unit): Completion {
        if (isReturn)
            block(this)
        return this
    }

    private inline fun Completion.ifBreak(block: (Completion) -> Unit): Completion {
        if (isBreak)
            block(this)
        return this
    }

    private inline fun Completion.ifContinue(block: (Completion) -> Unit): Completion {
        if (isContinue)
            block(this)
        return this
    }

    private inline fun Completion.ifThrow(block: (Completion) -> Unit): Completion {
        if (isThrow)
            block(this)
        return this
    }

    private inline fun Completion.ifAbrupt(block: (Completion) -> Unit): Completion {
        if (type != Completion.Type.Normal)
            block(this)
        return this
    }

    private fun normalCompletion(argument: JSValue = JSEmpty) = Completion(Completion.Type.Normal, argument, null)

    private fun returnCompletion(argument: JSValue = JSUndefined) = Completion(Completion.Type.Return, argument, null)

    private fun breakCompletion(target: String? = null) = Completion(Completion.Type.Break, JSEmpty, target)

    private fun continueCompletion(target: String? = null) = Completion(Completion.Type.Continue, JSEmpty, target)

    private fun throwCompletion(argument: JSValue = Agent.runningContext.error!!) = Completion(Completion.Type.Throw, argument, null)

    private fun updateEmpty(record: Completion, value: JSValue): Completion {
        if (record.isReturn || record.isThrow)
            ecmaAssert(record.value != JSEmpty)
        if (record.value != JSEmpty)
            return record
        return Completion(record.type, value, record.target)
    }

    private inline fun ifError(block: (Completion) -> Unit) {
        if (Agent.hasError())
            block(Completion(Completion.Type.Throw, Agent.runningContext.error!!, null))
    }

    private inline fun <reified T : JSErrorObject> error(message: String): Completion {
        throwError<T>(message)
        return Completion(Completion.Type.Throw, Agent.runningContext.error!!, null)
    }

    private fun syntaxError(message: String): Completion {
        return error<JSSyntaxErrorObject>(message)
    }

    private fun typeError(message: String): Completion {
        return error<JSTypeErrorObject>(message)
    }

    private fun referenceError(message: String): Completion {
        return error<JSReferenceErrorObject>(message)
    }
}
