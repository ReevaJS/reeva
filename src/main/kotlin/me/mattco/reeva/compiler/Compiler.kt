package me.mattco.reeva.compiler

import codes.som.anthony.koffee.MethodAssembly
import codes.som.anthony.koffee.assembleClass
import codes.som.anthony.koffee.insns.jvm.*
import codes.som.anthony.koffee.insns.sugar.*
import codes.som.anthony.koffee.modifiers.public
import codes.som.anthony.koffee.types.TypeLike
import codes.som.anthony.koffee.types.coerceType
import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.LiteralNode
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.literals.PropertyNameNode
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.interpreter.Completion
import me.mattco.reeva.runtime.Agent
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.contexts.ExecutionContext
import me.mattco.reeva.runtime.environment.DeclarativeEnvRecord
import me.mattco.reeva.runtime.environment.EnvRecord
import me.mattco.reeva.runtime.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.values.JSReference
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSErrorObject
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.objects.PropertyKey
import me.mattco.reeva.runtime.values.primitives.*
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.lang.IllegalArgumentException

class Compiler(private val scriptNode: ScriptNode, fileName: String) {
    private val sanitizedFileName = fileName.replace(Regex("[^\\w]"), "_")
    private val dependencies = mutableListOf<ClassNode>()
    private var needsToPopContext = false
    private val extraMethods = mutableListOf<MethodNode>()

    private val methodStates = mutableListOf<MethodState>()
    data class MethodState(
        val className: String,
        val methodName: String,
        val returnType: TypeLike,
        val isTopLevel: Boolean = false,
    )

    fun compile(): CompilationResult {
        val className = "TopLevel_${sanitizedFileName}_$classCounter"

        val mainClass = assembleClass(public, className, superName = "me/mattco/reeva/compiler/TopLevelScript") {
            method(public, "<init>", void) {
                aload_0
                invokespecial(TopLevelScript::class, "<init>", void)
                _return
            }

            method(public, "run", JSValue::class, ExecutionContext::class) {
                methodStates.add(MethodState(className, "run", JSValue::class, true))
                compileScript(scriptNode)
                pushUndefined
                areturn
                methodStates.removeLast()
            }

            extraMethods.forEach {
                methodStates.add(MethodState(className, it.name, Type.getReturnType(it.desc)))
                node.methods.add(it)
                methodStates.removeLast()
            }
        }

        if (methodStates.isNotEmpty()) {
            throw IllegalStateException("methodStates is not empty!")
        }

        return CompilationResult(mainClass, dependencies)
    }

    data class CompilationResult(val mainClass: ClassNode, val dependencies: List<ClassNode>)

    @ECMAImpl("15.1.11", "GlobalDeclarationInstantiation")
    private fun MethodAssembly.compileScript(script: ScriptNode) {
        aload_1
        getfield(ExecutionContext::class, "realm", Realm::class)
        getfield(Realm::class, "globalEnv", GlobalEnvRecord::class)

        val lexNames = script.lexicallyDeclaredNames()
        val varNames = script.varDeclaredNames()
        lexNames.forEach {
            listOf("hasVarDeclaration", "hasLexicalDeclaration", "hasRestrictedGlobalProperty").forEach { method ->
                dup
                ldc(it)
                invokevirtual(GlobalEnvRecord::class, method, Boolean::class, String::class)
                ifStatement(JumpCondition.True) {
                    shouldThrow("SyntaxError")
                }
            }
        }
        varNames.forEach {
            dup
            ldc(it)
            invokevirtual(GlobalEnvRecord::class, "hasLexicalDeclaration", Boolean::class, String::class)
            ifStatement(JumpCondition.True) {
                shouldThrow("SyntaxError")
            }
        }

        val varDeclarations = script.varScopedDeclarations()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()
        val declaredFunctionNames = mutableListOf<String>()
        varDeclarations.asReversed().forEach {
            if (it is VariableDeclarationNode || it is ForBindingNode || it is BindingIdentifierNode)
                return@forEach
            ecmaAssert(it is FunctionDeclarationNode)
            val boundNames = it.boundNames()
            expect(boundNames.size == 1)
            val functionName = boundNames[0]
            if (functionName !in declaredFunctionNames) {
                dup
                ldc(functionName)
                invokevirtual(GlobalEnvRecord::class, "canDeclareGlobalFunction", Boolean::class, String::class)
                ifStatement(JumpCondition.False) {
                    shouldThrow("TypeError")
                }
                declaredFunctionNames.add(functionName)
                functionsToInitialize.add(0, it)
            }
        }
        var declaredVarNames = mutableListOf<String>()
        varDeclarations.forEach { decl ->
            if (decl is VariableDeclarationNode || decl is ForBindingNode || decl is BindingIdentifierNode) {
                decl.boundNames().forEach { name ->
                    if (name !in declaredFunctionNames) {
                        dup
                        ldc(name)
                        invokevirtual(GlobalEnvRecord::class, "canDeclareGlobalVar", Boolean::class, String::class)
                        ifStatement(JumpCondition.False) {
                            shouldThrow("TypeError")
                        }
                        if (name !in declaredVarNames)
                            declaredVarNames.add(name)
                    }
                }
            }
        }
        val lexDeclarations = script.lexicallyScopedDeclarations()
        lexDeclarations.forEach { decl ->
            decl.boundNames().forEach { name ->
                dup
                ldc(name)
                if (decl.isConstantDeclaration()) {
                    ldc(true)
                    invokevirtual(GlobalEnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
                } else {
                    ldc(false)
                    invokevirtual(GlobalEnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                }
            }
        }
        functionsToInitialize.forEach { func ->
            val compiledFunc = instantiateFunctionObject(func)
            dependencies.add(compiledFunc)

            // env
            dup
            dup
            // env, env, env
            new(compiledFunc.name)
            // env, env, env, func
            dup_x1
            // env, env, func, env, func
            swap
            // env, env, func, func, env
            pushRealm
            // env, env, func, func, env, realm
            swap
            // env, env, func, func, realm, env
            invokespecial(compiledFunc.name, "<init>", void, Realm::class, EnvRecord::class)
            // env, env, func

            ldc(func.identifier?.identifierName ?: "default")
            // env, env, func, string
            swap
            // env, env, string, func
            ldc(false)
            // env, env, string, func, boolean
            invokevirtual(GlobalEnvRecord::class, "createGlobalFunctionBinding", void, String::class, JSFunction::class, Boolean::class)
        }
        declaredVarNames.forEach {
            dup
            ldc(it)
            ldc(false)
            invokevirtual(GlobalEnvRecord::class, "createGlobalVarBinding", void, String::class, Boolean::class)
        }

        compileStatementList(script.statementList)
    }

    private fun MethodAssembly.compileStatementList(statementList: StatementListNode) {
        statementList.statements.forEach {
            compileStatement(it as StatementNode)
        }
    }

    private fun MethodAssembly.compileStatement(statement: StatementNode) {
        when (statement) {
            is BlockStatementNode -> compileBlockStatement(statement)
            is VariableStatementNode -> compileVariableStatement(statement)
            is EmptyStatementNode -> {
            }
            is ExpressionStatementNode -> compileExpressionStatement(statement)
            is IfStatementNode -> compileIfStatement(statement)
            is BreakableStatement -> compileBreakableStatement(statement)
            is LabelledStatementNode -> compileLabelledStatement(statement)
            is LexicalDeclarationNode -> compileLexicalDeclaration(statement)
            is FunctionDeclarationNode -> compileFunctionDeclaration(statement)
            is ReturnStatementNode -> compileReturnStatement(statement)
            is ThrowStatementNode -> compileThrowStatement(statement)
            is TryStatementNode -> compileTryStatementNode(statement)
            is ForStatementNode -> compileForStatementNode(statement)
            is BreakStatementNode -> compileBreakStatement(statement)
            else -> TODO()
        }
    }

    private fun MethodAssembly.compileBreakStatement(breakStatementNode: BreakStatementNode) {
        if (breakStatementNode.label != null)
            TODO()

        construct(Completion::class, Completion.Type::class, JSValue::class) {
            getstatic(Completion.Type::class, "Break", Completion.Type::class)
            pushUndefined
        }
        areturn
    }

    private fun MethodAssembly.compileForStatementNode(forStatementNode: ForStatementNode) {
        val initializer = forStatementNode.initializer
        val condition = forStatementNode.condition
        val incrementer = forStatementNode.incrementer
        val body = forStatementNode.body
        var perIterationBindings = listOf<String>()

        fun createPerIterationEnvironment() {
            if (perIterationBindings.isEmpty())
                return

            pushLexicalEnv
            // lastIterationEnv
            dup
            // lastIterationEnv, lastIterationEnv
            getfield(EnvRecord::class, "outerEnv", EnvRecord::class)
            // ecmaAssert(the last result is not null)

            invokestatic(DeclarativeEnvRecord::class, "create", DeclarativeEnvRecord::class, EnvRecord::class)
            // lastIterationEnv, thisIterationEnv

            perIterationBindings.forEach { binding ->
                // lastIterationEnv, thisIterationEnv
                dup
                // lastIterationEnv, thisIterationEnv, thisIterationEnv
                ldc(binding)
                ldc(false)
                invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)

                // lastIterationEnv, thisIterationEnv
                swap
                // thisIterationEnv, lastIterationEnv
                dup2
                // thisIterationEnv, lastIterationEnv, thisIterationEnv, lastIterationEnv
                ldc(binding)
                ldc(true)
                // thisIterationEnv, lastIterationEnv, thisIterationEnv, lastIterationEnv, binding, true
                invokevirtual(EnvRecord::class, "getBindingValue", JSValue::class, String::class, Boolean::class)
                // thisIterationEnv, lastIterationEnv, thisIterationEnv, lastValue
                ldc(binding)
                // thisIterationEnv, lastIterationEnv, thisIterationEnv, lastValue, binding
                swap
                // thisIterationEnv, lastIterationEnv, thisIterationEnv, binding, lastValue
                invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
                // thisIterationEnv, lastIterationEnv
                swap
                // lastIterationEnv, thisIterationEnv
            }

            // lastIterationEnv, thisIterationEnv
            pushRunningContext
            // lastIterationEnv, thisIterationEnv, context
            swap
            // lastIterationEnv, context, thisIterationEnv
            putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
            // lastIterationEnv
            pop
        }

        val name = "for_block_${Agent.objectCount++}"
        addExtraMethodToCurrentClass(name) {
            compileStatement(body)
            createPerIterationEnvironment()
        }

        when (initializer) {
            is ExpressionNode -> compileExpression(initializer)
            is VariableStatementNode -> compileVariableStatement(initializer)
            is LexicalDeclarationNode -> {
                pushLexicalEnv
                // oldEnv
                dup
                // oldEnv, oldEnv
                invokestatic(DeclarativeEnvRecord::class, "create", DeclarativeEnvRecord::class, EnvRecord::class)
                // oldEnv, loopEnv

                val isConst = initializer.isConstantDeclaration()
                val boundNames = initializer.boundNames()
                boundNames.forEach { name ->
                    dup
                    ldc(name)
                    if (isConst) {
                        ldc(true)
                        invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
                    } else {
                        ldc(false)
                        invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                    }
                }

                // oldEnv, loopEnv
                pushRunningContext
                // oldEnv, loopEnv, context
                swap
                // oldEnv, context, loopEnv
                putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
                // oldEnv

                compileLexicalDeclaration(initializer)

                if (!isConst)
                    perIterationBindings = boundNames
            }
            else -> unreachable()
        }

        createPerIterationEnvironment()

        val start = L[Agent.objectCount++]
        val end = L[Agent.objectCount++]

        +start

        if (condition != null) {
            compileExpression(condition)
            operation("getValue", JSValue::class, JSValue::class)
            operation("toBoolean", JSBoolean::class, JSValue::class)
            pushFalse
            jump(JumpCondition.RefEqual, end)
        }

        invokeExtraMethod(name)
        // TODO: Check label
        getfield(Completion::class, "type", Completion.Type::class)
        dup
        // type, type
        getstatic(Completion.Type::class, "Return", Completion.Type::class)
        // type, type, Type.Return
        ifStatement(JumpCondition.RefEqual) {
            // type
            pop
            goto(end)
        }
        // type
        getstatic(Completion.Type::class, "Break", Completion.Type::class)
        ifStatement(JumpCondition.RefEqual) {
            goto(end)
        }

        if (incrementer != null) {
            compileExpression(incrementer)
            operation("getValue", JSValue::class, JSValue::class)
            pop
        }

        goto(start)
        +end

        if (initializer is LexicalDeclarationNode) {
            // oldEnv
            pushRunningContext
            swap
            // context, oldEnv
            putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
        }
    }

    private fun MethodAssembly.compileTryStatementNode(tryStatementNode: TryStatementNode) {
        /**
         * When we enter a try block, we want to "intercept" all returns from the function
         * and investigate whether or not they are because an error was thrown (i.e. a call
         * to checkError), or if they are an actual return statement. The easiest way to do
         * this is to make a new function to enclose any return statement. The contents of
         * the try-block become their own function. Note that the catch block is still
         * evaluated in the current function, as there is no need to intercept return
         * statements inside of it
         */

        val name = "try_block_${Agent.objectCount++}"
        addExtraMethodToCurrentClass(name) {
            compileBlock(tryStatementNode.tryBlock)
        }

        invokeExtraMethod(name)

        invokestatic(Agent::class, "hasError", Boolean::class)
        ifStatement(JumpCondition.True) {
            pushRunningContext
            dup
            getfield(ExecutionContext::class, "error", JSErrorObject::class)
            // context, errorObj
            swap
            // errorObj, context
            aconst_null
            // errorObj, context, null
            putfield(ExecutionContext::class, "error", JSErrorObject::class)
            // errorObj

            // catch block
            val catchNode = tryStatementNode.catchNode
            if (catchNode.catchParameter == null) {
                compileBlock(catchNode.block)
            } else {
                pushLexicalEnv
                pushLexicalEnv
                // errorObj, oldEnv, oldEnv
                invokestatic(DeclarativeEnvRecord::class, "create", DeclarativeEnvRecord::class, EnvRecord::class)
                // errorObj, oldEnv, catchEnv
                catchNode.catchParameter.boundNames().forEach { name ->
                    dup
                    ldc(name)
                    ldc(false)
                    invokevirtual(DeclarativeEnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                }

                // errorObj, oldEnv, catchEnv
                dup_x1
                // errorObj, catchEnv, oldEnv, catchEnv

                pushRunningContext
                // errorObj, catchEnv, oldEnv, catchEnv, context
                swap
                // errorObj, catchEnv, oldEnv, context, catchEnv
                putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
                // errorObj, catchEnv, oldEnv

                dup2_x1
                // catchEnv, oldEnv, errorObj, catchEnv, oldEnv
                pop
                // catchEnv, oldEnv, errorObj, catchEnv
                swap
                // catchEnv, oldEnv, catchEnv, errorObj
                initializeBoundName(catchNode.catchParameter.identifierName)
                // catchEnv, oldEnv, catchEnv
                pop
                // catchEnv, oldEnv

                // custom checkError with some additional behavior
                invokestatic(Agent::class, "hasError", Boolean::class)
                ifStatement(JumpCondition.True) {
                    pushRunningContext
                    // catchEnv, oldEnv, context
                    swap
                    // catchEnv, context, oldEnv
                    putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
                    if (needsToPopContext) {
                        invokestatic(Agent::class, "popContext", void)
                    }
                    when (coerceType(methodStates.last().returnType)) {
                        coerceType(Completion::class) -> {
                            construct(Completion::class, Completion.Type::class, JSValue::class) {
                                getstatic(Completion.Type::class, "Return", Completion.Type::class)
                                pushRunningContext
                                getfield(ExecutionContext::class, "error", JSErrorObject::class)
                            }
                        }
                        else -> {
                            pushRunningContext
                            getfield(ExecutionContext::class, "error", JSErrorObject::class)
                        }
                    }
                    areturn
                }

                // catchEnv, oldEnv
                compileBlock(catchNode.block)
                // catchEnv, oldEnv
                pushRunningContext
                // catchEnv, oldEnv, context
                swap
                // catchEnv, context, oldEnv
                putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
                // catchEnv
                pop
            }
        }
    }

    private fun MethodAssembly.compileThrowStatement(throwStatementNode: ThrowStatementNode) {
        compileExpression(throwStatementNode.expr)
        // expr
        operation("getValue", JSValue::class, JSValue::class)
        // value
//        dup
        // value, value
        pushRunningContext
        // value, value, context
        swap
        // value, context, value
        // TODO: Allow throwing arbitrary values
        checkcast<JSErrorObject>()
        // value, context, JSErrorObject
        putfield(ExecutionContext::class, "error", JSErrorObject::class)
        // value
        checkError
    }

    private fun MethodAssembly.compileReturnStatement(returnStatementNode: ReturnStatementNode) {
        getstatic(Completion.Type::class, "Return", Completion.Type::class)
        if (returnStatementNode.node == null) {
            pushUndefined
        } else {
            compileExpression(returnStatementNode.node)
        }

        // Type, expr
        new<Completion>()
        // Type, expr, record
        dup_x2
        // record, Type, expr, record
        dup_x2
        // record, record, Type, expr, record
        pop
        // record, record, Type, expr
        invokespecial(Completion::class, "<init>", void, Completion.Type::class, JSValue::class)

        areturn
    }

    private fun MethodAssembly.compileFunctionDeclaration(functionDeclarationNode: FunctionDeclarationNode) {
        // TODO
    }

    private fun MethodAssembly.compileBlock(block: BlockNode) {
        // TODO: Scopes
        if (block.statements != null) {
            pushLexicalEnv
            pushLexicalEnv
            invokestatic(DeclarativeEnvRecord::class, "create", DeclarativeEnvRecord::class, EnvRecord::class)
            // oldEnv, blockEnv
            compileBlockDeclarationInstantiation(block.statements)
            // oldEnv, blockEnv
            pushRunningContext
            // oldEnv, blockEnv, context
            // oldEnv, context, blockEnv
            swap
            putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
            compileStatementList(block.statements)
            pushRunningContext
            swap
            putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
        }
    }

    private fun MethodAssembly.compileBlockDeclarationInstantiation(node: StatementListNode) {
        node.lexicallyScopedDeclarations().forEach { decl ->
            decl.boundNames().forEach { name ->
                dup
                ldc(name)
                if (decl.isConstantDeclaration()) {
                    ldc(true)
                    invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
                } else {
                    ldc(false)
                    invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                }
            }
            if (decl is FunctionDeclarationNode) {
                val compiledFunc = instantiateFunctionObject(decl)
                dependencies.add(compiledFunc)

                dup
                val boundNames = decl.boundNames()
                expect(boundNames.size == 1)
                ldc(boundNames[0])
                construct(compiledFunc.name, Realm::class) {
                    pushRealm
                }
                invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
            }
        }
    }

    private fun MethodAssembly.compileBlockStatement(blockStatement: BlockStatementNode) {
        compileBlock(blockStatement.block)
    }

    private fun MethodAssembly.compileVariableStatement(variableStatement: VariableStatementNode) {
        compileVariableDeclarationList(variableStatement.declarations)
    }

    private fun MethodAssembly.compileVariableDeclarationList(list: VariableDeclarationList) {
        list.declarations.forEach {
            compileVariableDeclarationNode(it)
        }
    }

    private fun MethodAssembly.compileVariableDeclarationNode(node: VariableDeclarationNode) {
        if (node.initializer != null) {
            val id = node.identifier.identifierName
            ldc(id)
            aconst_null
            operation("resolveBinding", JSReference::class, String::class, EnvRecord::class)
            if (isAnonymousFunctionDefinition(node)) {
                TODO()
            } else {
                compileInitializer(node.initializer)
                operation("getValue", JSValue::class, JSValue::class)
            }
            operation("putValue", void, JSValue::class, JSValue::class)
        } else {
            // Do nothing, this is handled by SS
        }
    }

    @ECMAImpl("14.1.12")
    private fun isAnonymousFunctionDefinition(node: ASTNode): Boolean {
        if (!node.isFunctionDefinition())
            return false
        return !node.hasName()
    }

    private fun MethodAssembly.compileInitializer(initializerNode: InitializerNode) {
        compileExpression(initializerNode.node)
    }

    private fun MethodAssembly.compileEmptyStatement(emptyStatement: EmptyStatementNode) {}

    private fun MethodAssembly.compileIfStatement(ifStatement: IfStatementNode) {
        compileExpression(ifStatement.condition)
        operation("getValue", JSValue::class, JSValue::class)
        operation("toBoolean", JSBoolean::class, JSValue::class)
        pushTrue
        if (ifStatement.falseBlock == null) {
            ifStatement(JumpCondition.RefEqual) {
                compileStatement(ifStatement.trueBlock)
            }
        } else {
            ifElseStatement(JumpCondition.RefEqual) {
                ifBlock {
                    compileStatement(ifStatement.trueBlock)
                }

                elseBlock {
                    compileStatement(ifStatement.falseBlock)
                }
            }
        }
    }

    private fun MethodAssembly.compileBreakableStatement(breakableStatement: BreakableStatement) {
        TODO()
    }

    private fun MethodAssembly.compileDoWhileStatement(doWhileStatement: DoWhileStatementNode) {
        TODO()
    }

    private fun MethodAssembly.compileWhileStatement(whileStatement: WhileStatementNode) {
        TODO()
    }

    private fun MethodAssembly.compileLabelledStatement(labelledStatement: LabelledStatementNode) {
        TODO()
    }

    private fun MethodAssembly.compileHoistableDeclaration(hoistableDeclarationNode: HoistableDeclarationNode) {
        TODO()
    }

    @ECMAImpl(section = "13.3.1.4")
    private fun MethodAssembly.compileLexicalDeclaration(lexicalDeclarationNode: LexicalDeclarationNode) {
        lexicalDeclarationNode.bindingList.lexicalBindings.forEach {
            expect(it.initializer != null)

            ldc(it.identifier.identifierName)
            aconst_null
            operation("resolveBinding", JSReference::class, String::class, EnvRecord::class)
            if (isAnonymousFunctionDefinition(lexicalDeclarationNode)) {
                TODO()
            } else {
                compileExpression(it.initializer.node)
                operation("getValue", JSValue::class, JSValue::class)
            }
            operation("initializeReferencedBinding", void, JSReference::class, JSValue::class)
        }
    }

    private fun MethodAssembly.compileBindingList(bindingList: BindingListNode) {
        TODO()
    }

    private fun MethodAssembly.compileLexicalBinding(lexicalBindingNode: LexicalBindingNode) {
        TODO()
    }

    private fun MethodAssembly.compileExpressionStatement(expressionStatementNode: ExpressionStatementNode) {
        compileExpression(expressionStatementNode.node)
        operation("getValue", JSValue::class, JSValue::class)
        pop
    }

    private fun MethodAssembly.compileExpression(expressionNode: ExpressionNode) {
        when (expressionNode) {
            ThisNode -> compileThis()
            is CommaExpressionNode -> compileCommaExpressionNode(expressionNode)
            is IdentifierReferenceNode -> compileIdentifierReference(expressionNode)
            is LiteralNode -> compileLiteral(expressionNode)
            is NewExpressionNode -> compileNewExpression(expressionNode)
            is CallExpressionNode -> compileCallExpression(expressionNode)
            is ObjectLiteralNode -> compileObjectLiteral(expressionNode)
            is ArrayLiteralNode -> compileArrayLiteral(expressionNode)
            is MemberExpressionNode -> compileMemberExpression(expressionNode)
            is OptionalExpressionNode -> compileOptionalExpression(expressionNode)
            is AssignmentExpressionNode -> compileAssignmentExpression(expressionNode)
            is ConditionalExpressionNode -> compileConditionalExpression(expressionNode)
            is CoalesceExpressionNode -> compileCoalesceExpression(expressionNode)
            is LogicalORExpressionNode -> compileLogicalORExpression(expressionNode)
            is LogicalANDExpressionNode -> compileLogicalANDExpression(expressionNode)
            is BitwiseORExpressionNode -> compileBitwiseORExpression(expressionNode)
            is BitwiseXORExpressionNode -> compileBitwiseXORExpression(expressionNode)
            is BitwiseANDExpressionNode -> compileBitwiseANDExpression(expressionNode)
            is EqualityExpressionNode -> compileEqualityExpression(expressionNode)
            is RelationalExpressionNode -> compileRelationalExpression(expressionNode)
            is ShiftExpressionNode -> compileShiftExpression(expressionNode)
            is AdditiveExpressionNode -> compileAdditiveExpression(expressionNode)
            is MultiplicativeExpressionNode -> compileMultiplicationExpression(expressionNode)
            is ExponentiationExpressionNode -> compileExponentiationExpression(expressionNode)
            is UnaryExpressionNode -> compileUnaryExpression(expressionNode)
            is UpdateExpressionNode -> compileUpdateExpression(expressionNode)
            else -> unreachable()
        }
    }

    @ECMAImpl
    private fun MethodAssembly.compileObjectLiteral(objectLiteralNode: ObjectLiteralNode) {
        pushRealm
        invokestatic(JSObject::class, "create", JSObject::class, Realm::class)

        if (objectLiteralNode.list != null) {
            objectLiteralNode.list.properties.forEach { property ->
                dup
                compilePropertyDefinition(property, true)
            }
        }
    }

    @ECMAImpl(section = "12.2.6.8")
    @ECMAImpl(section = "14.3.8")
    private fun MethodAssembly.compilePropertyDefinition(property: PropertyDefinitionNode, enumerable: Boolean) {
        when (property.type) {
            PropertyDefinitionNode.Type.KeyValue -> {
                expect(property.first is PropertyNameNode)
                expect(property.second != null)
                compilePropertyName(property.first)
                if (isAnonymousFunctionDefinition(property.second)) {
                    TODO()
                } else {
                    compileExpression(property.second)
                    operation("getValue", JSValue::class, JSValue::class)
                }
                ecmaAssert(enumerable)
                operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                pop
            }
            PropertyDefinitionNode.Type.Shorthand -> {
                expect(property.first is IdentifierReferenceNode)
                ldc(property.first.identifierName)
                wrapObjectInValue
                compileExpression(property.first)
                operation("getValue", JSValue::class, JSValue::class)
                expect(enumerable)
                operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                pop
            }
            PropertyDefinitionNode.Type.Method -> TODO()
            PropertyDefinitionNode.Type.Spread -> TODO()
        }
    }

    private fun MethodAssembly.compilePropertyName(propertyNameNode: PropertyNameNode) {
        val expr = propertyNameNode.expr

        when {
            propertyNameNode.isComputed -> compileExpression(expr)
            expr is IdentifierNode -> {
                ldc(expr.identifierName)
                wrapObjectInValue
            }
            expr is StringLiteralNode -> {
                ldc(expr.value)
                wrapDoubleInValue
            }
            expr is NumericLiteralNode -> {
                ldc(expr.value)
                wrapDoubleInValue
            }
            else -> unreachable()
        }
    }

    private fun MethodAssembly.compileArrayLiteral(arrayLiteralNode: ArrayLiteralNode) {
        ldc(arrayLiteralNode.elements.size)
        operation("arrayCreate", JSObject::class, Int::class)
        arrayLiteralNode.elements.forEachIndexed { index, element ->
            dup
            construct(JSNumber::class, Int::class) {
                ldc(index)
            }
            if (element.expression == null) {
                // TODO: Spec doesn't specify this step, is it alright to do this?
                pushEmpty
            } else {
                compileExpression(element.expression)
                operation("getValue", JSValue::class, JSValue::class)
            }
            operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
            pop
        }
    }

    private fun MethodAssembly.compileCommaExpressionNode(commaExpressionNode: CommaExpressionNode) {
        commaExpressionNode.expressions.forEachIndexed { index, node ->
            compileExpression(node)
            if (index != commaExpressionNode.expressions.lastIndex)
                pop
        }
    }

    private fun MethodAssembly.compileBindingIdentifier(bindingIdentifierNode: BindingIdentifierNode) {
        TODO()
    }

    private fun MethodAssembly.compileLabelIdentifier(labelIdentifierNode: LabelIdentifierNode) {
        TODO()
    }

    private fun MethodAssembly.compileIdentifier(identifierNode: IdentifierNode) {
        TODO()
    }

    private fun MethodAssembly.compileParenthesizedExpression(parenthesizedExpressionNode: ParenthesizedExpressionNode) {
        compileExpression(parenthesizedExpressionNode.target)
    }

    private fun MethodAssembly.compileLiteral(literalNode: LiteralNode) {
        when (literalNode) {
            is NullNode -> pushNull
            is TrueNode -> pushTrue
            is FalseNode -> pushFalse
            is StringLiteralNode -> {
                construct(JSString::class, String::class) {
                    ldc(literalNode.value)
                }
            }
            is NumericLiteralNode -> {
                construct(JSNumber::class, Double::class) {
                    ldc(literalNode.value)
                }
            }
            else -> unreachable()
        }
    }

    @ECMAImpl(section = "12.3.2.1")
    private fun MethodAssembly.compileMemberExpression(memberExpressionNode: MemberExpressionNode) {
        compileExpression(memberExpressionNode.lhs)
        operation("getValue", JSValue::class, JSValue::class)
        // TODO: Strict mode
        ldc(false)
        when (memberExpressionNode.type) {
            MemberExpressionNode.Type.NonComputed -> {
                expect(memberExpressionNode.rhs is IdentifierNode)
                ldc(memberExpressionNode.rhs.identifierName)
                swap
                operation(
                    "evaluatePropertyAccessWithIdentifierKey",
                    JSValue::class,
                    JSValue::class,
                    String::class,
                    Boolean::class
                )
            }
            MemberExpressionNode.Type.Computed -> {
                compileExpression(memberExpressionNode.rhs)
                swap
                operation(
                    "evaluatePropertyAccessWithExpressionKey",
                    JSValue::class,
                    JSValue::class,
                    JSValue::class,
                    Boolean::class
                )
            }
            else -> TODO()
        }

    }

    private fun MethodAssembly.compileSuperProperty(superPropertyNode: SuperPropertyNode) {
        TODO()
    }

    private fun MethodAssembly.compileMetaProperty(metaPropertyNode: MetaPropertyNode) {
        TODO()
    }

    private fun MethodAssembly.compileNewTarget() {
        operation("getNewTarget", JSValue::class)
    }

    private fun MethodAssembly.compileImportMeta() {
        TODO()
    }

    private fun MethodAssembly.compileNewExpression(newExpressionNode: NewExpressionNode) {
        compileExpression(newExpressionNode.target)
        val arguments = newExpressionNode.arguments
        if (arguments == null) {
            iconst_0
            anewarray<JSValue>()
        } else {
            compileArguments(arguments)
        }
        operation("evaluateNew", JSValue::class, JSValue::class, Array<JSValue>::class)
    }

    private fun MethodAssembly.compileCallExpression(callExpressionNode: CallExpressionNode) {
        compileExpression(callExpressionNode.target)
        dup
        operation("getValue", JSValue::class, JSValue::class)
        swap
        compileArguments(callExpressionNode.arguments)
        // TODO: Tail calls
        ldc(false)
        operation("evaluateCall", JSValue::class, JSValue::class, JSValue::class, List::class, Boolean::class)
    }

    private fun MethodAssembly.compileArguments(argumentsNode: ArgumentsNode) {
        val arguments = argumentsNode.arguments
        construct(java.util.ArrayList::class)
        arguments.forEachIndexed { index, argument ->
            if (argument.isSpread)
                TODO()
            dup
            compileExpression(argument.expression)
            operation("getValue", JSValue::class, JSValue::class)
            invokevirtual(ArrayList::class, "add", Boolean::class, Object::class)
            pop
        }
    }

    private fun MethodAssembly.compileOptionalExpression(optionalExpressionNode: OptionalExpressionNode) {
        TODO()
    }

    private fun MethodAssembly.compileSuperCall(superCallNode: SuperCallNode) {
        TODO()
    }

    private fun MethodAssembly.compileImportCall(importCallNode: ImportCallNode) {
        TODO()
    }

    private fun MethodAssembly.compileUpdateExpression(updateExpressionNode: UpdateExpressionNode) {
        compileExpression(updateExpressionNode.target)
        // lhs
        dup
        // lhs, lhs
        operation("getValue", JSValue::class, JSValue::class)
        operation("toNumeric", JSValue::class, JSValue::class)
        // lhs, oldValue

        // TODO: Don't do this, it throws a Java exception
        dup
        operation("checkNotBigInt", void, JSValue::class)

        // lhs, oldValue
        dup_x1
        // oldValue, lhs, oldValue
        construct(JSNumber::class, Double::class) {
            ldc(1.0)
        }
        // oldValue, lhs, oldValue, 1.0
        if (updateExpressionNode.isIncrement) {
            operation("numericAdd", JSValue::class, JSValue::class, JSValue::class)
        } else {
            operation("numericSubtract", JSValue::class, JSValue::class, JSValue::class)
        }
        // oldValue, lhs, newValue
        if (updateExpressionNode.isPostfix) {
            operation("putValue", void, JSValue::class, JSValue::class)
            // oldValue
        } else {
            dup_x1
            // oldValue, newValue, lhs, newValue
            operation("putValue", void, JSValue::class, JSValue::class)
            // oldValue, newValue
            swap
            // newValue, oldValue
            pop
            // newValue
        }
    }

    private fun MethodAssembly.compileUnaryExpression(unaryExpressionNode: UnaryExpressionNode) {
        compileExpression(unaryExpressionNode.node)
        when (unaryExpressionNode.op) {
            UnaryExpressionNode.Operator.Delete -> {
                operation("deleteOperator", JSValue::class, JSValue::class)
            }
            UnaryExpressionNode.Operator.Void -> {
                operation("getValue", JSValue::class, JSValue::class)
                pop
                pushUndefined
            }
            UnaryExpressionNode.Operator.Typeof -> {
                operation("typeofOperator", JSValue::class, JSValue::class)
            }
            UnaryExpressionNode.Operator.Plus -> {
                operation("getValue", JSValue::class, JSValue::class)
                operation("toNumber", JSValue::class, JSValue::class)
            }
            UnaryExpressionNode.Operator.Minus -> {
                operation("getValue", JSValue::class, JSValue::class)
                operation("toNumeric", JSValue::class, JSValue::class)
                dup
                // TODO
                operation("checkNotBigInt", void, JSValue::class)
                operation("numericUnaryMinus", JSValue::class, JSValue::class)
            }
            UnaryExpressionNode.Operator.BitwiseNot -> {
                operation("getValue", JSValue::class, JSValue::class)
                operation("toNumeric", JSValue::class, JSValue::class)
                dup
                // TODO
                operation("checkNotBigInt", void, JSValue::class)
                operation("numericBitwiseNOT", JSValue::class, JSValue::class)
            }
            UnaryExpressionNode.Operator.Not -> {
                operation("getValue", JSValue::class, JSValue::class)
                operation("toBoolean", JSValue::class, JSValue::class)
                pushTrue
                ifElseStatement(JumpCondition.RefEqual) {
                    ifBlock {
                        pushFalse
                    }

                    elseBlock {
                        pushTrue
                    }
                }
            }
        }
    }

    private fun MethodAssembly.compileExponentiationExpression(exponentiationExpressionNode: ExponentiationExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            exponentiationExpressionNode.lhs,
            exponentiationExpressionNode.rhs,
            "**"
        )
    }

    private fun MethodAssembly.compileMultiplicationExpression(multiplicativeExpressionNode: MultiplicativeExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            multiplicativeExpressionNode.lhs,
            multiplicativeExpressionNode.rhs,
            multiplicativeExpressionNode.op.symbol
        )
    }

    private fun MethodAssembly.compileAdditiveExpression(additiveExpressionNode: AdditiveExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            additiveExpressionNode.lhs,
            additiveExpressionNode.rhs,
            if (additiveExpressionNode.isSubtraction) "-" else "+"
        )
    }

    private fun MethodAssembly.compileShiftExpression(shiftExpressionNode: ShiftExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            shiftExpressionNode.lhs,
            shiftExpressionNode.rhs,
            shiftExpressionNode.op.symbol
        )
    }

    private fun MethodAssembly.compileBitwiseANDExpression(bitwiseANDExpressionNode: BitwiseANDExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            bitwiseANDExpressionNode.lhs,
            bitwiseANDExpressionNode.rhs,
            "&"
        )
    }

    private fun MethodAssembly.compileBitwiseXORExpression(bitwiseXORExpressionNode: BitwiseXORExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            bitwiseXORExpressionNode.lhs,
            bitwiseXORExpressionNode.rhs,
            "^"
        )
    }

    private fun MethodAssembly.compileBitwiseORExpression(bitwiseORExpressionNode: BitwiseORExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            bitwiseORExpressionNode.lhs,
            bitwiseORExpressionNode.rhs,
            "|"
        )
    }

    private fun MethodAssembly.compileRelationalExpression(relationalExpressionNode: RelationalExpressionNode) {
        compileExpression(relationalExpressionNode.lhs)
        operation("getValue", JSValue::class, JSValue::class)
        // lval
        compileExpression(relationalExpressionNode.rhs)
        operation("getValue", JSValue::class, JSValue::class)
        // lval, rval

        when (relationalExpressionNode.op) {
            RelationalExpressionNode.Operator.LessThan -> {
                ldc(true)
                operation("abstractRelationalComparison", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                dup
                pushUndefined
                // r, r, undefined
                ifStatement(JumpCondition.RefEqual) {
                    // r
                    pop
                    pushFalse
                }
            }
            RelationalExpressionNode.Operator.GreaterThan -> {
                swap
                ldc(false)
                operation("abstractRelationalComparison", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                dup
                pushUndefined
                // r, r, undefined
                ifStatement(JumpCondition.RefEqual) {
                    // r
                    pop
                    pushFalse
                }
            }
            RelationalExpressionNode.Operator.LessThanEquals -> {
                swap
                ldc(false)
                operation("abstractRelationalComparison", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                dup
                pushFalse
                // r, r, false
                ifStatement(JumpCondition.RefEqual) {
                    // r
                    pop
                    pushTrue
                }
            }
            RelationalExpressionNode.Operator.GreaterThanEquals -> {
                ldc(true)
                operation("abstractRelationalComparison", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                dup
                pushFalse
                // r, r, false
                ifStatement(JumpCondition.RefEqual) {
                    // r
                    pop
                    pushTrue
                }
            }
            RelationalExpressionNode.Operator.Instanceof -> {
                operation("instanceofOperator", JSValue::class, JSValue::class, JSValue::class)
            }
            RelationalExpressionNode.Operator.In -> {
                // lval, rval
                dup
                // lval, rval, rval
                instanceof<JSObject>()
                // lval, rval, boolean
                ifStatement(JumpCondition.False) {
                    pushRealm
                    ldc("right-hand side of 'in' operator must be an object")
                    invokestatic(JSTypeErrorObject::class, "create", JSTypeErrorObject::class, Realm::class, String::class)
                    invokestatic(Agent::class, "throwError", void, JSErrorObject::class)
                    returnFromFunction
                }
                // lval, rval
                operation("toPropertyKey", PropertyKey::class, JSValue::class)
                // lval, key
                invokevirtual(JSObject::class, "hasProperty", Boolean::class, PropertyKey::class)
                wrapBooleanInValue
            }
        }
    }

    private fun MethodAssembly.compileEqualityExpression(equalityExpressionNode: EqualityExpressionNode) {
        compileExpression(equalityExpressionNode.lhs)
        operation("getValue", JSValue::class, JSValue::class)
        compileExpression(equalityExpressionNode.rhs)
        operation("getValue", JSValue::class, JSValue::class)
        when (equalityExpressionNode.op) {
            EqualityExpressionNode.Operator.StrictEquality, EqualityExpressionNode.Operator.StrictInequality -> {
                operation("strictEqualityComparison", JSValue::class, JSValue::class, JSValue::class)
            }
            else -> {
                operation("abstractEqualityComparison", JSValue::class, JSValue::class, JSValue::class)
            }
        }
        when (equalityExpressionNode.op) {
            EqualityExpressionNode.Operator.NonstrictEquality, EqualityExpressionNode.Operator.NonstrictInequality -> {
                dup
                pushTrue
                ifStatement(JumpCondition.RefEqual) {
                    pop
                    pushFalse
                }
            }
            else -> {}
        }
    }

    private fun MethodAssembly.compileLogicalANDExpression(logicalANDExpressionNode: LogicalANDExpressionNode) {
        compileExpression(logicalANDExpressionNode.lhs)
        operation("getValue", JSValue::class, JSValue::class)
        dup
        // lval, lval
        operation("toBoolean", JSValue::class, JSValue::class)
        // lval, lbool
        pushTrue
        ifStatement(JumpCondition.RefEqual) {
            // lval
            pop
            compileExpression(logicalANDExpressionNode.rhs)
            operation("getValue", JSValue::class, JSValue::class)
        }
    }

    private fun MethodAssembly.compileLogicalORExpression(logicalORExpressionNode: LogicalORExpressionNode) {
        compileExpression(logicalORExpressionNode.lhs)
        operation("getValue", JSValue::class, JSValue::class)
        dup
        // lval, lval
        operation("toBoolean", JSBoolean::class, JSValue::class)
        // lval, lbool
        pushFalse
        ifStatement(JumpCondition.RefEqual) {
            // lval
            pop
            compileExpression(logicalORExpressionNode.rhs)
            operation("getValue", JSValue::class, JSValue::class)
        }
    }

    private fun MethodAssembly.compileCoalesceExpression(coalesceExpressionNode: CoalesceExpressionNode) {
        compileExpression(coalesceExpressionNode.lhs)
        operation("getValue", JSValue::class, JSValue::class)
        dup
        // lval, lval
        operation("isNullish", Boolean::class, JSValue::class)
        // lval, bool
        ifStatement(JumpCondition.True) {
            // lval
            pop
            compileExpression(coalesceExpressionNode.rhs)
            operation("getValue", JSValue::class, JSValue::class)
        }
    }

    private fun MethodAssembly.compileConditionalExpression(conditionalExpressionNode: ConditionalExpressionNode) {
        TODO()
    }

    @ECMAImpl(section = "12.15.4")
    private fun MethodAssembly.compileAssignmentExpression(assignmentExpressionNode: AssignmentExpressionNode) {
        expect(assignmentExpressionNode.lhs is LeftHandSideExpressionNode)

        if (assignmentExpressionNode.lhs is ObjectLiteralNode || assignmentExpressionNode.lhs is ArrayLiteralNode)
            TODO()

        compileExpression(assignmentExpressionNode.lhs)

        if (assignmentExpressionNode.op == AssignmentExpressionNode.Operator.Equals) {
            compileExpression(assignmentExpressionNode.rhs)
            operation("getValue", JSValue::class, JSValue::class)
            dup_x1
            operation("putValue", void, JSValue::class, JSValue::class)
        } else {
            TODO()
        }
    }

    private fun MethodAssembly.evaluateStringOrNumericBinaryExpression(lhs: ExpressionNode, rhs: ExpressionNode, op: String) {
        compileExpression(lhs)
        operation("getValue", JSValue::class, JSValue::class)
        compileExpression(rhs)
        operation("getValue", JSValue::class, JSValue::class)
        ldc(op)
        operation("applyStringOrNumericBinaryOperator", JSValue::class, JSValue::class, JSValue::class, String::class)
    }

    /**
     * Stack arguments:
     *   None
     *
     * Stack result (+1):
     *   1. JSValue
     */
    private fun MethodAssembly.compileIdentifierReference(identifierReferenceNode: IdentifierReferenceNode) {
        ldc(identifierReferenceNode.identifierName)
        aconst_null
        operation("resolveBinding", JSReference::class, String::class, EnvRecord::class)
    }

    /*
     * SCRIPT FUNCTION COMPILATION
     */
    @ECMAImpl("14.1.22")
    private fun instantiateFunctionObject(function: FunctionDeclarationNode): ClassNode {
        val functionName = function.identifier?.identifierName ?: "default"
        val parameters = function.parameters

        val functionClassName = "Function_${functionName}_${sanitizedFileName}_${Agent.objectCount++}"
        val previousExtraMethodCount = extraMethods.size

        return assembleClass(
            public,
            functionClassName,
            superName = "me/mattco/reeva/compiler/JSScriptFunction"
        ) {
            method(public, "<init>", void, Realm::class, EnvRecord::class) {
                aload_0
                aload_1
                getstatic(JSFunction.ThisMode::class, "NonLexical", JSFunction.ThisMode::class)
                // TODO: Strict mode
                ldc(false)
                aload_2
                invokespecial(JSScriptFunction::class, "<init>", void, Realm::class, JSFunction.ThisMode::class, Boolean::class, EnvRecord::class)
                _return
            }

            method(public, "name", String::class) {
                ldc(functionName)
                areturn
            }

            method(public, "getSourceText", String::class) {
                // TODO
                ldc("")
                areturn
            }

            method(public, "isClassConstructor", Boolean::class) {
                ldc(false)
                ireturn
            }

            method(public, "getHomeObject", JSValue::class) {
                // TODO
                pushUndefined
                areturn
            }

            method(public, "getParameterNames", Array<String>::class) {
                if (parameters.restParameter == null) {
                    val actualParams = parameters.functionParameters.parameters
                    ldc(actualParams.size)
                    anewarray<String>()
                    actualParams.forEachIndexed { index, param ->
                        dup
                        ldc(index)
                        ldc(param.bindingElement.binding.identifier.identifierName)
                        aastore
                    }
                } else {
                    TODO()
                }
                areturn
            }

            method(public, "getParamHasDefaultValue", Boolean::class, Int::class) {
                if (parameters.restParameter == null) {
                    parameters.functionParameters.parameters.forEachIndexed { index, param ->
                        iload_1
                        ldc(index)
                        ifStatement(JumpCondition.Equal) {
                            ldc(param.bindingElement.binding.initializer != null)
                            ireturn
                        }
                    }
                    construct(IllegalArgumentException::class, String::class) {
                        ldc("Invalid index given to getParamHasDefaultValue")
                    }
                    athrow
                } else {
                    TODO()
                }
            }

            method(public, "getDefaultParameterValue", JSValue::class, Int::class) {
                if (parameters.restParameter == null) {
                    parameters.functionParameters.parameters.forEachIndexed { index, param ->
                        val binding = param.bindingElement.binding

                        iload_1
                        ldc(index)
                        ifStatement(JumpCondition.Equal) {
                            if (binding.initializer == null) {
                                pushUndefined
                            } else {
                                compileExpression(binding.initializer.node)
                            }
                            areturn
                        }
                    }
                    construct(IllegalArgumentException::class, String::class) {
                        ldc("Invalid index given to getDefaultParameterValue")
                    }
                    athrow
                } else {
                    TODO()
                }
            }

            method(public, "call", JSValue::class, JSValue::class, List::class) {
                methodStates.add(MethodState(functionClassName, "call", JSValue::class))
                val oldNeedsToPop = needsToPopContext
                needsToPopContext = true

                // setup
                aload_0
                pushUndefined
                operation("prepareForOrdinaryCall", ExecutionContext::class, JSScriptFunction::class, JSValue::class)
                aload_0
                swap
                aload_1
                operation("ordinaryCallBindThis", JSValue::class, JSScriptFunction::class, ExecutionContext::class, JSValue::class)
                pop

                functionDeclarationInstantiation(function, JSFunction.ThisMode.NonLexical)

                // execution
                aload_0
                aload_1
                aload_2
                invokevirtual(functionClassName, "execute", Completion::class, JSValue::class, List::class)

                // Clean up execution context
                invokestatic(Agent::class, "popContext", void)

                // return the value from "execute"
                getfield(Completion::class, "value", JSValue::class)
                areturn

                needsToPopContext = oldNeedsToPop
                methodStates.removeLast()
            }

            method(public, "construct", JSValue::class, List::class, JSValue::class) {
                methodStates.add(MethodState(functionClassName, "construct", JSValue::class))
                val oldNeedsToPop = needsToPopContext
                needsToPopContext = true

                // TODO
                pushUndefined
                areturn

                needsToPopContext = oldNeedsToPop
                methodStates.removeLast()
            }

            method(public, "execute", Completion::class, JSValue::class, List::class) {
                methodStates.add(MethodState(functionClassName, "execute", Completion::class))
                // actual body
                if (function.body.statementList != null)
                    compileStatementList(function.body.statementList)

                // In case the method doesn't have a return statement inside of itself
                construct(Completion::class, Completion.Type::class, JSValue::class) {
                    getstatic(Completion.Type::class, "Return", Completion.Type::class)
                    pushUndefined
                }
                areturn
                methodStates.removeLast()
            }

            extraMethods.subList(previousExtraMethodCount, extraMethods.size).forEach {
                methodStates.add(MethodState(functionClassName, it.name, Type.getReturnType(it.desc)))
                node.methods.add(it)
                methodStates.removeLast()
            }

            repeat(extraMethods.size - previousExtraMethodCount) {
                extraMethods.removeLast()
            }
        }
    }

    @ECMAImpl("9.2.10")
    private fun MethodAssembly.functionDeclarationInstantiation(function: FunctionDeclarationNode, thisMode: JSFunction.ThisMode) {
        val parameters = function.parameters
        val body = function.body
        val parameterNames = parameters.boundNames()
        val hasDuplicates = parameterNames.groupBy { it }.size != parameterNames.size
        val simpleParameterList = parameters.isSimpleParameterList()
        val hasParameterExpressions = parameters.containsExpression()
        val varNames = body.varDeclaredNames()
        val varDeclarations = body.varScopedDeclarations()
        val lexicalNames = body.lexicallyDeclaredNames()

        val functionNames = mutableListOf<String>()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()

        body.varScopedDeclarations().asReversed().forEach { decl ->
            if (decl is FunctionDeclarationNode) {
                val boundNames = decl.boundNames()
                expect(boundNames.size == 1)
                val declName = boundNames[0]
                if (declName !in functionNames) {
                    functionNames.add(0, declName)
                    functionsToInitialize.add(decl)
                }
            }
        }

        var argumentsObjectNeeded = when {
            thisMode == JSFunction.ThisMode.Lexical -> false
            "arguments" in parameterNames -> false
            !hasParameterExpressions && ("arguments" in functionNames || "arguments" in lexicalNames) -> false
            else -> true
        }

        pushRunningContext
        getfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)

        // TODO: Strict check
        if (!hasParameterExpressions) {
            astore(3)
        } else {
            invokestatic(DeclarativeEnvRecord::class, "create", DeclarativeEnvRecord::class, EnvRecord::class)
            dup
            astore(3)
            putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
        }

        parameterNames.forEach { name ->
            aload(3)
            ldc(name)
            invokevirtual(EnvRecord::class, "hasBinding", Boolean::class, String::class)
            ifStatement(JumpCondition.False) {
                aload(3)
                ldc(name)
                ldc(false)
                invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                if (hasDuplicates) {
                    aload(3)
                    ldc(name)
                    pushUndefined
                    invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
                }
            }
        }

        // This will be dependent on the argumentsObjectNeeded flag
        val parameterBindings = parameterNames

        if (argumentsObjectNeeded) {
            // TODO
        }

        aload_0
        aload_2
        if (hasDuplicates) {
            aconst_null
        } else {
            aload(3)
        }
        operation("applyFunctionArguments", void, JSScriptFunction::class, List::class, EnvRecord::class)

        val varEnv = 4

        if (!hasParameterExpressions) {
            val instantiatedVarNames = parameterBindings.toMutableList()
            varNames.forEach { name ->
                if (name !in instantiatedVarNames) {
                    instantiatedVarNames.add(name)
                    aload(3)
                    ldc(name)
                    ldc(false)
                    invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                }
            }
            aload(3)
            astore(varEnv)
        } else {
            aload(3)
            invokestatic(DeclarativeEnvRecord::class, "create", DeclarativeEnvRecord::class, EnvRecord::class)
            astore(varEnv)
            pushRunningContext
            aload(varEnv)
            putfield(ExecutionContext::class, "variableEnv", EnvRecord::class)

            val instantiatedVarNames = parameterBindings.toMutableList()
            varNames.forEach { name ->
                if (name !in instantiatedVarNames) {
                    instantiatedVarNames.add(name)
                    aload(varEnv)
                    ldc(name)
                    ldc(false)
                    invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)

                    aload(varEnv)
                    ldc(name)

                    if (name !in parameterBindings || name in functionNames) {
                        pushUndefined
                    } else {
                        aload(3)
                        ldc(name)
                        ldc(false)
                        invokevirtual(EnvRecord::class, "getBindingValue", JSValue::class, String::class, Boolean::class)
                    }

                    invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
                }
            }
        }

        // TODO: if (isStrict)
        var lexEnv: Int
        if (true) {
            lexEnv = 5
            aload(varEnv)
            invokestatic(DeclarativeEnvRecord::class, "create", DeclarativeEnvRecord::class, EnvRecord::class)
            astore(lexEnv)
        } else {
            lexEnv = varEnv
        }

        pushRunningContext
        aload(lexEnv)
        putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)

        val lexDeclarations = body.lexicallyScopedDeclarations()
        lexDeclarations.forEach { decl ->
            decl.boundNames().forEach { name ->
                aload(lexEnv)
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

        functionsToInitialize.forEach { func ->
            val compiledFunc = instantiateFunctionObject(func)
            dependencies.add(compiledFunc)

            val boundNames = func.boundNames()
            expect(boundNames.size == 1)
            val funcName = boundNames[0]

            aload(varEnv)
            ldc(funcName)
            construct(compiledFunc.name, Realm::class, EnvRecord::class) {
                pushRealm
                aload(lexEnv)
            }
            ldc(false)
            invokevirtual(EnvRecord::class, "setMutableBinding", void, String::class, JSValue::class, Boolean::class)
        }
    }

    /**
     * Stack arguments:
     *   None
     *
     * Stack result (+1):
     *   JSValue
     */
    private fun MethodAssembly.compileThis() {
        operation("resolveThisBinding", JSObject::class)
    }

    // Stack args:
    //    1. env (EnvRecord)
    //    2. value (JSValue)
    private fun MethodAssembly.initializeBoundName(name: String) {
        // env, value
        swap
        // value, env
        dup
        // value, env, env
        ifElseStatement(JumpCondition.NonNull) {
            ifBlock {
                // value, env
                dup_x1
                // env, value, env
                swap
                // env, env, value
                ldc(name)
                // env, env, value, string
                swap
                // env, env, string, value
                invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
                // env
            }

            elseBlock {
                // value, env
                dup_x1
                // env, value, env
                ldc(name)
                // env, value, env, string
                swap
                // env, value, string, env
                operation("resolveBinding", JSReference::class, String::class, EnvRecord::class)
                // env, value, ref
                swap
                // env, ref, value
                operation("putValue", void, JSValue::class, JSValue::class)
                // env
            }
        }
    }

    private fun addExtraMethodToCurrentClass(
        name: String,
        routine: MethodAssembly.() -> Unit
    ) {
        methodStates.add(MethodState(methodStates.last().className, name, Completion::class, false))
        val descriptor = Type.getMethodDescriptor(coerceType(Completion::class), coerceType(JSValue::class), coerceType(List::class))

        val methodNode = MethodNode(Opcodes.ASM7, public.access, name, descriptor, null, null)
        val methodAssembly = MethodAssembly(methodNode)

        methodAssembly.apply {
            routine(this)
            construct(Completion::class, Completion.Type::class, JSValue::class) {
                getstatic(Completion.Type::class, "Return", Completion.Type::class)
                pushUndefined
            }
            areturn
        }

        extraMethods.add(methodNode)
        methodStates.removeLast()
    }

    private fun MethodAssembly.invokeExtraMethod(name: String) {
        aload_0
        if (methodStates.last().isTopLevel) {
            pushUndefined
            construct(ArrayList::class)
        } else {
            aload_1
            aload_2
        }
        invokevirtual(methodStates.last().className, name, Completion::class, JSValue::class, List::class)
    }

    private fun MethodAssembly.shouldThrow(errorName: String) {
        construct(java.lang.IllegalStateException::class, String::class) {
            ldc("FIXME: This should have instead thrown a JS $errorName")
        }
        athrow
    }

    private val MethodAssembly.pushRunningContext: Unit
        get() {
            invokestatic(Agent::class, "getRunningContext", ExecutionContext::class)
        }

    private val MethodAssembly.pushAgent: Unit
        get() {
            pushRunningContext
            getfield(ExecutionContext::class, "agent", Agent::class)
        }

    private val MethodAssembly.pushRealm: Unit
        get() {
            pushRunningContext
            getfield(ExecutionContext::class, "realm", Realm::class)
        }

    private val MethodAssembly.pushFunction: Unit
        get() {
            pushRunningContext
            getfield(ExecutionContext::class, "function", JSFunction::class)
        }

    private val MethodAssembly.pushLexicalEnv: Unit
        get() {
            pushRunningContext
            getfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
        }

    private val MethodAssembly.pushVariableEnv: Unit
        get() {
            pushRunningContext
            getfield(ExecutionContext::class, "variableEnv", EnvRecord::class)
        }

    private val MethodAssembly.pushEmpty: Unit
        get() {
            getstatic(JSEmpty::class, "INSTANCE", JSEmpty::class)
        }

    private val MethodAssembly.pushUndefined: Unit
        get() {
            getstatic(JSUndefined::class, "INSTANCE", JSUndefined::class)
        }

    private val MethodAssembly.pushNull: Unit
        get() {
            getstatic(JSNull::class, "INSTANCE", JSNull::class)
        }

    private val MethodAssembly.pushTrue: Unit
        get() {
            getstatic(JSTrue::class, "INSTANCE", JSTrue::class)
        }

    private val MethodAssembly.pushFalse: Unit
        get() {
            getstatic(JSFalse::class, "INSTANCE", JSFalse::class)
        }

    private fun MethodAssembly.isUndefined() {
        pushUndefined
        acmp
    }

    private fun MethodAssembly.isNull() {
        pushNull
        acmp
    }

    private fun MethodAssembly.isTrue() {
        pushTrue
        acmp
    }

    private fun MethodAssembly.isFalse() {
        pushFalse
        acmp
    }

    private val MethodAssembly.acmp: Unit
        get() {
            ifElseStatement(JumpCondition.RefEqual) {
                ifBlock { iconst_1 }
                elseBlock { iconst_0 }
            }
        }

    private val MethodAssembly.sameValue: Unit
        get() = invokevirtual(JSValue::class, "sameValue", Boolean::class, JSValue::class)

    private val MethodAssembly.sameValueZero: Unit
        get() = invokevirtual(JSValue::class, "sameValueZero", Boolean::class, JSValue::class)

    private val MethodAssembly.sameValueNonNumeric: Unit
        get() = invokevirtual(JSValue::class, "sameValueNonNumeric", Boolean::class, JSValue::class)

    private val MethodAssembly.toBoolean: Unit
        get() = invokevirtual(JSValue::class, "toBoolean", Boolean::class)

    private val MethodAssembly.toNumber: Unit
        get() = invokevirtual(JSValue::class, "toNumber", Double::class)

    private val MethodAssembly.toObject: Unit
        get() = invokevirtual(JSValue::class, "toObject", JSObject::class)

    private val MethodAssembly.wrapObjectInValue: Unit
        get() {
            operation("wrapInValue", JSValue::class, Any::class)
        }

    private val MethodAssembly.wrapIntInValue: Unit
        get() {
            new<JSNumber>()
            dup_x1
            swap
            invokespecial(JSNumber::class, "<init>", void, Int::class)
        }

    private val MethodAssembly.wrapDoubleInValue: Unit
        get() {
            new<JSNumber>()
            dup_x2
            dup_x2
            pop
            invokespecial(JSNumber::class, "<init>", void, Double::class)
        }

    private val MethodAssembly.wrapBooleanInValue: Unit
        get() {
            new<JSBoolean>()
            dup_x1
            swap
            invokespecial(JSBoolean::class, "<init>", void, Boolean::class)
        }

    private val MethodAssembly.returnFromFunction: Unit
        get() {
            if (needsToPopContext) {
                invokestatic(Agent::class, "popContext", void)
            }
            when (coerceType(methodStates.last().returnType)) {
                coerceType(Completion::class) -> {
                    construct(Completion::class, Completion.Type::class, JSValue::class) {
                        getstatic(Completion.Type::class, "Return", Completion.Type::class)
                        pushRunningContext
                        getfield(ExecutionContext::class, "error", JSErrorObject::class)
                    }
                }
                else -> {
                    pushRunningContext
                    getfield(ExecutionContext::class, "error", JSErrorObject::class)
                }
            }
            areturn
        }

    private val MethodAssembly.checkError: Unit
        get() {
            invokestatic(Agent::class, "hasError", Boolean::class)
            ifStatement(JumpCondition.True) {
                returnFromFunction
            }
        }

    private fun MethodAssembly.operation(name: String, returnType: TypeLike, vararg parameterTypes: TypeLike) {
        val method = Operations::class.java.declaredMethods.first { it.name == name }
        invokestatic(Operations::class, name, returnType, *parameterTypes)
        if (method.getDeclaredAnnotation(JSThrows::class.java) != null) {
            checkError
        }
    }

    companion object {
        private var classCounter = 0
            get() = field++
    }
}

/*
function foo() {
}

{
    function foo() {}
}

export foo;
 */

/*
abstract class CompiledProgram {

    abstract fun run()

}




class TopLevel_libjs { }

class Function_foo$0_libjs { }

class Function_foo$1_libjs { }
 */
