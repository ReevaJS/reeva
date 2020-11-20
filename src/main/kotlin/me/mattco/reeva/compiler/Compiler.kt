package me.mattco.reeva.compiler

import codes.som.anthony.koffee.MethodAssembly
import codes.som.anthony.koffee.assembleClass
import codes.som.anthony.koffee.insns.jvm.*
import codes.som.anthony.koffee.insns.sugar.*
import codes.som.anthony.koffee.labels.LabelLike
import codes.som.anthony.koffee.modifiers.public
import codes.som.anthony.koffee.sugar.ClassAssemblyExtension.init
import codes.som.anthony.koffee.types.TypeLike
import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.expressions.TemplateLiteralNode
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.core.*
import me.mattco.reeva.core.environment.DeclarativeEnvRecord
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.FunctionEnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSReference
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.builtins.regexp.JSRegExpObject
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.functions.JSFunctionProto
import me.mattco.reeva.runtime.functions.JSInterpreterFunction
import me.mattco.reeva.runtime.iterators.JSListIterator
import me.mattco.reeva.runtime.iterators.JSObjectPropertyIterator
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.JSObjectProto
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.TryCatchBlockNode

open class Compiler {
    protected var stackHeight = 0
    protected val labelNodes = mutableListOf<LabelNode>()
    protected val dependencies = mutableListOf<NamedByteArray>()

    data class LabelNode(
        val stackHeight: Int,
        val labelName: String?,
        val breakLabel: LabelLike?,
        val continueLabel: LabelLike?
    )

    data class CompilationResult(
        val primary: NamedByteArray,
        val dependencies: List<NamedByteArray>,
    )

    data class NamedByteArray(val name: String, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other)
                return true
            return other is NamedByteArray && name == other.name && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    fun compileScript(script: ScriptNode): CompilationResult {
        dependencies.clear()

        val className = "TopLevelScript_${Reeva.nextId()}"
        val classNode = assembleClass(public, className, superName = "me/mattco/reeva/compiler/TopLevelScript") {
            init(public, superClass = "me/mattco/reeva/compiler/TopLevelScript") {
                _return
            }

            method(public, "run", JSValue::class) {
                currentLocalIndex++
                globalDeclarationInstantiation(script)
                compileStatementList(script.statementList)
                loadUndefined()
                areturn
            }
        }

        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        classNode.accept(writer)
        return CompilationResult(
            NamedByteArray(className, writer.toByteArray()),
            dependencies
        )
    }

    fun compileModule(module: ModuleNode): CompilationResult {
        dependencies.clear()

        val className = "TopLevelModule_${Reeva.nextId()}"
        val classNode = assembleClass(public, className, superName = "me/mattco/reeva/compiler/TopLevelScript") {
            init(public, superClass = "me/mattco/reeva/compiler/TopLevelScript") {
                _return
            }

            method(public, "run", JSValue::class) {
                currentLocalIndex++
                TODO()
            }
        }

        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        classNode.accept(writer)
        return CompilationResult(
            NamedByteArray(className, writer.toByteArray()),
            dependencies
        )
    }

    protected fun MethodAssembly.globalDeclarationInstantiation(node: ScriptNode) {
        loadRealm()
        invokevirtual(Realm::class, "getGlobalEnv", GlobalEnvRecord::class)

        val lexNames = node.lexicallyDeclaredNames()
        val varNames = node.varDeclaredNames()
        val varDeclarations = node.varScopedDeclarations()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()
        val declaredFunctionNames = mutableListOf<String>()
        val declaredVarNames = mutableListOf<String>()

        // TODO: Won't this eventually be handled by static semantics or early errors?
        dup
        ldc(varNames.size)
        anewarray<String>()
        varNames.forEachIndexed { index, name ->
            dup
            ldc(index)
            ldc(name)
            aastore
        }
        ldc(lexNames.size)
        anewarray<String>()
        lexNames.forEachIndexed { index, name ->
            dup
            ldc(index)
            ldc(name)
            aastore
        }

        varDeclarations.asReversed().forEach {
            if (it !is VariableDeclarationNode && it !is ForBindingNode && it !is BindingIdentifierNode) {
                val functionName = it.boundNames()[0]
                if (functionName !in declaredFunctionNames) {
                    declaredFunctionNames.add(functionName)
                    functionsToInitialize.add(0, it as FunctionDeclarationNode)
                }
            }
        }
        ldc(declaredFunctionNames.size)
        anewarray<String>()
        declaredFunctionNames.forEachIndexed { index, name ->
            dup
            ldc(index)
            ldc(name)
            aastore
        }

        varDeclarations.forEach {
            if (it !is VariableDeclarationNode && it !is ForBindingNode && it !is BindingIdentifierNode)
                return@forEach
            it.boundNames().forEach { name ->
                if (name !in declaredFunctionNames) {
                    if (name !in declaredVarNames)
                        declaredVarNames.add(name)
                }
            }
        }
        ldc(declaredVarNames.size)
        anewarray<String>()
        declaredVarNames.forEachIndexed { index, name ->
            dup
            ldc(index)
            ldc(name)
            aastore
        }

        runtime(
            "verifyValidGlobalDeclarations",
            void,
            GlobalEnvRecord::class,
            Array<String>::class,
            Array<String>::class,
            Array<String>::class,
            Array<String>::class,
        )

        val lexDeclarations = node.lexicallyScopedDeclarations()
        lexDeclarations.forEach { decl ->
            decl.boundNames().forEach { name ->
                dup
                ldc(name)
                val isConstant = decl.isConstantDeclaration()
                ldc(isConstant)
                invokevirtual(
                    EnvRecord::class,
                    if (isConstant) "createImmutableBinding" else "createMutableBinding",
                    void,
                    String::class,
                    Boolean::class
                )
            }
        }
        functionsToInitialize.forEach { func ->
            val functionName = func.boundNames()[0]
            dup
            dup
            instantiateFunctionObject(func)
            ldc(functionName)
            swap
            ldc(false)
            invokevirtual(GlobalEnvRecord::class, "createGlobalFunctionBinding", void, String::class, JSFunction::class, Boolean::class)
        }
        declaredVarNames.forEach {
            dup
            ldc(it)
            ldc(false)
            invokevirtual(GlobalEnvRecord::class, "createGlobalVarBinding", void, String::class, Boolean::class)
        }

        pop
    }

    // Consumes an EnvRecord from the stack, pushes a JSFunction
    protected fun MethodAssembly.instantiateFunctionObject(functionNode: FunctionDeclarationNode) {
        loadRealm()
        invokevirtual(Realm::class, "getFunctionProto", JSFunctionProto::class)

        ordinaryFunctionCreate(
            "TODO",
            functionNode.parameters,
            functionNode.body,
            JSFunction.ThisMode.NonLexical,
            functionNode.identifier?.identifierName,
            isConstructor = true,
        )

        stackHeight++
    }

    // Consumes an EnvRecord and a JSObject (the prototype) from the stack, pushes a JSFunction
    protected fun MethodAssembly.ordinaryFunctionCreate(
        sourceText: String,
        parameters: FormalParametersNode,
        body: FunctionStatementList,
        thisMode: JSFunction.ThisMode,
        functionName: String?,
        isConstructor: Boolean,
    ) {
        val className = "Function_${functionName ?: "_Anonymous"}_${Reeva.nextId()}"
        val isStrict = body.statementList?.hasUseStrictDirective() == true
        val funcThisMode = when {
            thisMode == JSFunction.ThisMode.Lexical -> JSFunction.ThisMode.Lexical
            isStrict -> JSFunction.ThisMode.Strict
            else -> JSFunction.ThisMode.Global
        }

        val indexOfLastNormal = parameters.functionParameters.parameters.indexOfLast { param ->
            (param.bindingElement as? SingleNameBindingElement)?.initializer == null
        }

        val prevLocalIndex = currentLocalIndex
        val prevStackHeight = stackHeight

        val functionClassNode = assembleClass(
            public,
            className,
            superName = "me/mattco/reeva/runtime/functions/JSCompilerFunction"
        ) {
            method(public, "<init>", void, EnvRecord::class, JSObject::class) {
                currentLocalIndex = 3
                aload_0
                loadRealm()
                loadEnumMember<JSFunction.ThisMode>(funcThisMode.name)
                aload_1
                ldc(isStrict)
                loadUndefined()
                ldc(sourceText)
                aload_2

                invokespecial(
                    JSInterpreterFunction::class,
                    "<init>",
                    void,
                    Realm::class,
                    JSFunction.ThisMode::class,
                    EnvRecord::class,
                    Boolean::class,
                    JSValue::class,
                    String::class,
                    JSObject::class,
                )

                _return
            }

            method(public, "init", void) {
                aload_0
                construct(PropertyKey::class, String::class) {
                    ldc("length")
                }
                construct(Descriptor::class, JSValue::class, Int::class) {
                    construct(JSNumber::class, Int::class) {
                        ldc(indexOfLastNormal + 1)
                    }
                    ldc(Descriptor.CONFIGURABLE)
                }
                operation("definePropertyOrThrow", Boolean::class, JSValue::class, PropertyKey::class, Descriptor::class)
                pop
                if (functionName != null) {
                    aload_0
                    construct(PropertyKey::class, String::class) {
                        ldc(functionName)
                    }
                    operation("setFunctionName", Boolean::class, JSFunction::class, PropertyKey::class)
                    pop
                }

                if (isConstructor) {
                    aload_0
                    operation("makeConstructor", void, JSFunction::class)
                }

                _return
            }

            method(public, "evaluate", JSValue::class, List::class) {
                currentLocalIndex = 2
                aload_0
                aload_1
                stackHeight = 2
                functionDeclarationInstantiation(parameters, body, funcThisMode, isStrict)
                body.statementList?.also {
                    compileStatementList(it)
                }
                loadUndefined()
                expect(stackHeight == 0, "expected empty stack after compiling function dependency")
                areturn
            }
        }

        currentLocalIndex = prevLocalIndex
        stackHeight = prevStackHeight

        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        functionClassNode.accept(writer)
        dependencies.add(NamedByteArray(className, writer.toByteArray()))

        new(className)
        dup_x2
        dup_x2
        pop
        invokespecial(className, "<init>", void, EnvRecord::class, JSObject::class)

        dup
        invokevirtual(JSObject::class, "init", void)

        stackHeight--
    }

    // Consumes JSFunction and List<JSValue> from the stack
    protected fun MethodAssembly.functionDeclarationInstantiation(
        parameters: FormalParametersNode,
        body: FunctionStatementList,
        thisMode: JSFunction.ThisMode,
        isStrict: Boolean,
    ) {
        val arguments = astore()

        // We will need the function to create a mapped arguments object, but
        // we currently do not do that
//        val func = astore()
        pop

        val parameterNames = parameters.boundNames()
        val hasDuplicates = parameterNames.distinct().size != parameterNames.size
        val simpleParameterList = parameters.isSimpleParameterList()
        val hasParameterExpressions = parameters.containsExpression()
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
            thisMode == JSFunction.ThisMode.Lexical -> false
            "arguments" in parameterNames -> false
            !hasParameterExpressions && ("arguments" in functionNames || "arguments" in lexicalNames) -> false
            else -> true
        }

        loadLexicalEnv()

        if (!isStrict && hasParameterExpressions) {
            createDeclarativeEnvRecord()
            dup
            storeLexicalEnv()
        }

        parameterNames.forEach { name ->
            dup
            ldc(name)
            invokevirtual(EnvRecord::class, "hasBinding", Boolean::class, String::class)
            ifStatement(JumpCondition.False) {
                dup
                ldc(name)
                ldc(false)
                invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                if (hasDuplicates) {
                    dup
                    ldc(name)
                    loadUndefined()
                    invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
                }
            }
        }

        val parameterBindings = if (argumentsObjectNeeded) {
            dup
            ldc("arguments")
            ldc(false)
            if (isStrict) {
                invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
            } else {
                invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
            }

            dup
            ldc("arguments")
            // TODO: Figure out how to create a mapped arguments object
//            if (isStrict || !simpleParameterList) {
                load(arguments)
                operation("createUnmappedArgumentsObject", JSValue::class, List::class)
//            } else {
//
//            }
            invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
            parameterNames + listOf("arguments")
        } else parameterNames

        construct(Operations.IteratorRecord::class, JSObject::class, JSValue::class, Boolean::class) {
            loadRealm()
            load(arguments)
            invokestatic(JSListIterator::class, "create", JSListIterator::class, Realm::class, List::class)
            dup
            ldc("next")
            invokevirtual(JSObject::class, "get", JSValue::class, String::class)
            ldc(false)
        }
        loadLexicalEnv()
        iteratorBindingInitialization(parameters)

        val varEnv: Local

        if (hasParameterExpressions) {
            dup
            val env = astore()
            createDeclarativeEnvRecord()
            dup
            varEnv = astore()
            storeVariableEnv()

            val instantiatedVarNames = mutableListOf<String>()
            varNames.forEach { name ->
                if (name in instantiatedVarNames)
                    return@forEach

                instantiatedVarNames.add(name)
                load(varEnv)
                ldc(name)
                ldc(false)
                invokevirtual(EnvRecord::class, "createMutableBinding", String::class, Boolean::class)

                load(varEnv)
                ldc(name)
                if (name !in parameterBindings || name in functionNames) {
                    loadUndefined()
                } else {
                    load(env)
                    ldc(name)
                    ldc(false)
                    invokevirtual(EnvRecord::class, "getBindingValue", JSValue::class, String::class, Boolean::class)
                }
                invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
            }
        } else {
            varEnv = astore()
            val instantiatedVarNames = parameterBindings.toMutableList()
            varNames.forEach { name ->
                if (name !in instantiatedVarNames) {
                    instantiatedVarNames.add(name)
                    load(varEnv)
                    dup
                    ldc(name)
                    ldc(false)
                    invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                    ldc(name)
                    loadUndefined()
                    invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
                }
            }
        }

        val lexEnv = if (isStrict) {
            varEnv
        } else {
            load(varEnv)
            createDeclarativeEnvRecord()
            astore()
        }

        load(lexEnv)
        storeLexicalEnv()

        body.lexicallyScopedDeclarations().forEach { decl ->
            decl.boundNames().forEach { name ->
                load(lexEnv)
                ldc(name)
                if (decl.isConstantDeclaration()) {
                    ldc(true)
                    invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
                } else {
                    ldc(false)
                    invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                }
            }
        }

        functionsToInitialize.forEach { decl ->
            load(varEnv)
            ldc(decl.boundNames()[0])
            load(lexEnv)
            instantiateFunctionObject(decl)
            ldc(false)
            invokevirtual(EnvRecord::class, "setMutableBinding", void, String::class, JSValue::class, Boolean::class)
        }

        stackHeight -= 2
    }

    // Consumed an IteratorRecord and EnvRecord? from the stack
    private fun MethodAssembly.iteratorBindingInitialization(node: ASTNode) {
        when (node) {
            is FormalParametersNode -> {
                node.functionParameters.parameters.forEach {
                    dup2
                    iteratorBindingInitialization(it.bindingElement)
                }
                if (node.restParameter != null) {
                    iteratorBindingInitialization(node.restParameter.element)
                } else {
                    pop2
                }
            }
            is ArrayBindingPattern -> {
                node.bindingElements.forEach {
                    dup2
                    if (it is BindingElisionNode) {
                        pop
                        iteratorDestructuringAssignmentEvaluation(it)
                    } else {
                        iteratorBindingInitialization(it)
                    }
                }

                if (node.restProperty != null) {
                    iteratorBindingInitialization(node.restProperty)
                } else {
                    pop2
                }
            }
            is SingleNameBindingElement -> {
                swap
                ldc(node.identifier.identifierName)
                operation("resolveBinding", JSReference::class, String::class, EnvRecord::class)
                // env, record, lhs
                loadUndefined()
                val value = astore()
                // env, record, lhs

                swap
                dup
                val record = astore()
                invokevirtual(Operations.IteratorRecord::class, "isDone", Boolean::class)
                // env, lhs, boolean

                ifStatement(JumpCondition.False) {
                    tryCatchBuilder {
                        tryBlock {
                            // lhs
                            load(record)
                            operation("iteratorStep", JSValue::class, Operations.IteratorRecord::class)
                            // lhs, next
                            dup
                            loadFalse()
                            ifElseStatement(JumpCondition.RefEqual) {
                                ifBlock {
                                    pop
                                    load(record)
                                    ldc(true)
                                    invokevirtual(Operations.IteratorRecord::class, "setDone", void, Boolean::class)
                                }

                                elseBlock {
                                    operation("iteratorValue", JSValue::class, JSValue::class)
                                    astore(value.index)
                                }
                            }
                        }

                        catchBlock<ThrowException> {
                            load(record)
                            ldc(true)
                            invokevirtual(Operations.IteratorRecord::class, "setDone", void, Boolean::class)
                            athrow
                        }
                    }
                }

                // env, lhs
                if (node.initializer != null) {
                    load(value)
                    loadUndefined()
                    ifStatement(JumpCondition.RefEqual) {
                        if (Operations.isAnonymousFunctionDefinition(node.initializer))
                            TODO()
                        compileExpression(node.initializer.node)
                        getValue
                        astore(value.index)
                    }
                }

                swap
                load(value)
                ifElseStatement(JumpCondition.Null) {
                    ifBlock {
                        operation("putValue", void, JSValue::class, JSValue::class)
                    }

                    elseBlock {
                        operation("initializeReferencedBinding", void, JSReference::class, JSValue::class)
                    }
                }
            }
            is PatternBindingElement -> {
                swap
                loadUndefined()
                val value = astore()
                val record = astore()
                // env

                load(record)
                invokevirtual(Operations.IteratorRecord::class, "isDone", Boolean::class)
                ifStatement(JumpCondition.False) {
                    tryCatchBuilder {
                        tryBlock {
                            load(record)
                            operation("iteratorStep", JSValue::class, Operations.IteratorRecord::class)
                            dup
                            loadFalse()
                            ifElseStatement(JumpCondition.RefEqual) {
                                ifBlock {
                                    pop
                                    load(record)
                                    ldc(true)
                                    invokevirtual(Operations.IteratorRecord::class, "setDone", void, Boolean::class)
                                }

                                elseBlock {
                                    operation("iteratorValue", JSValue::class, JSValue::class)
                                    astore(value.index)
                                }
                            }
                        }

                        catchBlock<ThrowException> {
                            load(record)
                            ldc(true)
                            invokevirtual(Operations.IteratorRecord::class, "setDone", void, Boolean::class)
                            athrow
                        }
                    }
                }

                if (node.initializer != null) {
                    load(value)
                    loadUndefined()
                    ifStatement(JumpCondition.RefEqual) {
                        compileExpression(node.initializer.node)
                        getValue
                        astore(value.index)
                    }
                }

                load(value)
                swap
                bindingInitialization(node.pattern)
            }
            is BindingRestElement -> {
                val env = astore()
                val record = astore()
                ldc(0)
                operation("arrayCreate", JSObject::class, Int::class)
                val arr = astore()
                ldc(0)
                // n

                val start = makeLabel()
                val end = makeLabel()
                placeLabel(start)

                if (node.target is BindingIdentifierNode) {
                    ldc(node.target.identifierName)
                    load(env)
                    operation("resolveBinding", JSReference::class, String::class, EnvRecord::class)
                    load(record)
                    invokevirtual(EnvRecord::class, "isDone", Boolean::class)
                    ifStatement(JumpCondition.True) {
                        load(arr)
                        load(env)
                        ifElseStatement(JumpCondition.Null) {
                            ifBlock {
                                operation("putValue", void, JSValue::class, JSValue::class)
                            }

                            elseBlock {
                                operation("initializeReferencedBinding", void, JSReference::class, JSValue::class)
                            }
                        }
                    }
                } else {
                    expect(node.target is BindingPattern)
                    load(record)
                    invokevirtual(EnvRecord::class, "isDone", Boolean::class)
                    ifStatement(JumpCondition.True) {
                        load(arr)
                        load(env)
                        bindingInitialization(node.target)
                        goto(end)
                    }
                }

                placeLabel(end)
                pop
            }
            else -> unreachable()
        }
    }

    // Consumes a JSValue and EnvRecord? from the stack
    protected fun MethodAssembly.bindingInitialization(node: ASTNode) {
        when (node) {
            is BindingIdentifierNode -> {
                dup
                ifElseStatement(JumpCondition.Null) {
                    ifBlock {
                        pop
                        ldc(node.identifierName)
                        operation("resolveBinding", JSReference::class, String::class)
                        swap
                        operation("putValue", void, JSValue::class, JSValue::class)
                    }

                    elseBlock {
                        swap
                        ldc(node.identifierName)
                        swap
                        operation("initializeBinding", void, String::class, JSValue::class)
                    }
                }
            }
            is ArrayBindingPattern -> {
                TODO()
            }
            is ObjectBindingPattern -> {
                TODO()
            }
            else -> TODO()
        }
    }

    // Consumes an IteratorRecord from the stack
    private fun MethodAssembly.iteratorDestructuringAssignmentEvaluation(node: ASTNode) {
        val record = astore()

        when (node) {
            is BindingElisionNode -> {
                load(record)
                invokevirtual(EnvRecord::class, "isDone", Boolean::class)
                ifStatement(JumpCondition.False) {
                    tryCatchBuilder {
                        tryBlock {
                            load(record)
                            operation("iteratorStep", JSValue::class, Operations.IteratorRecord::class)
                            loadFalse()
                            ifStatement(JumpCondition.RefEqual) {
                                load(record)
                                ldc(true)
                                invokevirtual(Operations.IteratorRecord::class, "setDone", void, Boolean::class)
                            }
                        }

                        catchBlock<ThrowException> {
                            load(record)
                            ldc(true)
                            invokevirtual(Operations.IteratorRecord::class, "setDone", void, Boolean::class)
                            athrow
                        }
                    }
                }
            }
            else -> TODO()
        }
    }

    protected fun MethodAssembly.compileStatementList(statementListNode: StatementListNode) {
        // TODO: store last value
        statementListNode.statements.forEach {
            compileStatement(it as StatementNode)
        }
    }

    protected fun MethodAssembly.compileStatement(statement: StatementNode) {
        when (statement) {
            is BlockStatementNode -> compileBlockStatement(statement)
            is VariableStatementNode -> compileVariableStatement(statement)
            is EmptyStatementNode -> {}
            is ExpressionStatementNode -> compileExpressionStatement(statement)
            is IfStatementNode -> compileIfStatement(statement)
//            is BreakableStatement -> compileBreakableStatement(statement)
            is IterationStatement -> compileIterationStatement(statement, null)
            is LabelledStatementNode -> compileLabelledStatement(statement)
            is LexicalDeclarationNode -> compileLexicalDeclaration(statement)
            is FunctionDeclarationNode -> compileFunctionDeclaration(statement)
            is ClassDeclarationNode -> compileClassDeclaration(statement)
            is ReturnStatementNode -> compileReturnStatement(statement)
            is ThrowStatementNode -> compileThrowStatement(statement)
            is TryStatementNode -> compileTryStatement(statement)
            is BreakStatementNode -> compileBreakStatement(statement)
            is ImportDeclarationNode -> compileImportDeclaration(statement)
            is ExportDeclarationNode -> compileExportDeclaration(statement)
            else -> TODO()
        }
    }

    protected fun MethodAssembly.compileBlockStatement(node: BlockStatementNode) {
        compileBlock(node.block)
    }

    protected fun MethodAssembly.compileBlock(node: BlockNode) {
        if (node.statements == null)
            return
        loadLexicalEnv()
        dup
        createDeclarativeEnvRecord()
        // oldEnv, blockEnv

        // BlockDeclarationInstantiation start
        node.statements.lexicallyScopedDeclarations().forEach { decl ->
            decl.boundNames().forEach { name ->
                dup
                ldc(name)
                // oldEnv, blockEnv, blockEnv, name
                val isConstant = decl.isConstantDeclaration()
                ldc(isConstant)
                // oldEnv, blockEnv, blockEnv, name, boolean
                invokevirtual(
                    EnvRecord::class,
                    if (isConstant) "createImmutableBinding" else "createMutableBinding",
                    void,
                    String::class,
                    Boolean::class
                )
                // oldEnv, blockEnv
            }
            if (decl is FunctionDeclarationNode) {
                dup
                dup
                instantiateFunctionObject(decl)
                ldc(decl.boundNames()[0])
                swap
                invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
                stackHeight--
            }
        }
        // BlockDeclarationInstantiation end

        // oldEnv, blockEnv
        loadContext()
        // oldEnv, blockEnv, context
        swap
        // oldEnv, context, blockEnv
        putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
        // oldEnv
        val oldEnv = astore()

        tryCatchBuilder {
            tryBlock {
                compileStatementList(node.statements)
            }

            finallyBlock {
                loadContext()
                load(oldEnv)
                putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
            }
        }
    }

    protected fun MethodAssembly.compileVariableStatement(node: VariableStatementNode) {
        node.declarations.declarations.forEach { decl ->
            if (decl.initializer == null)
                return@forEach
            ldc(decl.identifier.identifierName)
            operation("resolveBinding", JSReference::class, String::class)
            stackHeight++
            compileExpression(decl.initializer.node)
            getValue
            // lhs, rhs
            if (Operations.isAnonymousFunctionDefinition(decl.initializer.node)) {
                dup
                checkcast<JSFunction>()
                construct(PropertyKey::class, String::class) {
                    ldc(decl.identifier.identifierName)
                }
                operation("setFunctionName", Boolean::class, JSFunction::class, PropertyKey::class)
                pop
            }
            operation("putValue", void, JSValue::class, JSValue::class)
            stackHeight -= 2
        }
    }

    protected fun MethodAssembly.compileExpressionStatement(node: ExpressionStatementNode) {
        compileExpression(node.node)
        getValue
        pop
        stackHeight--
    }

    protected fun MethodAssembly.compileIfStatement(node: IfStatementNode) {
        compileExpression(node.condition)
        getValue
        toBoolean
        stackHeight--

        if (node.falseBlock != null) {
            ifElseStatement(JumpCondition.True) {
                ifBlock {
                    compileStatement(node.trueBlock)
                }

                elseBlock {
                    compileStatement(node.falseBlock)
                }
            }
        } else {
            ifStatement(JumpCondition.True) {
                compileStatement(node.trueBlock)
            }
        }
    }

//    fun MethodAssembly.compileBreakableStatement(node: BreakableStatement) {
//        labelledEvaluation(node, emptySet())
//    }

    protected fun MethodAssembly.compileIterationStatement(node: IterationStatement, label: String?) {
        when (node) {
            is DoWhileStatementNode -> compileDoWhileStatement(node, label)
            is WhileStatementNode -> compileWhileStatement(node, label)
            is ForStatementNode -> compileForStatement(node, label)
            is ForInNode -> compileForInStatement(node, label)
            is ForOfNode -> compileForOfStatement(node, label)
            else -> unreachable()
        }
    }

    protected fun MethodAssembly.compileForInStatement(node: ForInNode, label: String?) {
        forInOfHeadEvaluation(
            if (node.decl is ForDeclarationNode) {
                node.decl.boundNames()
            } else emptyList(),
            node.expression,
            Interpreter.IterationKind.Enumerate,
        )

        dup
        ifElseStatement(JumpCondition.NonNull) {
            ifBlock {
                forInOfBodyEvaluation(
                    node.decl,
                    node.body,
                    Interpreter.IterationKind.Enumerate,
                    when (node.decl) {
                        is VariableDeclarationNode -> Interpreter.LHSKind.VarBinding
                        is ForDeclarationNode -> Interpreter.LHSKind.LexicalBinding
                        else -> Interpreter.LHSKind.Assignment
                    },
                    label = label,
                )
            }

            elseBlock {
                pop
            }
        }

        stackHeight--
    }

    protected fun MethodAssembly.compileForOfStatement(node: ForOfNode, label: String?) {
        forInOfHeadEvaluation(
            if (node.decl is ForDeclarationNode) {
                node.decl.boundNames()
            } else emptyList(),
            node.expression,
            Interpreter.IterationKind.Iterate
        )

        forInOfBodyEvaluation(
            node.decl,
            node.body,
            Interpreter.IterationKind.Iterate,
            when (node.decl) {
                is VariableDeclarationNode -> Interpreter.LHSKind.VarBinding
                is ForDeclarationNode -> Interpreter.LHSKind.LexicalBinding
                else -> Interpreter.LHSKind.Assignment
            },
            label = label,
        )

        stackHeight--
    }

    protected fun MethodAssembly.forInOfHeadEvaluation(
        uninitializedBoundNames: List<String>,
        expr: ExpressionNode,
        iterationKind: Interpreter.IterationKind,
    ) {
        if (iterationKind == Interpreter.IterationKind.AsyncIterate)
            TODO()

        if (uninitializedBoundNames.isNotEmpty()) {
            loadLexicalEnv()
            dup
            // oldEnv, oldEnv
            ecmaAssert(uninitializedBoundNames.distinct().size == uninitializedBoundNames.size)
            createDeclarativeEnvRecord()
            // oldEnv, newEnv
            uninitializedBoundNames.forEach { name ->
                dup
                // oldEnv, newEnv, newEnv
                ldc(name)
                ldc(false)
                invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                // oldEnv, newEnv
            }
            // oldEnv, newEnv
            storeLexicalEnv()
            // oldEnv
        }

        // oldEnv (if uninitializeBoundNames.isNotEmpty())
        // <empty> (if uninitializeBoundNames.isEmpty())
        compileExpression(expr)
        if (uninitializedBoundNames.isNotEmpty()) {
            // oldEnv, exprRef
            swap
            // exprRef, oldEnv
            storeLexicalEnv()
            // exprRef
        }

        // exprRef
        getValue

        // expr
        if (iterationKind == Interpreter.IterationKind.Enumerate) {
            dup
            // expr, expr
            invokevirtual(JSValue::class, "isNullish", Boolean::class)
            // expr, boolean
            ifElseStatement(JumpCondition.True) {
                ifBlock {
                    // expr
                    pop
                    aconst_null
                }

                elseBlock {
                    // expr
                    new<Operations.IteratorRecord>()
                    dup_x1
                    // record, expr, record
                    swap
                    // record, record, expr
                    operation("toObject", JSObject::class, JSValue::class)
                    // record, record, exprObj
                    loadRealm()
                    // record, record, exprObj, realm
                    swap
                    // record, record, realm, exprObj
                    invokestatic(
                        JSObjectPropertyIterator::class,
                        "create",
                        JSObjectPropertyIterator::class,
                        Realm::class,
                        JSObject::class
                    )
                    // record, record, iter
                    dup
                    // record, record, iter, iter
                    construct(JSString::class, String::class) {
                        ldc("next")
                    }
                    // record, record, iter, iter, "next"
                    operation("getV", JSValue::class, JSValue::class, JSValue::class)
                    // record, record, iter, value
                    ldc(false)
                    // record, record, iter, value, boolean
                    invokespecial(Operations.IteratorRecord::class, "<init>", void, JSObject::class, JSValue::class, Boolean::class)
                    // record
                }
            }
        } else {
            // expr
            loadEnumMember<Operations.IteratorHint>("Sync")
            // expr, Sync
            operation("getIterator", Operations.IteratorRecord::class, JSValue::class, Operations.IteratorHint::class)
            // record
        }
    }

    // Consumes an IteratorRecord from the stack
    protected fun MethodAssembly.forInOfBodyEvaluation(
        lhs: ASTNode,
        statement: StatementNode,
        iterationKind: Interpreter.IterationKind,
        lhsKind: Interpreter.LHSKind,
        label: String?,
        iteratorKind: Operations.IteratorHint? = Operations.IteratorHint.Sync
    ) {
        if (iterationKind == Interpreter.IterationKind.AsyncIterate)
            TODO()
        if (iteratorKind == Operations.IteratorHint.Async)
            TODO()
        if (lhs.isDestructuring())
            TODO()

        // record
        val record = astore()
        loadLexicalEnv()
        val oldEnv = astore()

        val start = makeLabel()
        val end = makeLabel()
        labelNodes.add(LabelNode(stackHeight, label, end, start))

        placeLabel(start)

        load(record)
        invokevirtual(Operations.IteratorRecord::class, "getNextMethod", JSValue::class)
        load(record)
        invokevirtual(Operations.IteratorRecord::class, "getIterator", JSObject::class)
        operation("call", JSValue::class, JSValue::class, JSValue::class)
        // nextResult

        dup
        // nextResult, nextResult
        instanceof<JSObject>()
        // nextResult, boolean
        ifStatement(JumpCondition.False) {
            // nextResult
            construct(Errors.TODO::class, String::class) {
                ldc("forInOfBodyEvaluation")
            }
            invokevirtual(Errors.TODO::class, "throwTypeError", Nothing::class)
            pop
            loadUndefined()
            areturn
        }

        // nextResult
        dup
        // nextResult, nextResult
        operation("iteratorComplete", Boolean::class, JSValue::class)
        // nextResult, boolean
        ifStatement(JumpCondition.True) {
            // nextResult
            pop
            goto(end)
        }

        // nextResult
        operation("iteratorValue", JSValue::class, JSValue::class)
        // nextValue
        val nextValue = astore()

        tryCatchBuilder {
            tryBlock {
                load(nextValue)
                // nextValue
                stackHeight++
                if (lhsKind != Interpreter.LHSKind.LexicalBinding) {
                    compileExpression(lhs as ExpressionNode)
                    // nextValue, lhsRef
                } else {
                    ecmaAssert(lhs is ForDeclarationNode)
                    // nextValue
                    load(oldEnv)
                    // nextValue, oldEnv
                    createDeclarativeEnvRecord()
                    // nextValue, iterationEnv
                    bindingInstantiation(lhs)
                    // nextValue, iterationEnv
                    storeLexicalEnv()
                    val boundNames = lhs.boundNames()
                    expect(boundNames.size == 1)
                    ldc(boundNames[0])
                    // nextValue, name
                    operation("resolveBinding", JSReference::class, String::class)
                    // nextValue, lhsRef
                }

                if (lhsKind == Interpreter.LHSKind.LexicalBinding) {
                    checkcast<JSReference>()
                    swap
                    // lhsRef, nextValue
                    operation("initializeReferencedBinding", void, JSReference::class, JSValue::class)
                } else {
                    // lhsRef, nextValue
                    swap
                    operation("putValue", void, JSValue::class, JSValue::class)
                }
                stackHeight -= 2
            }

            catchBlock<Throwable> {
                load(oldEnv)
                storeLexicalEnv()
                if (iterationKind != Interpreter.IterationKind.Enumerate) {
                    load(record)
                    loadUndefined()
                    operation("iteratorClose", JSValue::class, Operations.IteratorRecord::class, JSValue::class)
                    pop
                }
                athrow
            }
        }

        tryCatchBuilder {
            tryBlock {
                compileStatement(statement)
            }

            catchBlock<ThrowException> {
                if (iterationKind != Interpreter.IterationKind.Enumerate) {
                    load(record)
                    loadUndefined()
                    operation("iteratorClose", JSValue::class, Operations.IteratorRecord::class, JSValue::class)
                    pop
                }
                athrow
            }

            finallyBlock {
                load(oldEnv)
                storeLexicalEnv()
            }
        }

        goto(start)

        placeLabel(end)
    }

    protected fun MethodAssembly.bindingInstantiation(node: ForDeclarationNode) {
        node.binding.boundNames().forEach { name ->
            dup
            ldc(name)
            ldc(node.isConst)
            val opName = if (node.isConst) "createImmutableBinding" else "createMutableBinding"
            invokevirtual(EnvRecord::class, opName, void, String::class, Boolean::class)
        }
    }

    protected fun MethodAssembly.compileDoWhileStatement(node: DoWhileStatementNode, label: String?) {
        val start = makeLabel()
        val end = makeLabel()
        labelNodes.add(LabelNode(stackHeight, label, end, start))

        placeLabel(start)
        compileStatement(node.body)
        compileExpression(node.condition)
        getValue
        toBoolean
        stackHeight--
        ifStatement(JumpCondition.True) {
            goto(start)
        }

        placeLabel(end)
    }

    protected fun MethodAssembly.compileWhileStatement(node: WhileStatementNode, label: String?) {
        val start = makeLabel()
        val end = makeLabel()
        labelNodes.add(LabelNode(stackHeight, label, end, start))

        placeLabel(start)
        compileExpression(node.condition)
        stackHeight--
        ifStatement(JumpCondition.True) {
            compileStatement(node.body)
            goto(start)
        }

        placeLabel(end)
    }

    protected fun MethodAssembly.compileForStatement(node: ForStatementNode, label: String?) {
        when (node.initializer) {
            is ExpressionNode -> {
                compileExpression(node.initializer)
                getValue
                pop
                stackHeight--

                forBodyEvaluation(
                    node.condition,
                    node.incrementer,
                    node.body,
                    emptyList(),
                    label,
                )
            }
            is VariableStatementNode -> {
                compileVariableStatement(node.initializer)

                forBodyEvaluation(
                    node.condition,
                    node.incrementer,
                    node.body,
                    emptyList(),
                    label,
                )
            }
            is LexicalDeclarationNode -> {
                val isConst = node.initializer.isConstantDeclaration()
                val boundNames = node.initializer.boundNames()

                loadLexicalEnv()
                dup
                // oldEnv, oldEnv
                createDeclarativeEnvRecord()
                // oldEnv, loopEnv

                boundNames.forEach {
                    dup
                    ldc(it)
                    ldc(isConst)
                    // oldEnv, loopEnv, loopEnv, string, boolean
                    invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
                    // oldEnv, loopEnv
                }

                storeLexicalEnv()
                val oldEnv = astore()

                tryCatchBuilder {
                    tryBlock {
                        compileLexicalDeclaration(node.initializer)
                        forBodyEvaluation(
                            node.condition,
                            node.incrementer,
                            node.body,
                            if (isConst) emptyList() else boundNames,
                            label,
                        )
                    }

                    finallyBlock {
                        load(oldEnv)
                        storeLexicalEnv()
                    }
                }
            }
        }
    }

    protected fun MethodAssembly.forBodyEvaluation(
        condition: ExpressionNode?,
        incrementer: ExpressionNode?,
        body: StatementNode,
        perIterationBindings: List<String>,
        label: String?,
    ) {
        val start = makeLabel()
        val end = makeLabel()
        labelNodes.add(LabelNode(stackHeight, label, end, start))

        createPerIterationEnvironment(perIterationBindings)
        placeLabel(start)

        if (condition != null) {
            compileExpression(condition)
            getValue
            toBoolean
            stackHeight--
            ifStatement(JumpCondition.False) {
                goto(end)
            }
        }

        compileStatement(body)
        createPerIterationEnvironment(perIterationBindings)

        if (incrementer != null) {
            compileExpression(incrementer)
            getValue
            pop
            stackHeight--
        }

        goto(start)

        placeLabel(end)
    }

    protected fun MethodAssembly.createPerIterationEnvironment(perIterationBindings: List<String>) {
        if (perIterationBindings.isEmpty())
            return

        loadLexicalEnv()
        dup
        // lastIterationEnv, lastIterationEnv
        getfield(EnvRecord::class, "outerEnv", EnvRecord::class)
        // lastIterationEnv, outer
        createDeclarativeEnvRecord()
        // lastIterationEnv, thisIterationEnv

        val thisIterationEnv = astore()
        val lastIterationEnv = astore()

        tryCatchBuilder {
            tryBlock {
                perIterationBindings.forEach {
                    load(thisIterationEnv)
                    ldc(it)
                    ldc(false)
                    // string, false, env
                    invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)

                    load(thisIterationEnv)
                    ldc(it)
                    // thisIterationEnv, string

                    load(lastIterationEnv)
                    ldc(it)
                    ldc(true)
                    // thisIterationEnv, string, binding, true, env
                    invokevirtual(EnvRecord::class, "getBindingValue", JSValue::class, String::class, Boolean::class)
                    // thisIterationEnv, string, value

                    invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
                }
            }

            finallyBlock {
                load(thisIterationEnv)
                storeLexicalEnv()
            }
        }
    }

    protected fun MethodAssembly.compileLexicalDeclaration(node: LexicalDeclarationNode) {
        node.bindingList.lexicalBindings.forEach { binding ->
            ldc(binding.identifier.identifierName)
            operation("resolveBinding", JSReference::class, String::class)
            stackHeight++
            if (binding.initializer == null) {
                expect(!node.isConst)
                loadUndefined()
            } else {
                compileExpression(binding.initializer.node)
                stackHeight--
                if (Operations.isAnonymousFunctionDefinition(binding.initializer.node)) {
                    dup
                    checkcast<JSFunction>()
                    construct(PropertyKey::class, String::class) {
                        ldc(binding.identifier.identifierName)
                    }
                    operation("setFunctionName", Boolean::class, JSFunction::class, PropertyKey::class)
                    pop
                }
                getValue
            }
            operation("initializeReferencedBinding", void, JSReference::class, JSValue::class)
            stackHeight--
        }
    }

    protected fun MethodAssembly.compileFunctionDeclaration(node: StatementNode) {
        // nop
    }

    protected fun MethodAssembly.compileClassDeclaration(node: ClassDeclarationNode) {
        bindingClassDeclarationEvaluation(node)
        pop
        stackHeight--
    }

    protected fun MethodAssembly.bindingClassDeclarationEvaluation(classDeclarationNode: ClassDeclarationNode) {
        val node = classDeclarationNode.classNode
        if (node.identifier == null) {
            classDefinitionEvaluation(node, null, "default")
        } else {
            val className = node.identifier.identifierName
            classDefinitionEvaluation(node, className, className)
            dup
            loadLexicalEnv()
            swap
            stackHeight += 2
            initializeBoundName(className)
        }
    }

    protected fun MethodAssembly.classDefinitionEvaluation(node: ClassNode, classBinding: String?, className: String) {
        loadLexicalEnv()
        dup
        val env = astore()
        createDeclarativeEnvRecord()
        val classScope = astore()

        if (classBinding != null) {
            load(classScope)
            ldc(classBinding)
            ldc(true)
            invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
        }

        // Push protoParent and constructorParent onto the stack
        if (node.heritage == null) {
            loadRealm()
            dup
            invokevirtual(Realm::class, "getObjectProto", JSObjectProto::class)
            swap
            invokevirtual(Realm::class, "getFunctionProto", JSFunctionProto::class)
        } else {
            load(classScope)
            storeLexicalEnv()
            compileExpression(node.heritage)
            stackHeight--
            load(env)
            storeLexicalEnv()
            getValue
            dup
            val superclass = astore()

            loadNull()
            ifElseStatement(JumpCondition.RefEqual) {
                ifBlock {
                    loadNull()
                    loadRealm()
                    invokevirtual(Realm::class, "getFunctionProto", JSFunctionProto::class)
                }

                elseBlock {
                    load(superclass)
                    operation("isConstructor", Boolean::class, JSValue::class)

                    ifElseStatement(JumpCondition.True) {
                        ifBlock {
                            load(superclass)
                            checkcast<JSObject>()
                            ldc("prototype")
                            invokevirtual(JSObject::class, "get", JSValue::class, String::class)

                            val end = makeLabel()

                            dup
                            instanceof<JSObject>()
                            ifStatement(JumpCondition.True) {
                                goto(end)
                            }

                            dup
                            loadNull()
                            ifStatement(JumpCondition.RefEqual) {
                                goto(end)
                            }

                            loadKObject<Errors.Class.BadExtendsProto>()
                            invokevirtual(Errors.Class.BadExtendsProto::class, "throwTypeError", Nothing::class)
                            loadUndefined()
                            areturn

                            placeLabel(end)

                            load(superclass)
                        }

                        elseBlock {
                            loadKObject<Errors.Class.BadExtends>()
                            invokevirtual(Errors.Class.BadExtends::class, "throwTypeError", Nothing::class)
                            loadUndefined()
                            areturn
                        }
                    }
                }
            }
        }

        // protoParent, ctorParent
        swap
        loadRealm()
        // ctorParent, protoParent, realm
        swap
        // ctorParent, realm, protoParent

        invokestatic(JSObject::class, "create", JSObject::class, Realm::class, JSValue::class)
        val proto = astore()
        // ctorParent

        val constructor = (node.body.constructorMethod() as? ClassElementNode)?.node as? MethodDefinitionNode ?:
        if (node.heritage != null) {
            MethodDefinitionNode(
                PropertyNameNode(IdentifierNode("constructor"), false),
                FormalParametersNode(
                    FormalParameterListNode(emptyList()),
                    FunctionRestParameterNode(
                        BindingRestElement(BindingIdentifierNode("args"))
                    )
                ),
                FunctionStatementList(
                    StatementListNode(listOf(ExpressionStatementNode(
                        SuperCallNode(ArgumentsNode(ArgumentsListNode(listOf(
                            ArgumentListEntry(
                                IdentifierReferenceNode("args"),
                                true
                            )
                        ))))
                    )))
                ),
                MethodDefinitionNode.Type.Normal
            )
        } else {
            MethodDefinitionNode(
                PropertyNameNode(IdentifierNode("constructor"), false),
                FormalParametersNode(FormalParameterListNode(emptyList()), null),
                FunctionStatementList(null),
                MethodDefinitionNode.Type.Normal
            )
        }

        // ctorParent

        load(classScope)
        storeLexicalEnv()

        // ctorParent
        load(proto)
        swap
        // proto, ctorParent

        stackHeight += 2
        defineMethod(constructor)

        // DefinedMethod
        invokevirtual(Interpreter.DefinedMethod::class, "getClosure", JSFunction::class)

        // classFunction
        dup
        val classFunction = astore()

        // classFunction
        dup
        ldc(true)
        invokevirtual(JSFunction::class, "setStrict", void, Boolean::class)

        dup
        construct(PropertyKey::class, String::class) {
            ldc(className)
        }
        operation("setFunctionName", Boolean::class, JSFunction::class, PropertyKey::class)
        pop

        dup
        ldc(false)
        load(proto)
        operation("makeConstructor", void, JSFunction::class, Boolean::class, JSObject::class)

        if (node.heritage != null) {
            dup
            loadEnumMember<JSFunction.ConstructorKind>("Derived")
            invokevirtual(JSFunction::class, "setConstructorKind", void, JSFunction.ConstructorKind::class)
        }

        dup
        ldc(true)
        invokevirtual(JSFunction::class, "setClassConstructor", void, Boolean::class)

        // classFunction
        new<Descriptor>()
        // classFunction, descriptor
        dup_x1
        // descriptor, classFunction, descriptor
        swap
        // descriptor, descriptor, classFunction
        ldc(Descriptor.WRITABLE or Descriptor.CONFIGURABLE)
        // descriptor, descriptor, classFunction, attrs
        invokespecial(Descriptor::class, "<init>", void, JSValue::class, Int::class)
        // descriptor

        load(proto)
        // descriptor, proto
        swap
        // proto, descriptor
        construct(PropertyKey::class, String::class) {
            ldc("constructor")
        }
        // proto, descriptor, key
        swap
        // proto, key, descriptor
        invokevirtual(JSObject::class, "defineOwnProperty", Boolean::class, PropertyKey::class, Descriptor::class)
        pop

        construct(ArrayList::class)
        val instanceFields = astore()

        node.body.elements.filter {
            it.node != constructor
        }.forEach { element ->
            tryCatchBuilder {
                tryBlock {
                    if (element.isStatic) {
                        load(classFunction)
                    } else {
                        load(proto)
                    }
                    stackHeight++
                    classElementEvaluation(element, false, element.isStatic)
                    dup
                    ifElseStatement(JumpCondition.NonNull) {
                        ifBlock {
                            load(instanceFields)
                            swap
                            invokeinterface(List::class, "add", Boolean::class, Any::class)
                            pop
                        }

                        elseBlock {
                            pop
                        }
                    }
                    stackHeight--
                }

                catchBlock<ThrowException> {
                    load(env)
                    storeLexicalEnv()
                    athrow
                }
            }
        }

        load(env)
        storeLexicalEnv()
        if (classBinding != null) {
            load(classScope)
            ldc(classBinding)
            load(classFunction)
            invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
        }

        load(classFunction)
        load(instanceFields)
        invokevirtual(JSFunction::class, "setFields", void, List::class)

        load(classFunction)
    }

    // Consumes a JSObject from the stack, pushes a FieldRecord or null
    protected fun MethodAssembly.classElementEvaluation(element: ClassElementNode, enumerable: Boolean, isStatic: Boolean) {
        when (element.type) {
            ClassElementNode.Type.Method -> {
                propertyDefinitionEvaluation(element.node!! as MethodDefinitionNode, enumerable, true)
                aconst_null
                stackHeight++
            }
            ClassElementNode.Type.Field -> {
                evaluatePropertyName(element.node!!)

                if (isStatic) {
                    // obj, name
                    if (element.initializer == null) {
                        loadUndefined()
                        // obj, name, undefined
                        // <empty>
                        operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                        pop
                        stackHeight--
                    } else {
                        compileExpression(element.initializer.node)
                        // obj, name, expr
                        getValue
                        operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                        pop
                        // <empty>
                        stackHeight -= 2
                    }
                    aconst_null
                } else {
                    // obj, name
                    swap
                    // name, obj

                    if (element.initializer != null) {
                        val obj = astore()
                        new<JSFunction.FieldRecord>()
                        dup_x1
                        swap
                        loadLexicalEnv()
                        loadRealm()
                        invokevirtual(Realm::class, "getFunctionProto", JSFunctionProto::class)
                        val formalParameterList = FormalParametersNode(FormalParameterListNode(emptyList()), null)
                        // FieldRecord, name, env, funcProto
                        stackHeight++
                        ordinaryFunctionCreate(
                            "TODO",
                            formalParameterList,
                            FunctionStatementList(StatementListNode(listOf(
                                ReturnStatementNode(element.initializer.node)
                            ))),
                            JSFunction.ThisMode.Lexical,
                            null,
                            isConstructor = false,
                        )
                        // FieldRecord, name, initializer
                        dup
                        ldc(true)
                        // FieldRecord, name, initializer, initializer, boolean
                        invokevirtual(JSFunction::class, "setStrict", void, Boolean::class)
                        // FieldRecord, name, initializer
                        dup
                        load(obj)
                        // FieldRecord, name, initializer, initializer, obj
                        operation("makeMethod", JSValue::class, JSFunction::class, JSObject::class)
                        // FieldRecord, name, initializer, result
                        pop
                        // FieldRecord, name, initializer
                        ldc(Operations.isAnonymousFunctionDefinition(element.initializer))
                        // FieldRecord, name, initializer, boolean
                    } else {
                        pop
                        new<JSFunction.FieldRecord>()
                        dup_x1
                        swap
                        loadKObject<JSEmpty>()
                        ldc(false)
                        // FieldRecord, name, JSEmpty, boolean
                    }

                    invokespecial(JSFunction.FieldRecord::class, "<init>", void, JSValue::class, JSValue::class, Boolean::class)
                    stackHeight--
                }
            }
            ClassElementNode.Type.Empty -> {
                aconst_null
                stackHeight++
            }
        }
    }

    // Consumes an env and JSValue from the stack
    protected fun MethodAssembly.initializeBoundName(name: String) {
        dup
        ifElseStatement(JumpCondition.Null) {
            ifBlock {
                swap
                pop
                ldc(name)
                operation("resolveBinding", JSReference::class, String::class)
                swap
                // binding, value
                operation("putValue", void, JSValue::class, JSValue::class)
            }

            elseBlock {
                ldc(name)
                swap
                invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
            }
        }
        stackHeight -= 2
    }

    protected fun MethodAssembly.compileReturnStatement(node: ReturnStatementNode) {
        if (node.node != null) {
            compileExpression(node.node)
            getValue
            stackHeight--
        } else {
            loadUndefined()
        }
        areturn
    }

    protected fun MethodAssembly.compileThrowStatement(node: ThrowStatementNode) {
        construct(ThrowException::class, JSValue::class) {
            compileExpression(node.expr)
            getValue
        }
        athrow
        stackHeight--
    }

    protected fun MethodAssembly.compileTryStatement(node: TryStatementNode) {
        tryCatchBuilder {
            tryBlock {
                compileBlock(node.tryBlock)
            }

            if (node.catchNode != null) {
                catchBlock<ThrowException> {
                    if (node.catchNode.catchParameter == null) {
                        pop
                        compileBlock(node.catchNode.block)
                    } else {
                        val ex = astore()
                        loadLexicalEnv()
                        dup
                        createDeclarativeEnvRecord()

                        val parameter = node.catchNode.catchParameter
                        parameter.boundNames().forEach { name ->
                            dup
                            ldc(name)
                            ldc(false)
                            invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                        }

                        // oldEnv, catchEnv
                        storeLexicalEnv()
                        val oldEnv = astore()

                        tryCatchBuilder {
                            tryBlock {
                                load(ex)
                                invokevirtual(ThrowException::class, "getValue", JSValue::class)
                                loadLexicalEnv()
                                bindingInitialization(parameter)

                                compileBlock(node.catchNode.block)
                            }

                            finallyBlock {
                                load(oldEnv)
                                storeLexicalEnv()
                            }
                        }
                    }
                }
            }

            if (node.finallyBlock != null) {
                finallyBlock {
                    compileBlock(node.finallyBlock)
                }
            }
        }
    }

    protected fun MethodAssembly.compileBreakStatement(node: BreakStatementNode) {
        var labelNode = labelNodes.removeLast()
        if (node.label != null) {
            while (labelNode.labelName != node.label.identifierName)
                labelNode = labelNodes.removeLast()
            expect(labelNode.breakLabel != null)
        } else {
            while (labelNode.labelName != null && labelNode.breakLabel == null)
                labelNode = labelNodes.removeLast()
        }

        repeat(stackHeight - labelNode.stackHeight) {
            pop
            stackHeight--
        }

        goto(labelNode.breakLabel!!)
    }

    protected fun MethodAssembly.compileLabelledStatement(node: LabelledStatementNode) {
        val label = node.label.identifierName
        when (node.item) {
            is DoWhileStatementNode -> compileDoWhileStatement(node.item, label)
            is WhileStatementNode -> compileWhileStatement(node.item, label)
            is ForStatementNode -> compileForStatement(node.item, label)
            else -> compileStatement(node.item)
        }
    }

    protected fun MethodAssembly.compileImportDeclaration(node: StatementNode) {
        TODO()
    }

    protected fun MethodAssembly.compileExportDeclaration(node: StatementNode) {
        TODO()
    }

    protected fun MethodAssembly.compileExpression(node: ExpressionNode) {
        when (node) {
            ThisNode -> compileThis()
            is CommaExpressionNode -> compileCommaExpressionNode(node)
            is IdentifierReferenceNode -> compileIdentifierReference(node)
            is FunctionExpressionNode -> compileFunctionExpression(node)
            is ArrowFunctionNode -> compileArrowFunction(node)
            is LiteralNode -> compileLiteral(node)
            is RegExpLiteralNode -> compileRegExp(node)
            is NewExpressionNode -> compileNewExpression(node)
            is CallExpressionNode -> compileCallExpression(node)
            is ObjectLiteralNode -> compileObjectLiteral(node)
            is ArrayLiteralNode -> compileArrayLiteral(node)
            is MemberExpressionNode -> compileMemberExpression(node)
            is OptionalExpressionNode -> compileOptionalExpression(node)
            is AssignmentExpressionNode -> compileAssignmentExpression(node)
            is ConditionalExpressionNode -> compileConditionalExpression(node)
            is CoalesceExpressionNode -> compileCoalesceExpression(node)
            is LogicalORExpressionNode -> compileLogicalORExpression(node)
            is LogicalANDExpressionNode -> compileLogicalANDExpression(node)
            is BitwiseORExpressionNode -> compileBitwiseORExpression(node)
            is BitwiseXORExpressionNode -> compileBitwiseXORExpression(node)
            is BitwiseANDExpressionNode -> compileBitwiseANDExpression(node)
            is EqualityExpressionNode -> compileEqualityExpression(node)
            is RelationalExpressionNode -> compileRelationalExpression(node)
            is ShiftExpressionNode -> compileShiftExpression(node)
            is AdditiveExpressionNode -> compileAdditiveExpression(node)
            is MultiplicativeExpressionNode -> compileMultiplicationExpression(node)
            is ExponentiationExpressionNode -> compileExponentiationExpression(node)
            is UnaryExpressionNode -> compileUnaryExpression(node)
            is UpdateExpressionNode -> compileUpdateExpression(node)
            is ParenthesizedExpressionNode -> compileExpression(node.target)
            is ForBindingNode -> compileForBinding(node)
            is TemplateLiteralNode -> compileTemplateLiteral(node)
            is ClassExpressionNode -> compileClassExpression(node)
            is SuperPropertyNode -> compileSuperProperty(node)
            is SuperCallNode -> compileSuperCall(node)
            else -> unreachable()
        }
    }

    protected fun MethodAssembly.compileThis() {
        operation("resolveThisBinding", JSValue::class)
        stackHeight++
    }

    protected fun MethodAssembly.compileCommaExpressionNode(node: CommaExpressionNode) {
        node.expressions.forEachIndexed { index, expression ->
            compileExpression(expression)
            getValue
            if (index != node.expressions.lastIndex) {
                pop
                stackHeight--
            }
        }
    }

    protected fun MethodAssembly.compileIdentifierReference(node: IdentifierReferenceNode) {
        ldc(node.identifierName)
        operation("resolveBinding", JSReference::class, String::class)
        stackHeight++
    }

    protected fun MethodAssembly.compileFunctionExpression(node: FunctionExpressionNode) {
        if (node.identifier == null) {
            loadLexicalEnv()
            loadRealm()
            invokevirtual(Realm::class, "getFunctionProto", JSFunctionProto::class)
            val sourceText = "TODO"
            stackHeight += 2

            ordinaryFunctionCreate(
                sourceText,
                node.parameters,
                node.body,
                JSFunction.ThisMode.NonLexical,
                "",
                isConstructor = true,
            )
        } else {
            loadLexicalEnv()
            createDeclarativeEnvRecord()
            dup
            // funcEnv, funcEnv
            ldc(node.identifier.identifierName)
            ldc(false)
            // funcEnv, funcEnv, string, boolean
            invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
            // funcEnv

            dup
            // funcEnv, funcEnv

            loadRealm()
            invokevirtual(Realm::class, "getFunctionProto", JSFunctionProto::class)
            val sourceText = "TODO"
            // funcEnv, funcEnv, proto
            stackHeight += 2

            ordinaryFunctionCreate(
                sourceText,
                node.parameters,
                node.body,
                JSFunction.ThisMode.NonLexical,
                node.name,
                isConstructor = true
            )

            // funcEnv, closure
            dup_x1
            // closure, funcEnv, closure
            ldc(node.identifier.identifierName)
            swap

            // closure, funcEnv, name, closure
            invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
        }
    }

    protected fun MethodAssembly.compileArrowFunction(node: ArrowFunctionNode) {
        loadLexicalEnv()
        val sourceText = "TODO"
        val parameters = node.parameters.let {
            if (it is BindingIdentifierNode) {
                FormalParametersNode(
                    FormalParameterListNode(
                        listOf(FormalParameterNode(SingleNameBindingElement(it, null)))
                    ),
                    null
                )
            } else it as FormalParametersNode
        }
        val body = node.body.let {
            if (it is ExpressionNode) {
                FunctionStatementList(StatementListNode(listOf(
                    ReturnStatementNode(it)
                )))
            } else it as FunctionStatementList
        }

        loadRealm()
        invokevirtual(Realm::class, "getFunctionProto", JSFunctionProto::class)
        stackHeight += 2

        ordinaryFunctionCreate(
            sourceText,
            parameters,
            body,
            JSFunction.ThisMode.Lexical,
            node.name,
            isConstructor = false
        )
    }

    protected fun MethodAssembly.compileLiteral(node: LiteralNode) {
        when (node) {
            is NullNode -> loadNull()
            is BooleanNode -> (if (node.value) JSTrue::class else JSFalse::class).let {
                getstatic(it, "INSTANCE", it)
            }
            is NumericLiteralNode -> construct(JSNumber::class, Double::class) {
                ldc(node.value)
            }
            is StringLiteralNode -> construct(JSString::class, String::class) {
                ldc(node.value)
            }
            else -> unreachable()
        }
        stackHeight++
    }

    protected fun MethodAssembly.compileRegExp(node: RegExpLiteralNode) {
        loadRealm()
        ldc(node.source)
        ldc(node.flags)
        invokestatic(JSRegExpObject::class, "create", JSRegExpObject::class, Realm::class, String::class, String::class)
    }

    protected fun MethodAssembly.compileNewExpression(node: NewExpressionNode) {
        compileExpression(node.target)
        getValue
        dup
        operation("isConstructor", Boolean::class, JSValue::class)
        ifStatement(JumpCondition.False) {
            new<Errors.NotACtor>()
            dup_x1
            swap
            operation("toPrintableString", String::class, JSValue::class)
            invokespecial(Errors.NotACtor::class, "<init>", void, String::class)
            invokevirtual(Errors.NotACtor::class, "throwTypeError", Nothing::class)
            loadUndefined()
            areturn
        }
        if (node.arguments == null) {
            construct(ArrayList::class)
            stackHeight++
        } else {
            argumentsListEvaluation(node.arguments)
        }
        operation("construct", JSValue::class, JSValue::class, List::class)
        stackHeight--
    }

    protected fun MethodAssembly.argumentsListEvaluation(node: ArgumentsNode) {
        construct(ArrayList::class)
        stackHeight++

        val entries = node.arguments
        if (entries.isEmpty())
            return

        entries.forEach {
            dup

            if (it.isSpread) {
                // list

                compileExpression(it.expression)
                getValue
                // list, value
                operation("getIterator", Operations.IteratorRecord::class, JSValue::class)
                // list, record

                val whileStart = makeLabel()
                val whileEnd = makeLabel()

                placeLabel(whileStart)

                // list, record
                operation("iteratorStep", JSValue::class, Operations.IteratorRecord::class)
                // list, next
                dup
                // list, next, next
                loadFalse()
                // list, next, next, false
                ifElseStatement(JumpCondition.RefEqual) {
                    ifBlock {
                        // list, next
                        pop2
                        goto(whileEnd)
                    }

                    elseBlock {
                        // list, next
                        operation("iteratorValue", JSValue::class, JSValue::class)
                        // list, nextArg
                        invokeinterface(List::class, "add", Boolean::class, Object::class)
                        pop
                    }
                }

                placeLabel(whileEnd)
                stackHeight--
            } else {
                compileExpression(it.expression)
                getValue
                invokeinterface(List::class, "add", Boolean::class, Object::class)
                pop
                stackHeight--
            }
        }

    }

    protected fun MethodAssembly.compileCallExpression(node: CallExpressionNode) {
        compileExpression(node.target)
        dup
        getValue
        swap
        argumentsListEvaluation(node.arguments)
        ldc(false)
        operation("evaluateCall", JSValue::class, JSValue::class, JSValue::class, List::class, Boolean::class)
        stackHeight--

        // TODO: The following code checks for a direct eval invocation, but we should
        // be able to do that statically
//        compileExpression(node.target)
//        dup
//        val ref = astore()
//        getValue
//        val func = astore()
//        argumentsListEvaluation(node.arguments)
//        val args = astore()
//
//        val end = makeLabel()
//
//        load(ref)
//        instanceof<JSReference>()
//        ifStatement(JumpCondition.False) {
//            goto(end)
//        }
//
//        load(ref)
//        checkcast<JSReference>()
//        invokevirtual(JSReference::class, "isPropertyReference", Boolean::class)
//        ifStatement(JumpCondition.True) {
//            goto(end)
//        }
//
//        load(ref)
//        checkcast<JSReference>()
//        getfield(JSReference::class, "name", PropertyKey::class)
//        dup
//        invokevirtual(PropertyKey::class, "isString", Boolean::class)
//        ifStatement(JumpCondition.False) {
//            goto(end)
//        }
//
//
//        invokevirtual(PropertyKey::class, "getAsString", String::class)
//        ldc("eval")
//        ifStatement(JumpCondition.Equal) {
//            goto(end)
//        }
//
//        load(func)
//        loadRealm()
//        invokevirtual(Realm::class, "getGlobalObject", JSObject::class)
//        ldc("eval")
//        invokevirtual(JSObject::class, "get", JSValue::class, String::class)
//        invokevirtual(JSValue::class, "sameValue", Boolean::class, JSValue::class)
//        ifStatement(JumpCondition.False) {
//            goto(end)
//        }
//
//        val pastEnd = makeLabel()
//
//        load(args)
//        invokevirtual(List::class, "isEmpty", Boolean::class)
//        ifElseStatement(JumpCondition.True) {
//            ifBlock {
//                loadUndefined()
//                goto(pastEnd)
//            }
//
//            elseBlock {
//                load(args)
//                ldc(false
//                invokevirtual(List::class, "get", JSValue::class, Int::class)
//                loadRealm()
//                operation("isStrict", Boolean::class)
//                ldc(true)
//                invokestatic(JSGlobalObject::class, "performEval", JSValue::class, JSValue::class, Realm::class, Boolean::class, Boolean::class)
//
//                goto(pastEnd)
//            }
//        }
//
//        placeLabel(end)
//        load(func)
//        load(ref)
//        load(args)
//        ldc(false
//        operation("evaluateCall", JSValue::class, JSValue::class, JSValue::class, List::class, Boolean::class)
//
//        placeLabel(pastEnd)
//
//        stackHeight++
    }

    protected fun MethodAssembly.compileObjectLiteral(node: ObjectLiteralNode) {
        loadRealm()
        invokestatic(JSObject::class, "create", JSObject::class, Realm::class)

        stackHeight++

        if (node.list == null)
            return

        node.list.properties.forEach { property ->
            dup
            when (property.type) {
                PropertyDefinitionNode.Type.KeyValue -> {
                    evaluatePropertyName(property.first)
                    compileExpression(property.second!!)
                    getValue
                    operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                    pop
                    stackHeight -= 2
                }
                PropertyDefinitionNode.Type.Shorthand -> {
                    expect(property.first is IdentifierReferenceNode)
                    construct(JSString::class, String::class) {
                        ldc(property.first.identifierName)
                    }
                    compileIdentifierReference(property.first)
                    getValue
                    operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                    pop
                    stackHeight--
                }
                PropertyDefinitionNode.Type.Method -> {
                    stackHeight++
                    propertyDefinitionEvaluation(property.first as MethodDefinitionNode, enumerable = true, isStrict = true)
                }
                PropertyDefinitionNode.Type.Spread -> TODO()
            }
        }
    }

    // Takes obj (JSObject) on the stack. Does not push a result
    protected fun MethodAssembly.propertyDefinitionEvaluation(
        methodDefinitionNode: MethodDefinitionNode,
        enumerable: Boolean,
        isStrict: Boolean,
    ) {
        // obj
        val enumAttr = if (enumerable) Descriptor.ENUMERABLE else 0

        when (methodDefinitionNode.type) {
            MethodDefinitionNode.Type.Normal -> {
                dup
                dup
                stackHeight += 2
                defineMethod(methodDefinitionNode)
                // obj, DefinedMethod
                if (isStrict) {
                    dup
                    invokevirtual(Interpreter.DefinedMethod::class, "getClosure", JSFunction::class)
                    ldc(true)
                    invokevirtual(JSFunction::class, "setStrict", void, Boolean::class)
                }
                // obj, DefinedMethod
                dup
                invokevirtual(Interpreter.DefinedMethod::class, "getClosure", JSFunction::class)
                // obj, DefinedMethod, closure
                swap
                // obj, closure, DefinedMethod
                dup_x1
                // obj, DefinedMethod, closure, DefinedMethod
                invokevirtual(Interpreter.DefinedMethod::class, "getKey", PropertyKey::class)
                // obj, DefinedMethod, closure, PropertyKey
                operation("setFunctionName", Boolean::class, JSFunction::class, PropertyKey::class)
                pop
                // obj, DefinedMethod
                dup
                // obj, DefinedMethod, DefinedMethod
                invokevirtual(Interpreter.DefinedMethod::class, "getKey", PropertyKey::class)
                // obj, DefinedMethod, key
                swap
                // obj, key, DefinedMethod
                invokevirtual(Interpreter.DefinedMethod::class, "getClosure", JSFunction::class)
                // obj, key, closure

                new<Descriptor>()
                // obj, key, closure, Descriptor
                dup_x1
                // obj, key, Descriptor, closure, Descriptor
                swap
                // obj, key, Descriptor, Descriptor, closure
                ldc(Descriptor.CONFIGURABLE or enumAttr or Descriptor.WRITABLE)
                // obj, key, Descriptor, Descriptor, closure, attrs
                invokespecial(Descriptor::class, "<init>", void, JSValue::class, Int::class)
                // obj, key, Descriptor
                operation("definePropertyOrThrow", Boolean::class, JSValue::class, PropertyKey::class, Descriptor::class)
                stackHeight--
                pop
            }
            MethodDefinitionNode.Type.Getter, MethodDefinitionNode.Type.Setter -> {
                val isGetter = methodDefinitionNode.type == MethodDefinitionNode.Type.Getter

                loadLexicalEnv()
                loadRealm()
                invokevirtual(Realm::class, "getFunctionProto", JSFunctionProto::class)
                stackHeight++
                ordinaryFunctionCreate(
                    "TODO",
                    methodDefinitionNode.parameters,
                    methodDefinitionNode.body,
                    JSFunction.ThisMode.NonLexical,
                    null,
                    isConstructor = false,
                )
                // obj, closure
                dup
                // obj, closure, closure
                evaluatePropertyName(methodDefinitionNode.identifier)
                // obj, closure, closure, keyValue
                stackHeight--
                operation("toPropertyKey", PropertyKey::class, JSValue::class)
                // obj, closure, closure, key
                dup
                val propKey = astore()
                ldc(if (isGetter) "get" else "set")
                // obj, closure, closure, key, "get"
                operation("setFunctionName", Boolean::class, JSFunction::class, PropertyKey::class, String::class)
                // obj, closure, boolean
                pop
                // obj, closure
                dup2
                // obj, closure, obj, closure
                swap
                // obj, closure, closure, obj
                operation("makeMethod", JSValue::class, JSFunction::class, JSObject::class)
                // obj, closure, value
                pop
                // obj, closure
                load(propKey)
                swap
                new<Descriptor>()
                // obj, closure, desc
                dup_x1
                // obj, desc, closure, desc
                swap
                // obj, desc, desc, closure

                new<JSAccessor>()
                dup_x1
                swap

                aconst_null
                if (!isGetter)
                    swap
                invokespecial(JSAccessor::class, "<init>", void, JSFunction::class, JSFunction::class)
                // obj, desc, desc, accessor
                ldc(Descriptor.CONFIGURABLE or enumAttr)
                // obj, desc, desc, accessor, attrs
                invokespecial(Descriptor::class, "<init>", void, JSValue::class, Int::class)
                // obj, desc
                operation("definePropertyOrThrow", Boolean::class, JSValue::class, PropertyKey::class, Descriptor::class)
                pop
            }
            MethodDefinitionNode.Type.Generator -> TODO()
            MethodDefinitionNode.Type.Async -> TODO()
            MethodDefinitionNode.Type.AsyncGenerator -> TODO()
        }

        stackHeight--
    }

    // Takes obj (JSObject) and functionPrototype (JSObject) on the stack, pushes DefinedMethod
    protected fun MethodAssembly.defineMethod(method: MethodDefinitionNode) {
        expect(method.type == MethodDefinitionNode.Type.Normal)

        loadLexicalEnv()
        swap
        // obj, env, functionProto
        checkcast<JSObject>()
        ordinaryFunctionCreate(
            "TODO",
            method.parameters,
            method.body,
            JSFunction.ThisMode.NonLexical,
            null,
            isConstructor = false
        )
        // obj, closure
        dup_x1
        // closure, obj, closure
        swap
        // closure, closure, obj
        operation("makeMethod", JSValue::class, JSFunction::class, JSObject::class)
        pop

        // closure
        new<Interpreter.DefinedMethod>()
        // closure, DefinedMethod
        dup_x1
        swap
        // DefinedMethod, DefinedMethod, closure

        evaluatePropertyName(method.identifier)
        operation("toPropertyKey", PropertyKey::class, JSValue::class)
        swap

        // DefinedMethod, DefinedMethod, key, closure
        invokespecial(Interpreter.DefinedMethod::class, "<init>", void, PropertyKey::class, JSFunction::class)
        stackHeight--
        // DefinedMethod
    }

    protected fun MethodAssembly.evaluatePropertyName(node: ASTNode) {
        if (node is PropertyNameNode) {
            if (node.isComputed) {
                compileExpression(node.expr)
                getValue
                operation("toPropertyKey", PropertyKey::class, JSValue::class)
                invokevirtual(PropertyKey::class, "getAsValue", JSValue::class)
            } else when (val expr = node.expr) {
                is IdentifierNode -> {
                    construct(JSString::class, String::class) {
                        ldc(expr.identifierName)
                    }
                    stackHeight++
                }
                is StringLiteralNode, is NumericLiteralNode -> compileExpression(expr)
                else -> unreachable()
            }
        } else TODO()
    }

    protected fun MethodAssembly.compileArrayLiteral(node: ArrayLiteralNode) {
        ldc(node.elements.size)
        operation("arrayCreate", JSObject::class, Int::class)
        stackHeight++
        if (node.elements.isEmpty())
            return

        node.elements.forEachIndexed { index, element ->
            dup
            construct(JSNumber::class, Int::class) {
                ldc(index)
            }
            when (element.type) {
                ArrayElementNode.Type.Normal -> {
                    compileExpression(element.expression!!)
                    getValue
                    operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                    pop
                    stackHeight--
                }
                ArrayElementNode.Type.Spread -> TODO()
                ArrayElementNode.Type.Elision -> { }
            }
        }
    }

    protected fun MethodAssembly.compileMemberExpression(node: MemberExpressionNode) {
        when (node.type) {
            MemberExpressionNode.Type.Computed -> {
                compileExpression(node.lhs)
                getValue
                // TODO: Strict mode
                compileExpression(node.rhs)
                getValue
                operation("isStrict", Boolean::class)
                operation("evaluatePropertyAccessWithExpressionKey", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                stackHeight--
            }
            MemberExpressionNode.Type.NonComputed -> {
                compileExpression(node.lhs)
                getValue
                // TODO: Strict mode
                ldc((node.rhs as IdentifierNode).identifierName)
                operation("isStrict", Boolean::class)
                operation("evaluatePropertyAccessWithIdentifierKey", JSValue::class, JSValue::class, String::class, Boolean::class)
            }
            MemberExpressionNode.Type.Tagged -> TODO()
        }
    }

    protected fun MethodAssembly.compileOptionalExpression(node: ExpressionNode) {
        TODO()
    }

    protected fun MethodAssembly.compileAssignmentExpression(node: AssignmentExpressionNode) {
        val (lhs, rhs) = node.let { it.lhs to it.rhs }

        if (lhs.let { it is ObjectLiteralNode && it is ArrayLiteralNode })
            TODO()

        when (node.op) {
            AssignmentExpressionNode.Operator.Equals -> {
                compileExpression(lhs)
                compileExpression(rhs)
                getValue
                if (Operations.isAnonymousFunctionDefinition(rhs) && lhs is IdentifierReferenceNode) {
                    dup
                    checkcast<JSFunction>()
                    construct(PropertyKey::class, String::class) {
                        ldc(lhs.identifierName)
                    }
                    operation("setFunctionName", Boolean::class, JSFunction::class, PropertyKey::class)
                    pop
                }
                dup_x1
                operation("putValue", void, JSValue::class, JSValue::class)
                stackHeight--
            }
            AssignmentExpressionNode.Operator.And -> {
                compileExpression(lhs)
                dup
                getValue
                toBoolean
                ifStatement(JumpCondition.True) {
                    if (Operations.isAnonymousFunctionDefinition(rhs) && lhs is IdentifierReferenceNode)
                        TODO()
                    compileExpression(rhs)
                    dup_x1
                    operation("putValue", void, JSValue::class, JSValue::class)
                    stackHeight--
                }
            }
            AssignmentExpressionNode.Operator.Or -> {
                compileExpression(lhs)
                dup
                getValue
                toBoolean
                ifStatement(JumpCondition.False) {
                    if (Operations.isAnonymousFunctionDefinition(rhs) && lhs is IdentifierReferenceNode)
                        TODO()
                    compileExpression(rhs)
                    dup_x1
                    operation("putValue", void, JSValue::class, JSValue::class)
                    stackHeight--
                }
            }
            AssignmentExpressionNode.Operator.Nullish -> {
                compileExpression(lhs)
                dup
                getValue
                invokevirtual(JSValue::class, "isNullish", Boolean::class)
                ifStatement(JumpCondition.True) {
                    if (Operations.isAnonymousFunctionDefinition(rhs) && lhs is IdentifierReferenceNode)
                        TODO()
                    compileExpression(rhs)
                    dup_x1
                    operation("putValue", void, JSValue::class, JSValue::class)
                    stackHeight--
                }
            }
            else -> {
                compileExpression(lhs)
                dup
                getValue
                compileExpression(rhs)
                getValue
                ldc(node.op.symbol.dropLast(1))
                operation("applyStringOrNumericBinaryOperator", JSValue::class, JSValue::class, JSValue::class, String::class)
                dup_x1
                operation("putValue", void, JSValue::class, JSValue::class)
                stackHeight--
            }
        }
    }

    protected fun MethodAssembly.compileConditionalExpression(node: ConditionalExpressionNode) {
        compileExpression(node.predicate)
        getValue
        toBoolean
        stackHeight--
        ifElseStatement(JumpCondition.True) {
            ifBlock {
                compileExpression(node.ifTrue)
            }

            elseBlock {
                compileExpression(node.ifFalse)
            }
        }
        stackHeight--
        getValue
    }

    protected fun MethodAssembly.compileCoalesceExpression(node: CoalesceExpressionNode) {
        compileExpression(node.lhs)
        getValue
        dup
        invokevirtual(JSValue::class, "isNullish", Boolean::class)
        ifStatement(JumpCondition.False) {
            pop
            stackHeight--
            compileExpression(node.rhs)
            getValue
        }
    }

    protected fun MethodAssembly.compileLogicalORExpression(node: LogicalORExpressionNode) {
        compileExpression(node.lhs)
        getValue
        dup
        toBoolean
        ifStatement(JumpCondition.False) {
            pop
            stackHeight--
            compileExpression(node.rhs)
            getValue
        }
    }

    protected fun MethodAssembly.compileLogicalANDExpression(node: LogicalANDExpressionNode) {
        compileExpression(node.lhs)
        getValue
        dup
        toBoolean
        ifStatement(JumpCondition.True) {
            pop
            stackHeight--
            compileExpression(node.rhs)
            getValue
        }
    }

    protected fun MethodAssembly.evaluateStringOrNumericBinaryExpression(lhs: ExpressionNode, rhs: ExpressionNode, op: String) {
        compileExpression(lhs)
        getValue
        compileExpression(rhs)
        getValue
        ldc(op)
        operation("applyStringOrNumericBinaryOperator", JSValue::class, JSValue::class, JSValue::class, String::class)
        stackHeight--
    }

    protected fun MethodAssembly.compileBitwiseORExpression(node: BitwiseORExpressionNode) {
        evaluateStringOrNumericBinaryExpression(node.lhs, node.rhs, "|")
    }

    protected fun MethodAssembly.compileBitwiseXORExpression(node: BitwiseXORExpressionNode) {
        evaluateStringOrNumericBinaryExpression(node.lhs, node.rhs, "^")
    }

    protected fun MethodAssembly.compileBitwiseANDExpression(node: BitwiseANDExpressionNode) {
        evaluateStringOrNumericBinaryExpression(node.lhs, node.rhs, "&")
    }

    protected fun MethodAssembly.compileEqualityExpression(node: EqualityExpressionNode) {
        compileExpression(node.lhs)
        getValue
        compileExpression(node.rhs)
        getValue
        stackHeight--

        when (node.op) {
            EqualityExpressionNode.Operator.StrictEquality -> {
                swap
                operation("strictEqualityComparison", JSValue::class, JSValue::class, JSValue::class)
            }
            EqualityExpressionNode.Operator.StrictInequality -> {
                swap
                operation("strictEqualityComparison", JSValue::class, JSValue::class, JSValue::class)
                invertJSBoolean()
            }
            EqualityExpressionNode.Operator.NonstrictEquality -> {
                swap
                operation("abstractEqualityComparison", JSValue::class, JSValue::class, JSValue::class)
            }
            EqualityExpressionNode.Operator.NonstrictInequality -> {
                swap
                operation("abstractEqualityComparison", JSValue::class, JSValue::class, JSValue::class)
                invertJSBoolean()
            }
        }
    }

    protected fun MethodAssembly.compileRelationalExpression(node: RelationalExpressionNode) {
        compileExpression(node.lhs)
        getValue
        compileExpression(node.rhs)
        getValue
        stackHeight--

        when (node.op) {
            RelationalExpressionNode.Operator.LessThan -> {
                ldc(true)
                operation("abstractRelationalComparison", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                dup
                loadUndefined()
                ifStatement(JumpCondition.RefEqual) {
                    pop
                    loadFalse()
                }
            }
            RelationalExpressionNode.Operator.GreaterThan -> {
                swap
                ldc(false)
                operation("abstractRelationalComparison", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                dup
                loadUndefined()
                ifStatement(JumpCondition.RefEqual) {
                    pop
                    loadFalse()
                }
            }
            RelationalExpressionNode.Operator.LessThanEquals -> {
                swap
                ldc(false)
                operation("abstractRelationalComparison", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                invertJSBoolean()
            }
            RelationalExpressionNode.Operator.GreaterThanEquals -> {
                ldc(true)
                operation("abstractRelationalComparison", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                invertJSBoolean()
            }
            RelationalExpressionNode.Operator.Instanceof ->
                operation("instanceofOperator", JSValue::class, JSValue::class, JSValue::class)
            RelationalExpressionNode.Operator.In -> {
                dup
                instanceof<JSObject>()
                ifStatement(JumpCondition.False) {
                    pop
                    loadKObject<Errors.InBadRHS>()
                    invokevirtual(Errors.InBadRHS::class, "throwTypeError", Nothing::class)
                    loadUndefined()
                    areturn
                }
                swap
                operation("toPropertyKey", PropertyKey::class, JSValue::class)
                operation("hasProperty", Boolean::class, JSValue::class, PropertyKey::class)
                new<JSBoolean>()
                dup_x1
                swap
                invokespecial(JSBoolean::class, "<init>", void, Boolean::class)
            }
        }
    }

    protected fun MethodAssembly.compileShiftExpression(node: ShiftExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            node.lhs,
            node.rhs,
            when (node.op) {
                ShiftExpressionNode.Operator.ShiftLeft -> "<<"
                ShiftExpressionNode.Operator.ShiftRight -> ">>"
                ShiftExpressionNode.Operator.UnsignedShiftRight -> ">>>"
            },
        )
    }

    protected fun MethodAssembly.compileAdditiveExpression(node: AdditiveExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            node.lhs,
            node.rhs,
            if (node.isSubtraction) "-" else "+",
        )
    }

    protected fun MethodAssembly.compileMultiplicationExpression(node: MultiplicativeExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            node.lhs,
            node.rhs,
            when (node.op) {
                MultiplicativeExpressionNode.Operator.Multiply -> "*"
                MultiplicativeExpressionNode.Operator.Divide -> "/"
                MultiplicativeExpressionNode.Operator.Modulo -> "%"
            },
        )
    }

    protected fun MethodAssembly.compileExponentiationExpression(node: ExponentiationExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            node.lhs,
            node.rhs,
            "**",
        )
    }

    protected fun MethodAssembly.compileUnaryExpression(node: UnaryExpressionNode) {
        compileExpression(node.node)

        when (node.op) {
            UnaryExpressionNode.Operator.Delete -> operation("deleteOperator", JSValue::class, JSValue::class)
            UnaryExpressionNode.Operator.Void -> {
                getValue
                pop
                loadUndefined()
            }
            UnaryExpressionNode.Operator.Typeof -> {
                getValue
                operation("typeofOperator", JSValue::class, JSValue::class)
            }
            UnaryExpressionNode.Operator.Plus -> {
                getValue
                operation("toNumber", JSValue::class, JSValue::class)
            }
            UnaryExpressionNode.Operator.Minus -> {
                getValue
                operation("toNumeric", JSValue::class, JSValue::class)
                dup
                instanceof<JSBigInt>()
                ifStatement(JumpCondition.True) {
                    pop
                    construct(Errors.TODO::class, String::class) {
                        ldc("compileUnaryExpression, -BigInt")
                    }
                    invokevirtual(Errors.TODO::class, "throwTypeError", Nothing::class)
                    loadUndefined()
                    areturn
                }
                operation("numericUnaryMinus", JSValue::class, JSValue::class)
            }
            UnaryExpressionNode.Operator.BitwiseNot -> {
                getValue
                operation("toNumeric", JSValue::class, JSValue::class)
                dup
                instanceof<JSBigInt>()
                ifStatement(JumpCondition.True) {
                    pop
                    construct(Errors.TODO::class, String::class) {
                        ldc("compileUnaryExpression, -BigInt")
                    }
                    invokevirtual(Errors.TODO::class, "throwTypeError", Nothing::class)
                    loadUndefined()
                    areturn
                }
                operation("numericBitwiseNOT", JSValue::class, JSValue::class)
            }
            UnaryExpressionNode.Operator.Not -> {
                getValue
                toBoolean
                ifElseStatement(JumpCondition.True) {
                    ifBlock {
                        loadFalse()
                    }

                    elseBlock {
                        loadTrue()
                    }
                }
            }
        }
    }

    protected fun MethodAssembly.compileUpdateExpression(node: UpdateExpressionNode) {
        compileExpression(node.target)
        dup
        // expr, expr
        getValue
        dup
        // expr, oldValue, oldValue
        instanceof<JSBigInt>()
        // expr, oldValue, boolean
        ifStatement(JumpCondition.True) {
            // expr, oldValue
            pop2
            construct(Errors.TODO::class, String::class) {
                ldc("compileUpdateExpression, BigInt")
            }
            invokevirtual(Errors.TODO::class, "throwTypeError", Nothing::class)
            loadUndefined()
            areturn
        }
        // expr, oldValue
        dup_x1
        // oldValue, expr, oldValue
        construct(JSNumber::class, Double::class) {
            ldc(1.0)
        }
        // oldValue, expr, oldValue, 1.0
        if (node.isIncrement) {
            operation("numericAdd", JSValue::class, JSValue::class, JSValue::class)
        } else {
            operation("numericSubtract", JSValue::class, JSValue::class, JSValue::class)
        }
        // oldValue, expr, newValue
        dup_x1
        // oldValue, newValue, expr, newValue
        operation("putValue", void, JSValue::class, JSValue::class)
        // oldValue, newValue
        if (!node.isPostfix)
            swap
        pop
    }

    protected fun MethodAssembly.compileForBinding(node: ForBindingNode) {
        ldc(node.identifier.identifierName)
        operation("resolveBinding", JSReference::class, String::class)
        stackHeight++
    }

    protected fun MethodAssembly.compileTemplateLiteral(node: TemplateLiteralNode) {
        construct(JSString::class, String::class) {
            buildStringHelper {
                node.parts.forEach {
                    append {
                        compileExpression(it)
                        getValue
                        operation("toString", JSString::class, JSValue::class)
                        invokevirtual(JSString::class, "getString", String::class)
                        stackHeight--
                    }
                }
            }
        }
        stackHeight++
    }

    protected fun MethodAssembly.compileClassExpression(node: ClassExpressionNode) {
        val name = node.classNode.identifier?.identifierName
        // TODO: Set [[SourceText]]
        if (name != null) {
            classDefinitionEvaluation(node.classNode, name, name)
        } else {
            classDefinitionEvaluation(node.classNode, null, "")
        }
    }

    protected fun MethodAssembly.compileSuperProperty(node: SuperPropertyNode) {
        operation("getThisEnvironment", EnvRecord::class)
        checkcast<FunctionEnvRecord>()
        invokevirtual(FunctionEnvRecord::class, "getThisBinding", JSValue::class)
        if (node.computed) {
            compileExpression(node.target)
            getValue
            operation("toPropertyKey", PropertyKey::class, JSValue::class)
        } else {
            construct(PropertyKey::class, String::class) {
                ldc((node.target as IdentifierNode).identifierName)
            }
            stackHeight++
        }
        operation("isStrict", Boolean::class)
        operation("makeSuperPropertyReference", JSReference::class, JSValue::class, PropertyKey::class, Boolean::class)
    }

    protected fun MethodAssembly.compileSuperCall(node: SuperCallNode) {
        operation("getSuperConstructor", JSValue::class)
        dup
        operation("isConstructor", Boolean::class, JSValue::class)
        ifStatement(JumpCondition.False) {
            loadKObject<Errors.Class.BadSuperFunc>()
            invokevirtual(Errors.Class.BadSuperFunc::class, "throwTypeError", Nothing::class)
            loadUndefined()
            areturn
        }
        argumentsListEvaluation(node.arguments)
        operation("getNewTarget", JSValue::class)
        checkcast<JSObject>()
        operation("construct", JSValue::class, JSValue::class, List::class, JSValue::class)
        // result

        operation("getThisEnvironment", EnvRecord::class)
        checkcast<FunctionEnvRecord>()
        // result, thisEnv
        dup
        // result, thisEnv, thisEnv
        invokevirtual(FunctionEnvRecord::class, "getThisBindingStatus", FunctionEnvRecord.ThisBindingStatus::class)
        // result, thisEnv, bindingStatus
        loadEnumMember<FunctionEnvRecord.ThisBindingStatus>("Initialized")
        // result, thisEnv, bindingStatus, bindingStatus
        ifStatement(JumpCondition.RefEqual) {
            loadKObject<Errors.Class.DuplicateSuperCall>()
            invokevirtual(Errors.Class.DuplicateSuperCall::class, "throwReferenceError", Nothing::class)
            loadUndefined()
            areturn
        }

        // result, thisEnv
        dup2
        // result, thisEnv, result, thisEnv
        swap
        // result, thisEnv, thisEnv, result
        invokevirtual(FunctionEnvRecord::class, "bindThisValue", JSValue::class, JSValue::class)
        pop
        // result, thisEnv
        invokevirtual(FunctionEnvRecord::class, "getFunction", JSFunction::class)
        // result, function
        swap
        // function, result
        checkcast<JSObject>()
        // function, result
        dup_x1
        swap
        // result, function
        invokestatic(JSFunction::class, "initializeInstanceFields", void, JSObject::class, JSFunction::class)
    }

    protected inline fun <reified T> MethodAssembly.loadEnumMember(name: String) {
        getstatic(T::class, name, T::class)
    }

    protected inline fun <reified T> MethodAssembly.loadKObject() {
        getstatic(T::class, "INSTANCE", T::class)
    }

    protected fun MethodAssembly.loadEmpty() = loadKObject<JSEmpty>()

    protected fun MethodAssembly.loadUndefined() = loadKObject<JSUndefined>()

    protected fun MethodAssembly.loadNull() = loadKObject<JSNull>()

    protected fun MethodAssembly.loadTrue() = loadKObject<JSTrue>()

    protected fun MethodAssembly.loadFalse() = loadKObject<JSFalse>()

    protected fun MethodAssembly.invertJSBoolean() {
        loadTrue()
        ifElseStatement(JumpCondition.RefEqual) {
            ifBlock {
                loadFalse()
            }

            elseBlock {
                loadTrue()
            }
        }
    }

    protected fun MethodAssembly.operation(name: String, returnType: TypeLike, vararg parameterTypes: TypeLike) {
        invokestatic(Operations::class, name, returnType, *parameterTypes)
    }

    val MethodAssembly.getValue: Unit
        get() = operation("getValue", JSValue::class, JSValue::class)

    val MethodAssembly.toBoolean: Unit
        get() = operation("toBoolean", Boolean::class, JSValue::class)

    protected fun MethodAssembly.createDeclarativeEnvRecord() {
        invokestatic(DeclarativeEnvRecord::class, "create", DeclarativeEnvRecord::class, EnvRecord::class)
    }

    protected fun MethodAssembly.loadContext() {
        invokestatic(Agent::class, "getRunningContext", ExecutionContext::class)
    }

    protected fun MethodAssembly.loadRealm() {
        loadContext()
        getfield(ExecutionContext::class, "realm", Realm::class)
    }

    protected fun MethodAssembly.loadVariableEnv() {
        loadContext()
        getfield(ExecutionContext::class, "variableEnv", EnvRecord::class)
    }

    protected fun MethodAssembly.loadLexicalEnv() {
        loadContext()
        getfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
    }

    protected fun MethodAssembly.storeVariableEnv() {
        loadContext()
        swap
        putfield(ExecutionContext::class, "variableEnv", EnvRecord::class)
    }

    protected fun MethodAssembly.storeLexicalEnv() {
        loadContext()
        swap
        putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
    }

    protected fun MethodAssembly.tryCatchBuilder(block: TryCatchBuilder.() -> Unit) {
        val builder = TryCatchBuilder().apply(block)
        expect(builder.catchBlocks.isNotEmpty() || builder.finallyBlock != null)

        val finallyBlock = builder.finallyBlock

        val mainStart = makeLabel()
        val mainEnd = makeLabel()
        val lastFinallyBlock = if (finallyBlock != null) makeLabel() else null
        val tryCatchFinallyEnd = makeLabel()

        placeLabel(mainStart)
        val prevInsnCount = instructions.size()
        verifyConsistentStackHeight {
            builder.tryBlock()
        }

        // TODO: Optimize AST tree so this check isn't necessary.
        // Might not be able to be done completely in the AST tree, as some statements
        // with empty blocks may have side effects, for example:
        //     "for (let i = 0; i < myFunctionWithSideEffects(); i++) {}"
        // But those checks can be done in the respective statement compilation methods
        if (instructions.size() == prevInsnCount)
            return

        placeLabel(mainEnd)
        verifyConsistentStackHeight {
            finallyBlock?.invoke()
        }
        goto(tryCatchFinallyEnd)

        builder.catchBlocks.forEach {
            val catchStart = makeLabel()
            val catchEnd = makeLabel()

            placeLabel(catchStart)
            verifyConsistentStackHeight {
                it.second()
            }
            placeLabel(catchEnd)
            verifyConsistentStackHeight {
                finallyBlock?.invoke()
            }
            goto(tryCatchFinallyEnd)

            tryCatchBlocks.add(TryCatchBlockNode(
                mainStart,
                mainEnd,
                catchStart,
                coerceType(it.first).internalName,
            ))

            if (lastFinallyBlock != null) {
                tryCatchBlocks.add(TryCatchBlockNode(
                    catchStart,
                    catchEnd,
                    lastFinallyBlock,
                    null
                ))
            }
        }

        if (lastFinallyBlock != null) {
            placeLabel(lastFinallyBlock)
            tryCatchBlocks.add(TryCatchBlockNode(
                mainStart,
                mainEnd,
                lastFinallyBlock,
                null,
            ))

            val exception = astore()
            verifyConsistentStackHeight {
                finallyBlock!!()
            }
            load(exception)
            athrow
        }

        placeLabel(tryCatchFinallyEnd)
    }

    protected fun verifyConsistentStackHeight(block: () -> Unit) {
        val initialStackHeight = stackHeight
        block()
        if (initialStackHeight != stackHeight)
            throw IllegalStateException("verifyConsistentStackHeight failed. Initial: $initialStackHeight, final: $stackHeight")
    }

    protected class TryCatchBuilder {
        lateinit var tryBlock: (() -> Unit)
        val catchBlocks = mutableListOf<Pair<TypeLike, () -> Unit>>()
        var finallyBlock: (() -> Unit)? = null

        fun tryBlock(block: () -> Unit) {
            tryBlock = block
        }

        inline fun <reified T> catchBlock(noinline block: () -> Unit) {
            catchBlocks.add(T::class to block)
        }

        fun finallyBlock(block: () -> Unit) {
            finallyBlock = block
        }
    }

    protected fun MethodAssembly.buildStringHelper(block: BuildStringHelper.() -> Unit) {
        val helper = BuildStringHelper().apply(block)
        construct(StringBuilder::class)
        helper.parts.forEach {
            it.second()
            invokevirtual(StringBuilder::class, "append", StringBuilder::class, it.first)
        }
        invokevirtual(StringBuilder::class, "toString", String::class)
    }

    class BuildStringHelper {
        val parts = mutableListOf<Pair<TypeLike, () -> Unit>>()

        fun append(type: TypeLike = String::class, block: () -> Unit) {
            parts.add(type to block)
        }
    }

    protected fun MethodAssembly.runtime(name: String, returnType: TypeLike, vararg paramTypes: TypeLike) {
        invokestatic(CompilerRuntime::class, name, returnType, *paramTypes)
    }

    // helper JVM bytecodes
    // a, b, c -> a, c
    protected val MethodAssembly.pop_x1: Unit
        get() {
            swap
            pop
        }

    // a, b, c -> c, b, a
    protected val MethodAssembly.swap_x1: Unit
        get() {
            dup_x2
            pop
            swap
        }

    protected val MethodAssembly.pop3: Unit
        get() {
            pop2
            pop
        }

    protected val MethodAssembly.pop4: Unit
        get() {
            pop2
            pop2
        }
}
