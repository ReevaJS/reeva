package me.mattco.reeva.interpreter

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.expressions.TemplateLiteralNode
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.core.*
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.core.environment.DeclarativeEnvRecord
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.JSReference
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.functions.JSInterpretedFunction
import me.mattco.reeva.runtime.iterators.JSObjectPropertyIterator
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.utils.*
import kotlin.jvm.Throws

class Interpreter(private val realm: Realm, private val scriptOrModule: ScriptNode) {
    @Throws(ThrowException::class)
    fun interpret(): JSValue {
        val globalEnv = realm.globalEnv
        globalDeclarationInstantiation(scriptOrModule, globalEnv)
        return interpretScript(scriptOrModule)
    }

    private fun globalDeclarationInstantiation(body: ScriptNode, env: GlobalEnvRecord) {
        val lexNames = body.lexicallyDeclaredNames()
        val varNames = body.varDeclaredNames()
        lexNames.forEach {
            if (env.hasVarDeclaration(it))
                throwSyntaxError("TODO: message")
            if (env.hasLexicalDeclaration(it))
                throwSyntaxError("TODO: message")
        }
        varNames.forEach {
            if (env.hasLexicalDeclaration(it))
                throwSyntaxError("TODO: message")
        }
        val varDeclarations = body.varScopedDeclarations()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()
        val declaredFunctionNames = mutableListOf<String>()
        varDeclarations.asReversed().forEach {
            if (it !is VariableDeclarationNode && it !is ForBindingNode && it !is BindingIdentifierNode) {
                val functionName = it.boundNames()[0]
                if (functionName !in declaredFunctionNames) {
                    if (!env.canDeclareGlobalFunction(functionName))
                        throwTypeError("TODO: message")
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
                        throwTypeError("TODO: message")
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
    }

    //  This is public because it is used by the eval global function
    @ECMAImpl("14.1.22")
    fun instantiateFunctionObject(functionNode: FunctionDeclarationNode, scope: EnvRecord): JSFunction {
        val sourceText = "TODO"
        val function = ordinaryFunctionCreate(
            realm.functionProto,
            sourceText,
            functionNode.parameters,
            functionNode.body,
            JSFunction.ThisMode.NonLexical,
            false,
            scope
        )
        if (functionNode.identifier != null)
            setFunctionName(function, functionNode.identifier.identifierName.key())

        Operations.makeConstructor(function)

        return function
    }

    @ECMAImpl("9.2.3")
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
            functionDeclarationInstantiation(function, parameterList, arguments, body)
            if (body.statementList != null) {
                interpretStatementList(body.statementList)
            } else JSUndefined
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
    ) {
        val calleeContext = Agent.runningContext
        val strict = function.isStrict
        val parameterNames = formals.boundNames()
        val hasDuplicates = parameterNames.distinct().size != parameterNames.size
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

        val parameterBindings = if (argumentsObjectNeeded) {
            val argsObject = if (strict || !simpleParameterList) {
                Operations.createUnmappedArgumentsObject(arguments)
            } else {
                Operations.createMappedArgumentsObject(function, formals, arguments, env)
            }
            if (strict) {
                env.createImmutableBinding("arguments", false)
            } else {
                env.createMutableBinding("arguments", false)
            }
            env.initializeBinding("arguments", argsObject)
            parameterNames + listOf("arguments")
        } else parameterNames


        formals.functionParameters.parameters.forEachIndexed { index, parameter ->
            val lhs = Operations.resolveBinding(parameter.bindingElement.binding.identifier.identifierName)
            var value = if (index > arguments.lastIndex) {
                JSUndefined
            } else arguments[index]

            if (value == JSUndefined && parameter.bindingElement.binding.initializer != null) {
                val result = interpretExpression(parameter.bindingElement.binding.initializer.node)
                val defaultValue = Operations.getValue(result)
                value = defaultValue
            }

            if (hasDuplicates) {
                Operations.putValue(lhs, value)
            } else {
                Operations.initializeReferencedBinding(lhs, value)
            }
        }

        if (formals.restParameter != null) {
            val id = formals.restParameter.element.identifier.identifierName
            val startingIndex = formals.functionParameters.parameters.size
            val lhs = Operations.resolveBinding(id)
            val value = if (startingIndex > arguments.lastIndex) {
                Operations.arrayCreate(0)
            } else {
                val arr = Operations.arrayCreate(arguments.lastIndex - startingIndex + 1)
                arguments.subList(startingIndex, arguments.size).forEachIndexed { index, value ->
                    Operations.createDataPropertyOrThrow(arr, index.toValue(), value)
                }
                arr
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
    }

    @ECMAImpl("9.2.8")
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

    private fun interpretScript(scriptNode: ScriptNode): JSValue {
        return interpretStatementList(scriptNode.statementList)
    }

    private fun interpretStatementList(statementListNode: StatementListNode): JSValue {
        var lastValue: JSValue = JSUndefined
        statementListNode.statements.forEach { statement ->
            val result = interpretStatement(statement as StatementNode)
            if (result != JSEmpty)
                lastValue = result
        }
        return lastValue
    }

    private fun interpretStatement(statement: StatementNode): JSValue {
        return when (statement) {
            is BlockStatementNode -> interpretBlockStatement(statement)
            is VariableStatementNode -> interpretVariableStatement(statement)
            is EmptyStatementNode -> JSEmpty
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

    private fun interpretBlockStatement(blockStatementNode: BlockStatementNode): JSValue {
        return interpretBlock(blockStatementNode.block)
    }

    private fun interpretBlock(block: BlockNode): JSValue {
        if (block.statements == null)
            return JSEmpty
        val oldEnv = Agent.runningContext.lexicalEnv
        val blockEnv = DeclarativeEnvRecord.create(Agent.runningContext.lexicalEnv)
        blockDeclarationInstantiation(block.statements, blockEnv)
        Agent.runningContext.lexicalEnv = blockEnv
        return interpretStatementList(block.statements).also {
            Agent.runningContext.lexicalEnv = oldEnv
        }
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

    private fun interpretVariableStatement(variableStatementNode: VariableStatementNode): JSValue {
        variableStatementNode.declarations.declarations.forEach { decl ->
            if (decl.initializer == null)
                return@forEach
            val lhs = Operations.resolveBinding(decl.identifier.identifierName)
            val rhsResult = interpretExpression(decl.initializer.node)
            val rhs = Operations.getValue(rhsResult)
            Operations.putValue(lhs, rhs)
        }
        return JSUndefined
    }

    private fun interpretExpressionStatement(expressionStatement: ExpressionStatementNode): JSValue {
        return interpretExpression(expressionStatement.node).also(Operations::getValue)
    }

    private fun interpretIfStatement(ifStatement: IfStatementNode): JSValue {
        val exprRef = interpretExpression(ifStatement.condition)
        val exprValue = Operations.toBoolean(Operations.getValue(exprRef))
        return when {
            exprValue.value -> updateEmpty(JSUndefined) { interpretStatement(ifStatement.trueBlock) }
            ifStatement.falseBlock != null -> updateEmpty(JSUndefined) { interpretStatement(ifStatement.falseBlock) }
            else -> JSUndefined
        }
    }

    private fun interpretBreakableStatement(breakableStatementNode: BreakableStatement): JSValue {
        return labelledEvaluation(breakableStatementNode, emptySet())
    }

    private fun interpretIterationStatement(iterationStatement: IterationStatement, labelSet: Set<String>): JSValue {
        return when (iterationStatement) {
            is DoWhileStatementNode -> interpretDoWhileStatement(iterationStatement, labelSet)
            is WhileStatementNode -> interpretWhileStatement(iterationStatement, labelSet)
            is ForStatementNode -> interpretForStatement(iterationStatement, labelSet)
            is ForInNode -> interpretForInNode(iterationStatement, labelSet)
            is ForOfNode -> interpretForOfNode(iterationStatement, labelSet)
            else -> TODO()
        }
    }

    private fun interpretForInNode(forInNode: ForInNode, labelSet: Set<String>): JSValue {
        val keyResult = forInOfHeadEvaluation(
            if (forInNode.decl is ForDeclarationNode) {
                forInNode.decl.boundNames()
            } else emptyList(),
            forInNode.expression,
            IterationKind.Enumerate
        )

        return forInOfBodyEvaluation(
            forInNode.decl,
            forInNode.body,
            keyResult,
            IterationKind.Enumerate,
            when (forInNode.decl) {
                is VariableDeclarationNode -> LHSKind.VarBinding
                is ForDeclarationNode -> LHSKind.LexicalBinding
                else -> LHSKind.Assignment
            },
            labelSet
        )
    }

    private fun interpretForOfNode(forOfNode: ForOfNode, labelSet: Set<String>): JSValue {
        val keyResult = forInOfHeadEvaluation(
            if (forOfNode.decl is ForDeclarationNode) {
                forOfNode.decl.boundNames()
            } else emptyList(),
            forOfNode.expression,
            IterationKind.Iterate,
        )

        return forInOfBodyEvaluation(
            forOfNode.decl,
            forOfNode.body,
            keyResult,
            IterationKind.Iterate,
            when (forOfNode.decl) {
                is VariableDeclarationNode -> LHSKind.VarBinding
                is ForDeclarationNode -> LHSKind.LexicalBinding
                else -> LHSKind.Assignment
            },
            labelSet
        )
    }

    private fun forInOfHeadEvaluation(uninitializedBoundNames: List<String>, expr: ExpressionNode, iterationKind: IterationKind): Operations.IteratorRecord {
        if (iterationKind == IterationKind.AsyncIterate)
            TODO()

        val oldEnv = Agent.runningContext.lexicalEnv
        if (uninitializedBoundNames.isNotEmpty()) {
            ecmaAssert(uninitializedBoundNames.groupBy { it }.size == uninitializedBoundNames.size)
            val newEnv = DeclarativeEnvRecord.create(oldEnv)
            uninitializedBoundNames.forEach { name ->
                newEnv.createMutableBinding(name, false)
            }
            Agent.runningContext.lexicalEnv = newEnv
        }

        val exprRef = interpretExpression(expr)
        Agent.runningContext.lexicalEnv = oldEnv
        val exprValue = Operations.getValue(exprRef)

        return if (iterationKind == IterationKind.Enumerate) {
            if (exprValue == JSUndefined || exprValue == JSEmpty)
                throw BreakException(null)
            val obj = Operations.toObject(exprValue)
            val iterator = JSObjectPropertyIterator.create(Agent.runningContext.realm, obj)
            val nextMethod = Operations.getV(iterator, "next".toValue())
            Operations.IteratorRecord(iterator, nextMethod, false)
        } else Operations.getIterator(exprValue, Operations.IteratorHint.Sync)
    }

    private fun forInOfBodyEvaluation(
        lhs: ASTNode,
        statement: StatementNode,
        iteratorRecord: Operations.IteratorRecord,
        iterationKind: IterationKind,
        lhsKind: LHSKind,
        labelSet: Set<String>,
        iteratorKind: Operations.IteratorHint? = Operations.IteratorHint.Sync
    ): JSValue {
        if (iterationKind == IterationKind.AsyncIterate)
            TODO()
        if (iteratorKind == Operations.IteratorHint.Async)
            TODO()

        val oldEnv = Agent.runningContext.lexicalEnv
        var value: JSValue = JSEmpty
        val destructuring = lhs.isDestructuring()
        if (destructuring)
            TODO()

        while (true) {
            val nextResult = Operations.call(iteratorRecord.nextMethod, iteratorRecord.iterator)
            if (nextResult !is JSObject)
                throwTypeError("TODO: message")

            val done = Operations.iteratorComplete(nextResult)
            if (done == JSTrue)
                return value

            val nextValue = Operations.iteratorValue(nextResult)


            try {
                val lhsRef = if (lhsKind != LHSKind.LexicalBinding) {
                    interpretExpression(lhs as ExpressionNode)
                } else {
                    ecmaAssert(lhs is ForDeclarationNode)
                    val iterationEnv = DeclarativeEnvRecord.create(oldEnv)
                    bindingInstantiation(lhs, iterationEnv)
                    Agent.runningContext.lexicalEnv = iterationEnv
                    val boundNames = lhs.boundNames()
                    expect(boundNames.size == 1)
                    Operations.resolveBinding(boundNames[0])
                }

                if (lhsKind == LHSKind.LexicalBinding) {
                    Operations.initializeReferencedBinding(lhsRef as JSReference, nextValue)
                } else {
                    Operations.putValue(lhsRef, nextValue)
                }
            } catch (e: Throwable) {
                Agent.runningContext.lexicalEnv = oldEnv
                if (iterationKind != IterationKind.Enumerate)
                    Operations.iteratorClose(iteratorRecord, value)
                throw e
            }

            val result = try {
                interpretStatement(statement)
            } catch (e: FlowException) {
                if (e is ThrowException) {
                    if (iterationKind != IterationKind.Enumerate)
                        Operations.iteratorClose(iteratorRecord, value)
                    throw e
                }
                if (!loopContinues(e, labelSet)) {
                    if (iterationKind != IterationKind.Enumerate)
                        Operations.iteratorClose(iteratorRecord, value)
                    return value
                }
                JSEmpty
            } finally {
                Agent.runningContext.lexicalEnv = oldEnv
            }

            if (result != JSEmpty)
                value = result
        }
    }

    private fun bindingInstantiation(forDeclarationNode: ForDeclarationNode, env: EnvRecord): JSValue {
        ecmaAssert(env is DeclarativeEnvRecord)
        forDeclarationNode.binding.boundNames().forEach { name ->
            if (forDeclarationNode.isConst) {
                env.createImmutableBinding(name, true)
            } else {
                env.createMutableBinding(name, false)
            }
        }
        return JSEmpty
    }

    enum class IterationKind {
        Enumerate,
        Iterate,
        AsyncIterate,
    }

    enum class LHSKind {
        Assignment,
        VarBinding,
        LexicalBinding,
    }

    private fun interpretDoWhileStatement(doWhileStatementNode: DoWhileStatementNode, labelSet: Set<String>): JSValue {
        var value: JSValue = JSUndefined
        while (true) {
            val result = try {
                interpretStatement(doWhileStatementNode.body)
            } catch (e: FlowException) {
                if (e is ThrowException)
                    throw e
                if (!loopContinues(e, labelSet))
                    return value
                JSEmpty
            }
            if (result != JSEmpty)
                value = result
            val exprRef = interpretExpression(doWhileStatementNode.condition)
            val exprValue = Operations.getValue(exprRef)
            if (Operations.toBoolean(exprValue) == JSFalse)
                return value
        }
    }

    private fun interpretWhileStatement(whileStatementNode: WhileStatementNode, labelSet: Set<String>): JSValue {
        var value: JSValue = JSUndefined
        while (true) {
            val exprRef = interpretExpression(whileStatementNode.condition)
            val exprValue = Operations.getValue(exprRef)
            if (Operations.toBoolean(exprValue) == JSFalse)
                return value
            val result = try {
                interpretStatement(whileStatementNode.body)
            } catch (e: FlowException) {
                if (e is ThrowException)
                    throw e
                if (!loopContinues(e, labelSet))
                    return value
                JSEmpty
            }
            if (result != JSEmpty)
                value = result
        }
    }

    private fun interpretForStatement(forStatementNode: ForStatementNode, labelSet: Set<String>): JSValue {
        when (forStatementNode.initializer) {
            is ExpressionNode -> {
                val exprRef = interpretExpression(forStatementNode.initializer)
                Operations.getValue(exprRef)

                return forBodyEvaluation(
                    forStatementNode.condition,
                    forStatementNode.incrementer,
                    forStatementNode.body,
                    emptyList(),
                    labelSet
                )
            }
            is VariableStatementNode -> {
                interpretVariableStatement(forStatementNode.initializer)

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
                return try {
                    interpretLexicalDeclaration(forStatementNode.initializer)
                    forBodyEvaluation(
                        forStatementNode.condition,
                        forStatementNode.incrementer,
                        forStatementNode.body,
                        if (isConst) emptyList() else boundNames,
                        labelSet,
                    )
                } finally {
                    Agent.runningContext.lexicalEnv = oldEnv
                }
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
    ): JSValue {
        var value: JSValue = JSUndefined
        createPerIterationEnvironment(perIterationBindings)
        while (true) {
            if (condition != null) {
                val testRef = interpretExpression(condition)
                val testValue = Operations.getValue(testRef)
                if (Operations.toBoolean(testValue) == JSFalse)
                    return value
            }
            val result = try {
                interpretStatement(body)
            } catch (e: FlowException) {
                if (e is ThrowException)
                    throw e
                if (!loopContinues(e, labelSet))
                    return value
                JSEmpty
            }
            if (result != JSEmpty)
                value = result
            createPerIterationEnvironment(perIterationBindings)
            if (incrementer != null) {
                val incRef = interpretExpression(incrementer)
                Operations.getValue(incRef)
            }
        }
    }

    private fun createPerIterationEnvironment(perIterationBindings: List<String>): JSValue {
        if (perIterationBindings.isEmpty())
            return JSEmpty

        val lastIterationEnv = Agent.runningContext.lexicalEnv
        expect(lastIterationEnv != null)
        val outer = lastIterationEnv.outerEnv
        ecmaAssert(outer != null)
        val thisIterationEnv = DeclarativeEnvRecord.create(outer)

        try {
            perIterationBindings.forEach { binding ->
                thisIterationEnv.createMutableBinding(binding, false)
                val lastValue = lastIterationEnv.getBindingValue(binding, true)
                thisIterationEnv.initializeBinding(binding, lastValue)
            }
        } finally {
            Agent.runningContext.lexicalEnv = thisIterationEnv
        }

        return JSUndefined
    }

    @ECMAImpl("13.7.1.2")
    private fun loopContinues(exception: FlowException, labelSet: Set<String>): Boolean {
        return when {
            exception !is ContinueException -> false
            exception.label == null -> true
            exception.label in labelSet -> true
            else -> false
        }
    }

    private fun interpretBreakStatement(breakStatementNode: BreakStatementNode): JSValue {
        if (breakStatementNode.label == null) {
            throw BreakException(null)
        } else {
            throw BreakException(breakStatementNode.label.identifierName)
        }
    }

    private fun labelledEvaluation(node: StatementNode, labelSet: Set<String>): JSValue {
        return when (node) {
            is LabelledStatementNode -> {
                try {
                    labelledEvaluation(node.item, labelSet + node.label.identifierName)
                } catch (e: BreakException) {
                    if (e.label == node.label.identifierName)
                        JSEmpty
                    throw e
                }
            }
            is FunctionDeclarationNode -> interpretFunctionDeclaration(node)
            is BreakableStatement -> {
                expect(node is IterationStatement)
                try {
                    interpretIterationStatement(node, labelSet)
                } catch (e: BreakException) {
                    if (e.label == null) {
                        JSUndefined
                    } else throw e
                }
            }
            else -> TODO()
        }
    }

    private fun interpretLexicalDeclaration(lexicalDeclarationNode: LexicalDeclarationNode): JSValue {
        lexicalDeclarationNode.bindingList.lexicalBindings.forEach {
            if (it.initializer == null) {
                expect(!lexicalDeclarationNode.isConst)
                val lhs = Operations.resolveBinding(it.identifier.identifierName)
                Operations.initializeReferencedBinding(lhs, JSUndefined)
            } else {
                val lhs = Operations.resolveBinding(it.identifier.identifierName)
                val rhs = interpretExpression(it.initializer.node)
                val value = Operations.getValue(rhs)
                Operations.initializeReferencedBinding(lhs, value)
            }
        }
        return JSEmpty
    }

    private fun interpretFunctionDeclaration(functionDeclarationNode: FunctionDeclarationNode): JSValue {
        return JSEmpty
    }

    private fun interpretReturnStatement(returnStatementNode: ReturnStatementNode): JSValue {
        if (returnStatementNode.node == null) {
            throw ReturnException(JSUndefined)
        } else {
            val exprRef = interpretExpression(returnStatementNode.node)
            throw ReturnException(Operations.getValue(exprRef))
        }
    }

    private fun interpretThrowStatement(throwStatementNode: ThrowStatementNode): JSValue {
        val exprRef = interpretExpression(throwStatementNode.expr)
        throw ThrowException(Operations.getValue(exprRef))
    }

    private fun interpretTryStatement(tryStatementNode: TryStatementNode): JSValue {
        return try {
            updateEmpty(JSUndefined) { interpretBlock(tryStatementNode.tryBlock) }
        } catch (e: ThrowException) {
            if (tryStatementNode.catchNode == null)
                throw e

            if (tryStatementNode.catchNode.catchParameter == null) {
                updateEmpty(JSUndefined) { interpretBlock(tryStatementNode.catchNode.block) }
            } else {
                val oldEnv = Agent.runningContext.lexicalEnv
                val catchEnv = DeclarativeEnvRecord.create(oldEnv)
                val parameter = tryStatementNode.catchNode.catchParameter
                parameter.boundNames().forEach { name ->
                    catchEnv.createMutableBinding(name, false)
                }
                Agent.runningContext.lexicalEnv = catchEnv
                try {
                    bindingInitialization(parameter.identifierName, e.value, catchEnv)
                    updateEmpty(JSUndefined) { interpretBlock(tryStatementNode.catchNode.block) }
                } finally {
                    Agent.runningContext.lexicalEnv = oldEnv
                }
            }
        } finally {
            if (tryStatementNode.finallyBlock != null)
                interpretBlock(tryStatementNode.finallyBlock)
        }
    }

    private fun bindingInitialization(identifier: String, value: JSValue, env: EnvRecord?): JSValue {
        return if (env == null) {
            val lhs = Operations.resolveBinding(identifier)
            Operations.putValue(lhs, value)
            JSEmpty
        } else {
            env.initializeBinding(identifier, value)
            JSUndefined
        }
    }

    private fun interpretExpression(expression: ExpressionNode): JSValue {
        return when (expression) {
            ThisNode -> interpretThis()
            is CommaExpressionNode -> interpretCommaExpressionNode(expression)
            is IdentifierReferenceNode -> interpretIdentifierReference(expression)
            is FunctionExpressionNode -> interpretFunctionExpression(expression)
            is ArrowFunctionNode -> interpretArrowFunction(expression)
            is LiteralNode -> interpretLiteral(expression)
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
            is ParenthesizedExpressionNode -> interpretExpression(expression.target)
            is ForBindingNode -> interpretForBinding(expression)
            is TemplateLiteralNode -> interpretTemplateLiteral(expression)
            else -> unreachable()
        }
    }

    private fun interpretTemplateLiteral(templateLiteralNode: TemplateLiteralNode): JSValue {
        return buildString {
            templateLiteralNode.parts.forEach {
                append(Operations.toString(Operations.getValue(interpretExpression(it))))
            }
        }.toValue()
    }

    private fun interpretForBinding(forBindingNode: ForBindingNode): JSValue {
        return Operations.resolveBinding(forBindingNode.identifier.identifierName)
    }

    private fun interpretThis(): JSValue {
        return Operations.resolveThisBinding()
    }

    private fun interpretCommaExpressionNode(commaExpression: CommaExpressionNode): JSValue {
        var result: JSValue? = null
        commaExpression.expressions.forEach {
            val exprResult = interpretExpression(it)
            val value = Operations.getValue(exprResult)
            result = value
        }
        return result!!
    }

    private fun interpretIdentifierReference(identifierReference: IdentifierReferenceNode): JSValue {
        return Operations.resolveBinding(identifierReference.identifierName)
    }

    private fun interpretFunctionExpression(functionExpressionNode: FunctionExpressionNode): JSValue {
        return if (functionExpressionNode.identifier == null) {
            val scope = Agent.runningContext.lexicalEnv!!
            val sourceText = "TODO"
            val closure = ordinaryFunctionCreate(
                realm.functionProto,
                sourceText,
                functionExpressionNode.parameters,
                functionExpressionNode.body,
                JSFunction.ThisMode.NonLexical,
                false,
                scope,
            )
            setFunctionName(closure, "".key())
            Operations.makeConstructor(closure)
            closure
        } else {
            val scope = Agent.runningContext.lexicalEnv!!
            val funcEnv = DeclarativeEnvRecord.create(scope)
            val name = functionExpressionNode.identifier.identifierName
            funcEnv.createImmutableBinding(name, false)
            val sourceText = "TODO"
            val closure = ordinaryFunctionCreate(
                realm.functionProto,
                sourceText,
                functionExpressionNode.parameters,
                functionExpressionNode.body,
                JSFunction.ThisMode.NonLexical,
                false,
                funcEnv,
            )
            setFunctionName(closure, name.key())
            Operations.makeConstructor(closure)
            funcEnv.initializeBinding(name, closure)
            closure
        }
    }

    private fun interpretArrowFunction(arrowFunctionNode: ArrowFunctionNode): JSValue {
        val scope = Agent.runningContext.lexicalEnv!!
        val sourceText = "TODO"
        val parameters = arrowFunctionNode.parameters.let {
            if (it is BindingIdentifierNode) {
                FormalParametersNode(
                    FormalParameterListNode(
                        listOf(FormalParameterNode(BindingElementNode(SingleNameBindingNode(it, null))))
                    ),
                    null
                )
            } else it as FormalParametersNode
        }
        val body = arrowFunctionNode.body.let {
            if (it is ExpressionNode) {
                FunctionStatementList(StatementListNode(listOf(
                    ReturnStatementNode(it)
                )))
            } else it as FunctionStatementList
        }
        val closure = ordinaryFunctionCreate(
            realm.functionProto,
            sourceText,
            parameters,
            body,
            JSFunction.ThisMode.Lexical,
            false,
            scope,
        )
        setFunctionName(closure, "".key())
        return closure
    }

    private fun interpretLiteral(literalNode: LiteralNode): JSValue {
        return when (literalNode) {
            is NullNode -> JSNull
            is BooleanNode -> literalNode.value.toValue()
            is NumericLiteralNode -> literalNode.value.toValue()
            is StringLiteralNode -> literalNode.value.toValue()
            else -> unreachable()
        }
    }

    private fun interpretNewExpression(newExpressionNode: NewExpressionNode): JSValue {
        val ref = interpretExpression(newExpressionNode.target)
        val constructor = Operations.getValue(ref)
        val arguments = if (newExpressionNode.arguments == null) {
            emptyList()
        } else {
            argumentsListEvaluation(newExpressionNode.arguments)
        }
        if (!Operations.isConstructor(constructor))
            throwTypeError("${Operations.toPrintableString(constructor)} is not a constructor")
        return Operations.construct(constructor, arguments)
    }

    // Null indicates an error
    private fun argumentsListEvaluation(argumentsNode: ArgumentsNode): List<JSValue> {
        val argumentEntries = argumentsNode.arguments
        if (argumentEntries.isEmpty())
            return emptyList()
        val arguments = mutableListOf<JSValue>()
        argumentEntries.forEach { entry ->
            val ref = interpretExpression(entry.expression)
            val value = Operations.getValue(ref)
            if (entry.isSpread) {
                val record = Operations.getIterator(value)
                while (true) {
                    val next = Operations.iteratorStep(record)
                    if (next == JSFalse)
                        break
                    val nextArg = Operations.iteratorValue(next)
                    arguments.add(nextArg)
                }
            } else {
                arguments.add(value)
            }
        }
        return arguments
    }

    private fun interpretCallExpression(callExpressionNode: CallExpressionNode): JSValue {
        val ref = interpretExpression(callExpressionNode.target)
        val func = Operations.getValue(ref)
        val args = argumentsListEvaluation(callExpressionNode.arguments)
        return Operations.evaluateCall(func, ref, args, false)
    }

    private fun interpretObjectLiteral(objectLiteralNode: ObjectLiteralNode): JSValue {
        val obj = JSObject.create(Agent.runningContext.realm)
        if (objectLiteralNode.list == null)
            return obj
        objectLiteralNode.list.properties.forEach { property ->
            when (property.type) {
                PropertyDefinitionNode.Type.KeyValue -> {
                    val propKey = evaluatePropertyName(property.first)
                    val exprValueRef = interpretExpression(property.second!!)
                    val propValue = Operations.getValue(exprValueRef)
                    Operations.createDataPropertyOrThrow(obj, propKey, propValue)
                }
                PropertyDefinitionNode.Type.Shorthand -> {
                    expect(property.first is IdentifierReferenceNode)
                    val propName = property.first.identifierName
                    val exprValue = interpretIdentifierReference(property.first)
                    val propValue = Operations.getValue(exprValue)
                    Operations.createDataPropertyOrThrow(obj, propName.toValue(), propValue)
                }
                PropertyDefinitionNode.Type.Method -> {
                    val method = property.first as MethodDefinitionNode

                    when (method.type) {
                        MethodDefinitionNode.Type.Normal -> {
                            val (key, closure) = defineMethod(method, obj)
                            setFunctionName(closure, key)
                            Operations.definePropertyOrThrow(
                                obj,
                                key,
                                Descriptor(closure, Descriptor.CONFIGURABLE or Descriptor.ENUMERABLE or Descriptor.WRITABLE)
                            )
                        }
                        MethodDefinitionNode.Type.Getter -> {
                            val propKey = Operations.toPropertyKey(evaluatePropertyName(method.identifier))
                            val closure = ordinaryFunctionCreate(
                                realm.functionProto,
                                "TODO",
                                method.parameters,
                                method.body,
                                JSFunction.ThisMode.NonLexical,
                                false,
                                Agent.runningContext.lexicalEnv!!,
                            )
                            Operations.makeMethod(closure, obj)
                            setFunctionName(closure, propKey, "get")
                            Operations.definePropertyOrThrow(
                                obj,
                                propKey,
                                Descriptor(JSEmpty, Descriptor.CONFIGURABLE or Descriptor.ENUMERABLE, closure)
                            )
                        }
                        MethodDefinitionNode.Type.Setter -> {
                            val propKey = Operations.toPropertyKey(evaluatePropertyName(method.identifier))
                            val closure = ordinaryFunctionCreate(
                                realm.functionProto,
                                "TODO",
                                method.parameters,
                                method.body,
                                JSFunction.ThisMode.NonLexical,
                                false,
                                Agent.runningContext.lexicalEnv!!,
                            )
                            Operations.makeMethod(closure, obj)
                            setFunctionName(closure, propKey, "set")
                            Operations.definePropertyOrThrow(
                                obj,
                                propKey,
                                Descriptor(JSEmpty, Descriptor.CONFIGURABLE or Descriptor.ENUMERABLE, setter = closure)
                            )
                        }
                        MethodDefinitionNode.Type.Generator -> TODO()
                        MethodDefinitionNode.Type.Async -> TODO()
                        MethodDefinitionNode.Type.AsyncGenerator -> TODO()
                    }
                }
                PropertyDefinitionNode.Type.Spread -> TODO()
            }
        }
        return obj
    }

    data class DefinedMethod(val key: PropertyKey, val closure: JSFunction)

    private fun defineMethod(method: MethodDefinitionNode, obj: JSObject, functionPrototype: JSObject? = null): DefinedMethod {
        expect(method.type == MethodDefinitionNode.Type.Normal)

        val propKey = evaluatePropertyName(method.identifier)
        val closure = ordinaryFunctionCreate(
            functionPrototype ?: realm.functionProto,
            "TODO",
            method.parameters,
            method.body ,
            JSFunction.ThisMode.NonLexical,
            false,
            Agent.runningContext.lexicalEnv!!,
        )
        Operations.makeMethod(closure, obj)
        return DefinedMethod(Operations.toPropertyKey(propKey), closure)
    }

    private fun evaluatePropertyName(propertyName: ASTNode): JSValue {
        return if (propertyName is PropertyNameNode) {
            if (propertyName.isComputed) {
                val exprValue = interpretExpression(propertyName.expr)
                val propName = Operations.getValue(exprValue)
                val key = Operations.toPropertyKey(propName).asValue
                key
            } else {
                when (val expr = propertyName.expr) {
                    is IdentifierNode -> expr.identifierName
                    is StringLiteralNode -> expr.value
                    is NumericLiteralNode -> Operations.toString(expr.value.toValue()).string
                    else -> unreachable()
                }.toValue()
            }
        } else TODO()
    }

    private fun interpretArrayLiteral(arrayLiteralNode: ArrayLiteralNode): JSValue {
        val array = Operations.arrayCreate(arrayLiteralNode.elements.size)
        if (arrayLiteralNode.elements.isEmpty())
            return array
        arrayLiteralNode.elements.forEachIndexed { index, element ->
            when (element.type) {
                ArrayElementNode.Type.Normal -> {
                    val initResult = interpretExpression(element.expression!!)
                    val initValue = Operations.getValue(initResult)
                    Operations.createDataPropertyOrThrow(array, index.toValue(), initValue)
                }
                ArrayElementNode.Type.Spread -> TODO()
                ArrayElementNode.Type.Elision -> { }
            }
        }
        return array
    }

    private fun interpretMemberExpression(memberExpressionNode: MemberExpressionNode): JSValue {
        return when (memberExpressionNode.type) {
            MemberExpressionNode.Type.Computed -> {
                val baseReference = interpretExpression(memberExpressionNode.lhs)
                val baseValue = Operations.getValue(baseReference)
                // TODO: Strict mode
                val exprRef = interpretExpression(memberExpressionNode.rhs)
                val exprValue = Operations.getValue(exprRef)
                val result = Operations.evaluatePropertyAccessWithExpressionKey(baseValue, exprValue, false)
                result
            }
            MemberExpressionNode.Type.NonComputed -> {
                val baseReference = interpretExpression(memberExpressionNode.lhs)
                val baseValue = Operations.getValue(baseReference)
                // TODO: Strict mode
                val name = (memberExpressionNode.rhs as IdentifierNode).identifierName
                val result = Operations.evaluatePropertyAccessWithIdentifierKey(baseValue, name, false)
                result
            }
            MemberExpressionNode.Type.Tagged -> TODO()
        }
    }

    private fun interpretOptionalExpression(optionalExpressionNode: OptionalExpressionNode): JSValue {
        TODO()
    }

    private fun interpretAssignmentExpression(assignmentExpressionNode: AssignmentExpressionNode): JSValue {
        if (assignmentExpressionNode.lhs.let { it is ObjectLiteralNode && it is ArrayLiteralNode })
            TODO()

        if (assignmentExpressionNode.op == AssignmentExpressionNode.Operator.Equals) {
            val lref = interpretExpression(assignmentExpressionNode.lhs)
            val rref = interpretExpression(assignmentExpressionNode.rhs)
            val rval = Operations.getValue(rref)
            Operations.putValue(lref, rval)
            return rval
        } else {
            val lref = interpretExpression(assignmentExpressionNode.lhs)
            val lval = Operations.getValue(lref)
            val rref = interpretExpression(assignmentExpressionNode.rhs)
            val rval = Operations.getValue(rref)
            val newValue = Operations.applyStringOrNumericBinaryOperator(lval, rval, assignmentExpressionNode.op.symbol.dropLast(1))
            Operations.putValue(lref, newValue)
            return newValue
        }
    }

    private fun interpretConditionalExpression(conditionalExpressionNode: ConditionalExpressionNode): JSValue {
        val lref = interpretExpression(conditionalExpressionNode.predicate)
        val lval = Operations.getValue(lref).let { value ->
            Operations.toBoolean(value)
        }
        return if (lval == JSTrue) {
            Operations.getValue(interpretExpression(conditionalExpressionNode.ifTrue))
        } else {
            Operations.getValue(interpretExpression(conditionalExpressionNode.ifFalse))
        }
    }

    private fun interpretCoalesceExpression(coalesceExpressionNode: CoalesceExpressionNode): JSValue {
        val lref = interpretExpression(coalesceExpressionNode.lhs)
        val lval = Operations.getValue(lref)
        if (lval != JSUndefined && lval != JSNull)
            return lval
        val rref = interpretExpression(coalesceExpressionNode.rhs)
        return Operations.getValue(rref)
    }

    private fun interpretLogicalORExpression(logicalORExpressionNode: LogicalORExpressionNode): JSValue {
        val lref = interpretExpression(logicalORExpressionNode.lhs)
        val lval = Operations.getValue(lref)
        val lbool = Operations.toBoolean(lval)
        return if (lbool == JSTrue) {
            lval
        } else Operations.getValue(interpretExpression(logicalORExpressionNode.rhs))
    }

    private fun interpretLogicalANDExpression(logicalANDExpressionNode: LogicalANDExpressionNode): JSValue {
        val lref = interpretExpression(logicalANDExpressionNode.lhs)
        val lval = Operations.getValue(lref)
        val lbool = Operations.toBoolean(lval)
        return if (lbool == JSFalse) {
            lval
        } else Operations.getValue(interpretExpression(logicalANDExpressionNode.rhs))
    }

    private fun evaluateStringOrNumericBinaryExpression(lhs: ExpressionNode, rhs: ExpressionNode, op: String): JSValue {
        val lref = interpretExpression(lhs)
        val lval = Operations.getValue(lref)
        val rref = interpretExpression(rhs)
        val rval = Operations.getValue(rref)
        return Operations.applyStringOrNumericBinaryOperator(lval, rval, op)
    }

    private fun interpretBitwiseORExpression(bitwiseORExpressionNode: BitwiseORExpressionNode): JSValue {
        return evaluateStringOrNumericBinaryExpression(bitwiseORExpressionNode.lhs, bitwiseORExpressionNode.rhs, "|")
    }

    private fun interpretBitwiseXORExpression(bitwiseXORExpressionNode: BitwiseXORExpressionNode): JSValue {
        return evaluateStringOrNumericBinaryExpression(bitwiseXORExpressionNode.lhs, bitwiseXORExpressionNode.rhs, "^")
    }

    private fun interpretBitwiseANDExpression(bitwiseANDExpressionNode: BitwiseANDExpressionNode): JSValue {
        return evaluateStringOrNumericBinaryExpression(bitwiseANDExpressionNode.lhs, bitwiseANDExpressionNode.rhs, "&")
    }

    private fun interpretEqualityExpression(equalityExpressionNode: EqualityExpressionNode): JSValue {
        val lref = interpretExpression(equalityExpressionNode.lhs)
        val lval = Operations.getValue(lref)
        val rref = interpretExpression(equalityExpressionNode.rhs)
        val rval = Operations.getValue(rref)

        return when (equalityExpressionNode.op) {
            EqualityExpressionNode.Operator.StrictEquality -> Operations.strictEqualityComparison(rval, lval)
            EqualityExpressionNode.Operator.StrictInequality -> Operations.strictEqualityComparison(rval, lval).let { value ->
                if (value is JSTrue) JSFalse else JSTrue
            }
            EqualityExpressionNode.Operator.NonstrictEquality -> Operations.abstractEqualityComparison(rval, lval)
            EqualityExpressionNode.Operator.NonstrictInequality -> Operations.abstractEqualityComparison(rval, lval).let { value ->
                if (value is JSTrue) JSFalse else JSTrue
            }
        }
    }

    private fun interpretRelationalExpression(relationalExpressionNode: RelationalExpressionNode): JSValue {
        val lref = interpretExpression(relationalExpressionNode.lhs)
        val lval = Operations.getValue(lref)
        val rref = interpretExpression(relationalExpressionNode.rhs)
        val rval = Operations.getValue(rref)

        return when (relationalExpressionNode.op) {
            RelationalExpressionNode.Operator.LessThan -> {
                Operations.abstractRelationalComparison(lval, rval, true).let { value ->
                    if (value == JSUndefined) JSFalse else value
                }
            }
            RelationalExpressionNode.Operator.GreaterThan -> {
                Operations.abstractRelationalComparison(rval, lval, false).let { value ->
                    if (value == JSUndefined) JSFalse else value
                }
            }
            RelationalExpressionNode.Operator.LessThanEquals -> {
                Operations.abstractRelationalComparison(rval, lval, false).let { value ->
                    if (value == JSFalse) JSTrue else JSFalse
                }
            }
            RelationalExpressionNode.Operator.GreaterThanEquals -> {
                Operations.abstractRelationalComparison(lval, rval, true).let { value ->
                    if (value == JSFalse) JSTrue else JSFalse
                }
            }
            RelationalExpressionNode.Operator.Instanceof -> Operations.instanceofOperator(lval, rval).also {
            }
            RelationalExpressionNode.Operator.In -> {
                if (rval !is JSObject)
                    throwTypeError("right-hand side of 'in' operator must be an object")
                val key = Operations.toPropertyKey(lval)
                Operations.hasProperty(rval, key)
            }
        }
    }

    private fun interpretShiftExpression(shiftExpressionNode: ShiftExpressionNode): JSValue {
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

    private fun interpretAdditiveExpression(additiveExpressionNode: AdditiveExpressionNode): JSValue {
        return evaluateStringOrNumericBinaryExpression(
            additiveExpressionNode.lhs,
            additiveExpressionNode.rhs,
            if (additiveExpressionNode.isSubtraction) "-" else "+",
        )
    }

    private fun interpretMultiplicationExpression(multiplicativeExpressionNode: MultiplicativeExpressionNode): JSValue {
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

    private fun interpretExponentiationExpression(exponentiationExpressionNode: ExponentiationExpressionNode): JSValue {
        return evaluateStringOrNumericBinaryExpression(
            exponentiationExpressionNode.lhs,
            exponentiationExpressionNode.rhs,
            "**",
        )
    }

    private fun interpretUnaryExpression(unaryExpressionNode: UnaryExpressionNode): JSValue {
        val exprRef = interpretExpression(unaryExpressionNode.node)

        return when (unaryExpressionNode.op) {
            UnaryExpressionNode.Operator.Delete -> Operations.deleteOperator(exprRef)
            UnaryExpressionNode.Operator.Void -> {
                Operations.getValue(exprRef)
                JSUndefined
            }
            UnaryExpressionNode.Operator.Typeof -> {
                Operations.typeofOperator(Operations.getValue(exprRef))
            }
            UnaryExpressionNode.Operator.Plus -> {
                val exprValue = Operations.getValue(exprRef)
                Operations.toNumber(exprValue)
            }
            UnaryExpressionNode.Operator.Minus -> {
                val exprValue = Operations.getValue(exprRef)
                val oldValue = Operations.toNumeric(exprValue)
                if (oldValue is JSBigInt)
                    TODO()
                Operations.numericUnaryMinus(oldValue)
            }
            UnaryExpressionNode.Operator.BitwiseNot -> {
                val exprValue = Operations.getValue(exprRef)
                val oldValue = Operations.toNumeric(exprValue)
                if (oldValue is JSBigInt)
                    TODO()
                Operations.numericBitwiseNOT(oldValue)
            }
            UnaryExpressionNode.Operator.Not -> {
                val exprValue = Operations.getValue(exprRef)
                val oldValue = Operations.toBoolean(exprValue)
                if (oldValue == JSFalse) JSTrue else JSFalse
            }
        }
    }

    private fun interpretUpdateExpression(updateExpressionNode: UpdateExpressionNode): JSValue {
        val expr = interpretExpression(updateExpressionNode.target)
        val oldValue = Operations.getValue(expr)
        if (oldValue is JSBigInt)
            TODO()
        val newValue = if (updateExpressionNode.isIncrement) {
            Operations.numericAdd(oldValue, JSNumber(1.0))
        } else {
            Operations.numericSubtract(oldValue, JSNumber(1.0))
        }
        Operations.putValue(expr, newValue)
        return if (updateExpressionNode.isPostfix) {
            oldValue
        } else {
            newValue
        }
    }

    private fun updateEmpty(value: JSValue, block: (JSValue) -> JSValue): JSValue {
        try {
            val result = block(value)
            return if (result == JSEmpty) value else result
        } catch (e: ReturnException) {
            ecmaAssert(e.value != JSEmpty)
            throw e
        } catch (e: ThrowException) {
            ecmaAssert(e.value != JSEmpty)
            throw e
        }
    }
}
