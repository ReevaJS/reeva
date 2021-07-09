package me.mattco.reeva.interpreter.transformer

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.interpreter.ClassDescriptor
import me.mattco.reeva.interpreter.DeclarationsArray
import me.mattco.reeva.interpreter.JumpTable
import me.mattco.reeva.interpreter.MethodDescriptor
import me.mattco.reeva.interpreter.transformer.opcodes.*
import me.mattco.reeva.parsing.HoistingScope
import me.mattco.reeva.parsing.Scope
import me.mattco.reeva.parsing.Variable
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable
import java.math.BigInteger
import kotlin.math.floor

data class FunctionInfo(
    val name: String?,
    val code: FunctionOpcodes,
    val argCount: Int,
    val isStrict: Boolean,
    val isTopLevelScript: Boolean,

    // Only necessary if the function requires a mapped arguments object
    val parameters: ParameterList? = null,
)

class Transformer : ASTVisitor {
    private lateinit var generator: Generator
    private var currentScope: Scope? = null

    data class LabelledBlock(val labels: Set<String>, val block: Block)

    private val breakableScopes = mutableListOf<LabelledBlock>()
    private val continuableScopes = mutableListOf<LabelledBlock>()

    fun transform(node: ASTNode): FunctionInfo {
        if (::generator.isInitialized)
            throw IllegalStateException("Cannot re-use an IRTransformer")

        return when (node) {
            is ModuleNode -> TODO()
            is ScriptNode -> {
                generator = Generator(1, node.scope.additionalInlineableRegisterCount)

                globalDeclarationInstantiation(node.scope) {
                    visit(node.statements)
                    generator.addIfNotTerminated(Return)
                }

                FunctionInfo(
                    null,
                    generator.finish(),
                    1,
                    node.scope.isStrict,
                    isTopLevelScript = true
                )
            }
            is FunctionDeclarationNode -> {
                generator = Generator(node.parameters.size + 1, node.scope.additionalInlineableRegisterCount)

                makeFunctionInfo(
                    node.identifier.identifierName,
                    node.parameters,
                    node.body,
                    node.functionScope,
                    node.body.scope,
                    node.functionScope.isStrict,
                    node.kind,
                )
            }
            else -> TODO()
        }
    }

    private fun enterScope(scope: Scope) {
        currentScope = scope

        if (scope.requiresEnv())
            generator.add(PushDeclarativeEnvRecord(scope.nextSlot))
    }

    private fun exitScope(scope: Scope) {
        currentScope = scope.outer

        if (!scope.requiresEnv())
            return

        if (generator.currentBlock.isTerminated) {
            // Insert a PopEnv before the terminating instruction. It doesn't modify the
            // accumulator register, so this is always safe
            val currentBlock = generator.currentBlock
            currentBlock.add(currentBlock.lastIndex, PopEnvRecord)
        } else {
            generator.add(PopEnvRecord)
        }
    }

    override fun visitASTListNode(node: ASTListNode<*>) {
        node.children.forEach {
            visit(it)
            if (generator.currentBlock.isTerminated)
                return
        }
    }

    private fun visitBlock(node: BlockNode, pushScope: Boolean) {
        if (pushScope)
            enterScope(node.scope)
        try {
            var block: Block? = null
            if (node.labels.isNotEmpty()) {
                block = generator.makeBlock()
                enterBreakableScope(block, node.labels)
            }

            // BlockScopeInstantiation
            node.scope.functionsToInitialize.forEach {
                visitFunctionHelper(
                    it.identifier.identifierName,
                    it.parameters,
                    it.body,
                    it.functionScope,
                    it.body.scope,
                    it.body.scope.isStrict,
                    it.kind,
                )

                storeVariable(it.variable)
            }

            visitASTListNode(node.statements)

            if (node.labels.isNotEmpty()) {
                exitBreakableScope()
                generator.add(Jump(block!!))
                generator.currentBlock = block
            }
        } finally {
            if (pushScope)
                exitScope(node.scope)
        }
    }

    override fun visitBlock(node: BlockNode) {
        visitBlock(node, pushScope = true)
    }

    override fun visitExpressionStatement(node: ExpressionStatementNode) {
        visit(node.node)
    }

    override fun visitIfStatement(node: IfStatementNode) {
        visit(node.condition)

        if (node.falseBlock == null) {
            generator.ifHelper(::JumpIfToBooleanTrue) {
                visit(node.trueBlock)
            }
        } else {
            generator.ifElseHelper(::JumpIfToBooleanTrue, {
                visit(node.trueBlock)
            }, {
                visit(node.falseBlock)
            })
        }
    }

    override fun visitDoWhileStatement(node: DoWhileStatementNode) {
        val testBlock = generator.makeBlock()
        val headBlock = generator.makeBlock()
        val doneBlock = generator.makeBlock()

        generator.add(Jump(headBlock))
        generator.currentBlock = headBlock

        enterBreakableScope(doneBlock, node.labels)
        enterContinuableScope(testBlock, node.labels)
        visit(node.body)
        generator.addIfNotTerminated(Jump(testBlock))
        generator.currentBlock = testBlock
        visit(node.condition)
        exitBreakableScope()
        exitContinuableScope()

        generator.add(JumpIfToBooleanTrue(headBlock, doneBlock))
        generator.currentBlock = doneBlock
    }

    override fun visitWhileStatement(node: WhileStatementNode) {
        val testBlock = generator.makeBlock()
        val bodyBlock = generator.makeBlock()
        val doneBlock = generator.makeBlock()

        generator.add(Jump(testBlock))
        generator.currentBlock = testBlock
        visit(node.condition)
        generator.add(JumpIfToBooleanTrue(bodyBlock, doneBlock))
        generator.currentBlock = bodyBlock

        enterBreakableScope(doneBlock, node.labels)
        enterContinuableScope(testBlock, node.labels)
        visit(node.body)
        exitContinuableScope()
        exitBreakableScope()

        generator.add(Jump(testBlock))
        generator.currentBlock = doneBlock
    }

    override fun visitForStatement(node: ForStatementNode) {
        if (node.initializerScope != null)
            enterScope(node.initializerScope!!)

        if (node.initializer != null)
            visit(node.initializer)

        var testBlock: Block? = null

        if (node.condition != null) {
            testBlock = generator.makeBlock()
        }

        val bodyBlock = generator.makeBlock()
        val doneBlock = generator.makeBlock()
        var incrementerBlock: Block? = null

        if (node.condition != null) {
            generator.add(Jump(testBlock!!))
            generator.currentBlock = testBlock
            visit(node.condition)
            generator.add(JumpIfToBooleanTrue(bodyBlock, doneBlock))
        } else {
            generator.add(Jump(bodyBlock))
        }

        if (node.incrementer != null)
            incrementerBlock = generator.makeBlock()

        generator.currentBlock = bodyBlock
        enterBreakableScope(doneBlock, node.labels)
        enterContinuableScope(incrementerBlock ?: testBlock ?: bodyBlock, node.labels)
        visit(node.body)
        exitBreakableScope()
        exitContinuableScope()

        if (node.incrementer != null) {
            generator.add(Jump(incrementerBlock!!))
            generator.currentBlock = incrementerBlock
            visit(node.incrementer)
        }

        generator.add(Jump(testBlock ?: bodyBlock))

        generator.currentBlock = doneBlock

        if (node.initializerScope != null)
            exitScope(node.initializerScope!!)
    }

    private fun assign(node: ASTNode) {
        when (node) {
            // TODO: This branch shouldn't be necessary
            is IdentifierReferenceNode -> generator.add(StaGlobal(generator.intern(node.identifierName)))
            is BindingIdentifierNode -> generator.add(StaGlobal(generator.intern(node.identifierName)))
            is VariableDeclarationNode -> {
                val declaration = node.declarations[0]
                storeVariable(declaration.variable)
            }
            is LexicalDeclarationNode -> {
                val declaration = node.declarations[0]
                storeVariable(declaration.variable)
            }
            else -> TODO()
        }
    }

    private fun iterateForEach(labels: Set<String>, decl: ASTNode, body: ASTNode) {
        iterateValues(labels) {
            assign(decl)
            visit(body)
        }
    }

    override fun visitForIn(node: ForInNode) {
        visit(node.expression)
        val isUndefinedBlock = generator.makeBlock()
        val isNotUndefinedBlock = generator.makeBlock()

        generator.add(JumpIfUndefined(isUndefinedBlock, isNotUndefinedBlock))
        generator.currentBlock = isNotUndefinedBlock

        generator.add(ForInEnumerate)
        iterateForEach(node.labels, node.decl, node.body)

        generator.add(Jump(isUndefinedBlock))
        generator.currentBlock = isUndefinedBlock
    }

    override fun visitForOf(node: ForOfNode) {
        visit(node.expression)
        generator.add(GetIterator)
        iterateForEach(node.labels, node.decl, node.body)
    }

    override fun visitForAwaitOf(node: ForAwaitOfNode) {
        TODO()
    }

    override fun visitThrowStatement(node: ThrowStatementNode) {
        visit(node.expr)

        if (generator.currentBlock.isTerminated)
            return

        val handlerScope = generator.handlerScope
        if (handlerScope != null) {
            if (handlerScope.catchBlock != null) {
                generator.add(Jump(handlerScope.catchBlock))
            } else {
                generator.add(Star(handlerScope.scratchRegister(generator)))
                generator.add(LdaInt(JumpTable.THROW))
                generator.add(Star(handlerScope.actionRegister(generator)))
                generator.add(Jump(handlerScope.finallyBlock!!))
            }
        } else {
            generator.add(Throw)
        }
    }

    override fun visitTryStatement(node: TryStatementNode) {
        val hasCatch = node.catchNode != null
        val hasFinally = node.finallyBlock != null

        var catchBlock: Block? = null
        var finallyBlock: Block? = null

        if (hasCatch)
            catchBlock = generator.makeBlock()
        if (hasFinally)
            finallyBlock = generator.makeBlock()

        val doneBlock = generator.makeBlock()

        generator.enterHandlerScope(catchBlock, finallyBlock)
        val tryBlock = generator.makeBlock()
        generator.add(Jump(tryBlock))
        generator.currentBlock = tryBlock
        visit(node.tryBlock)

        if (hasFinally) {
            if (!generator.currentBlock.isTerminated) {
                val handlerScope = generator.handlerScope!!
                generator.add(LdaInt(JumpTable.FALLTHROUGH))
                generator.add(Star(handlerScope.actionRegister(generator)))
                generator.add(Jump(finallyBlock!!))
            }
        } else {
            generator.addIfNotTerminated(Jump(doneBlock))
        }

        val finallyHandlerScope = generator.exitHandlerScope()

        if (hasCatch) {
            generator.currentBlock = catchBlock!!

            if (hasFinally) {
                // We exit above and re-enter here because we no longer have an active
                // catch handler
                generator.enterHandlerScope(null, finallyBlock!!)
            }

            val catchNode = node.catchNode!!
            val parameter = catchNode.parameter

            enterScope(catchNode.block.scope)

            if (parameter != null) {
                // The exception is in the accumulator
                storeVariable(parameter.variable)
            }

            // We will have already pushed a context, so we don't push a scope here
            visitBlock(node.catchNode.block, pushScope = false)

            exitScope(catchNode.block.scope)

            if (!generator.currentBlock.isTerminated) {
                if (hasFinally) {
                    generator.add(LdaInt(JumpTable.FALLTHROUGH))
                    generator.add(Star(finallyHandlerScope.actionRegister(generator)))
                    generator.add(Jump(finallyBlock!!))
                } else {
                    generator.add(Jump(doneBlock))
                }
            }

            if (hasFinally)
                generator.exitHandlerScope()
        }

        if (hasFinally) {
            generator.currentBlock = finallyBlock!!
            visit(node.finallyBlock!!)

            if (!generator.currentBlock.isTerminated) {
                val throwBlock = generator.makeBlock()
                val returnBlock = generator.makeBlock()
                val jumpTable = JumpTable()
                jumpTable[JumpTable.FALLTHROUGH] = doneBlock
                jumpTable[JumpTable.THROW] = throwBlock
                jumpTable[JumpTable.RETURN] = returnBlock
                generator.add(Ldar(finallyHandlerScope.actionRegister(generator)))
                generator.add(JumpFromTable(generator.intern(jumpTable)))

                generator.currentBlock = throwBlock
                generator.add(Ldar(finallyHandlerScope.scratchRegister(generator)))
                generator.add(Throw)
                generator.currentBlock = returnBlock
                generator.add(Ldar(finallyHandlerScope.scratchRegister(generator)))
                generator.add(Return)
            }
        }

        generator.currentBlock = doneBlock
    }

    override fun visitBreakStatement(node: BreakStatementNode) {
        val block = if (node.label != null) {
            breakableScopes.last { node.label in it.labels }.block
        } else breakableScopes.last().block

        generator.add(Jump(block))
        generator.currentBlock = generator.makeBlock()
    }

    override fun visitContinueStatement(node: ContinueStatementNode) {
        val block = if (node.label != null) {
            continuableScopes.last { node.label in it.labels }.block
        } else continuableScopes.last().block

        generator.add(Jump(block))
        generator.currentBlock = generator.makeBlock()
    }

    override fun visitReturnStatement(node: ReturnStatementNode) {
        if (node.expression != null) {
            visit(node.expression)
            if (generator.currentBlock.isTerminated)
                return
        } else {
            generator.add(LdaUndefined)
        }

        val handlerScope = generator.handlerScope
        if (handlerScope?.finallyBlock != null) {
            generator.add(Star(handlerScope.scratchRegister(generator)))
            generator.add(LdaInt(JumpTable.RETURN))
            generator.add(Star(handlerScope.actionRegister(generator)))
            generator.add(Jump(handlerScope.finallyBlock))
        } else {
            generator.add(Return)
        }
    }

    override fun visitLexicalDeclaration(node: LexicalDeclarationNode) {
        node.declarations.forEach(::visitDeclaration)
    }

    override fun visitVariableDeclaration(node: VariableDeclarationNode) {
        node.declarations.forEach(::visitDeclaration)
    }

    private fun visitDeclaration(declaration: Declaration) {
        val variable = declaration.variable

        if (declaration.initializer != null) {
            visit(declaration.initializer)
        } else {
            generator.add(LdaUndefined)
        }

        storeVariable(variable)
    }

    override fun visitDebuggerStatement() {
        generator.add(DebugBreakpoint)
    }

    override fun visitImportDeclaration(node: ImportDeclarationNode) {
        TODO()
    }

    override fun visitExport(node: ExportNode) {
        TODO()
    }

    override fun visitBindingIdentifier(node: BindingIdentifierNode) {
        TODO()
    }

    override fun visitIdentifierReference(node: IdentifierReferenceNode) {
        val variable = node.targetVar
        loadVariable(node.targetVar)
        if (node.targetVar.mode == Variable.Mode.Global)
            return

        val declarationStart = node.targetVar.source.sourceStart
        val useStart = node.sourceStart
        if (useStart.index < declarationStart.index && variable.type != Variable.Type.Var) {
            // We need to check if the variable has been initialized
            val throwBlock = generator.makeBlock()
            val continuationBlock = generator.makeBlock()
            generator.add(JumpIfEmpty(throwBlock, continuationBlock))
            generator.currentBlock = throwBlock
            val message = "cannot access lexical variable \"${node.identifierName}\" before initialization"
            generator.add(ThrowConstantError(generator.intern(message)))
            generator.currentBlock = continuationBlock
        }
    }

    override fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        // nop
    }

    override fun visitFunctionExpression(node: FunctionExpressionNode) {
        visitFunctionHelper(
            node.identifier?.identifierName ?: "<anonymous>",
            node.parameters,
            node.body,
            node.functionScope,
            node.body.scope,
            node.functionScope.isStrict,
            node.kind,
        )
    }

    override fun visitArrowFunction(node: ArrowFunctionNode) {
        visitFunctionHelper(
            "<anonymous>",
            node.parameters,
            node.body,
            node.functionScope,
            if (node.body is BlockNode) node.body.scope else node.functionScope,
            node.functionScope.isStrict,
            node.kind,
        )
    }

    private fun globalDeclarationInstantiation(
        scope: Scope,
        evaluationBlock: () -> Unit
    ) {
        enterScope(scope)

        val variables = scope.declaredVariables

        val varVariables = variables.filter { it.type == Variable.Type.Var }
        val lexVariables = variables.filter { it.type != Variable.Type.Var }

        val varNames = varVariables.map { it.name }
        val lexNames = lexVariables.map { it.name }

        val functionNames = mutableListOf<String>()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()

        for (decl in varVariables.asReversed()) {
            val source = decl.source

            if (source !is FunctionDeclarationNode)
                continue

            val name = decl.name
            if (name in functionNames)
                continue

            functionNames.add(0, name)

            // We only care about top-level functions. Functions in nested block
            // scopes get initialized in BlockDeclarationInstantiation
            if (source.declaredScope == scope)
                functionsToInitialize.add(0, source)
        }

        val declaredVarNames = mutableListOf<String>()

        for (decl in varVariables) {
            val source = decl.source

            if (source is FunctionDeclarationNode)
                continue

            val name = decl.name
            if (name in functionNames || name in declaredVarNames)
                continue

            declaredVarNames.add(name)
        }

        if (declaredVarNames.isNotEmpty() || lexNames.isNotEmpty() || functionNames.isNotEmpty()) {
            val array = DeclarationsArray(declaredVarNames, lexNames, functionNames)
            generator.add(DeclareGlobals(generator.intern(array)))
        }

        for (func in functionsToInitialize) {
            visitFunctionHelper(
                func.identifier.identifierName,
                func.parameters,
                func.body,
                func.functionScope,
                func.body.scope,
                func.body.scope.isStrict,
                func.kind,
            )

            storeVariable(func.variable)
        }

        evaluationBlock()

        exitScope(scope)
    }

    private fun functionDeclarationInstantiation(
        parameters: ParameterList,
        functionScope: Scope,
        bodyScope: Scope,
        isStrict: Boolean,
        evaluationBlock: () -> Unit,
    ) {
        val variables = bodyScope.declaredVariables
        val varVariables = variables.filter { it.type == Variable.Type.Var }
        val functionNames = mutableListOf<String>()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()

        for (decl in varVariables.asReversed()) {
            val source = decl.source

            if (source !is FunctionDeclarationNode)
                continue

            val name = decl.name
            if (name in functionNames)
                continue

            functionNames.add(0, name)

            // We only care about top-level functions. Functions in nested block
            // scopes get initialized in BlockDeclarationInstantiation
            if (source.declaredScope == bodyScope)
                functionsToInitialize.add(0, source)
        }

        expect(functionScope is HoistingScope)
        when (functionScope.argumentsObjectMode) {
            HoistingScope.ArgumentsObjectMode.None -> {}
            HoistingScope.ArgumentsObjectMode.Unmapped -> {
                generator.add(CreateUnmappedArgumentsObject)
                storeVariable(functionScope.argumentsObjectVariable!!)
            }
            HoistingScope.ArgumentsObjectMode.Mapped -> {
                generator.add(CreateMappedArgumentsObject)
                storeVariable(functionScope.argumentsObjectVariable!!)
            }
        }

        enterScope(functionScope)

        if (parameters.containsDuplicates())
            TODO("Handle duplicate parameter names")

        parameters.forEachIndexed { index, param ->
            val register = index + 1
            val variable = param.variable

            when {
                param.isRest -> {
                    generator.add(CreateRestParam)
                    storeVariable(variable)
                }
                param.initializer != null -> {
                    generator.add(Ldar(register))
                    generator.ifHelper(::JumpIfUndefined) {
                        visit(param.initializer)
                        storeVariable(variable)
                    }
                }
                !variable.isInlineable -> {
                    generator.add(Ldar(register))
                    storeVariable(variable)
                }
            }
        }

        for (func in functionsToInitialize) {
            visitFunctionHelper(
                func.identifier.identifierName,
                func.parameters,
                func.body,
                func.functionScope,
                func.body.scope,
                isStrict,
                func.kind,
            )

            storeVariable(func.variable)
        }

        if (bodyScope != functionScope)
            enterScope(bodyScope)

        evaluationBlock()

        if (bodyScope != functionScope)
            exitScope(bodyScope)

        exitScope(functionScope)
    }

    private fun visitFunctionHelper(
        name: String,
        parameters: ParameterList,
        body: ASTNode,
        functionScope: Scope,
        bodyScope: Scope,
        isStrict: Boolean,
        kind: Operations.FunctionKind,
        isClassConstructor: Boolean = false,
    ): FunctionInfo {
        if (kind.isAsync)
            TODO()

        val prevGenerator = generator
        generator = Generator(parameters.size + 1, functionScope.additionalInlineableRegisterCount)

        val info = makeFunctionInfo(
            name,
            parameters,
            body,
            functionScope,
            bodyScope,
            isStrict,
            kind,
        )

        generator = prevGenerator
        val closureOp = when {
            isClassConstructor -> ::CreateClassConstructor
            kind.isGenerator -> ::CreateGeneratorClosure
            else -> ::CreateClosure
        }
        generator.add(closureOp(generator.intern(info)))

        return info
    }

    private fun makeFunctionInfo(
        name: String,
        parameters: ParameterList,
        body: ASTNode,
        functionScope: Scope,
        bodyScope: Scope,
        isStrict: Boolean,
        kind: Operations.FunctionKind,
    ): FunctionInfo {
        functionDeclarationInstantiation(
            parameters,
            functionScope,
            bodyScope,
            isStrict
        ) {
            // body's scope is the same as the function's scope (the scope we receive
            // as a parameter). We don't want to re-enter the same scope, so we explicitly
            // call visitASTListNode instead, which skips the {enter,exit}Scope calls.
            if (body is BlockNode) {
                visitASTListNode(body.statements)
            } else visit(body)

            if (body is BlockNode)
                generator.addIfNotTerminated(LdaUndefined)
            generator.addIfNotTerminated(Return)
        }

        return FunctionInfo(
            name,
            generator.finish(),
            parameters.size + 1,
            isStrict,
            isTopLevelScript = false,
            parameters,
        )
    }

    override fun visitClassDeclaration(node: ClassDeclarationNode) {
        expect(node.identifier != null)
        visitClassImpl(node.identifier.identifierName, node.classNode)
        storeVariable(node.identifier.variable)
    }

    override fun visitClassExpression(node: ClassExpressionNode) {
        visitClassImpl(node.identifier?.identifierName, node.classNode)
    }

    private fun visitClassImpl(name: String?, node: ClassNode) {
        val fields = mutableListOf<ClassFieldNode>()
        val methods = mutableListOf<ClassMethodNode>()
        var constructor: ClassMethodNode? = null

        node.body.forEach {
            if (it is ClassFieldNode) {
                fields.add(it)
            } else {
                val method = it as ClassMethodNode
                if (method.isConstructor()) {
                    expect(constructor == null)
                    constructor = method
                } else {
                    methods.add(method)
                }
            }
        }

        if (constructor != null) {
            val method = constructor!!.method
            visitFunctionHelper(
                name ?: "<anonymous class constructor>",
                method.parameters,
                method.body,
                method.functionScope,
                method.body.scope,
                isStrict = true,
                Operations.FunctionKind.Normal,
                isClassConstructor = true,
            )
        } else {
            val info = makeEmptyFunctionInfo(name ?: "<anonymous class constructor>")
            generator.add(CreateClassConstructor(generator.intern(info)))
        }

        val constructorReg = generator.reserveRegister()
        generator.add(Star(constructorReg))

        if (node.heritage != null) {
            visit(node.heritage)
        } else {
            generator.add(LdaEmpty)
        }

        val superClassReg = generator.reserveRegister()
        generator.add(Star(superClassReg))

        // Process fields
        if (fields.isNotEmpty())
            TODO()

        // Process methods
        val methodDescriptors = mutableListOf<Index>()
        val createClassArgs = mutableListOf<Register>()

        for (classMethod in methods) {
            val method = classMethod.method
            val propName = method.propName
            val isComputed = propName.type == PropertyName.Type.Computed

            // If the name is computed, that comes before the method register
            if (isComputed) {
                visit(propName.expression)
                // TODO: Cast to property name
                val reg = generator.reserveRegister()
                generator.add(Star(reg))
                createClassArgs.add(reg)
            }

            val functionInfo = visitFunctionHelper(
                propName.asString(),
                method.parameters,
                method.body,
                method.functionScope,
                method.body.scope,
                isStrict = true,
                method.kind.toFunctionKind(),
            )

            val descriptor = MethodDescriptor(
                if (isComputed) null else propName.asString(),
                classMethod.isStatic,
                method.kind.toFunctionKind(),
                method.kind == MethodDefinitionNode.Kind.Getter,
                method.kind == MethodDefinitionNode.Kind.Setter,
                generator.intern(functionInfo)
            )

            methodDescriptors.add(generator.intern(descriptor))

            val closureReg = generator.reserveRegister()
            generator.add(Star(closureReg))
            createClassArgs.add(closureReg)
        }

        val classDescriptor = generator.intern(ClassDescriptor(methodDescriptors))

        generator.add(CreateClass(classDescriptor, constructorReg, superClassReg, createClassArgs))
    }

    private fun makeEmptyFunctionInfo(name: String): FunctionInfo {
        val block = Block(1)
        block.add(LdaUndefined)
        block.add(Return)

        return FunctionInfo(
            name,
            FunctionOpcodes(
                listOf(block),
                emptyList(),
                0
            ),
            1,
            isStrict = true,
            isTopLevelScript = false,
            parameters = null
        )
    }

    override fun visitBinaryExpression(node: BinaryExpressionNode) {
        val op = when (node.operator) {
            BinaryOperator.Add -> ::Add
            BinaryOperator.Sub -> ::Sub
            BinaryOperator.Mul -> ::Mul
            BinaryOperator.Div -> ::Div
            BinaryOperator.Exp -> ::Exp
            BinaryOperator.Mod -> ::Mod
            BinaryOperator.BitwiseAnd -> ::BitwiseAnd
            BinaryOperator.BitwiseOr -> ::BitwiseOr
            BinaryOperator.BitwiseXor -> ::BitwiseXor
            BinaryOperator.Shl -> ::ShiftLeft
            BinaryOperator.Shr -> ::ShiftRight
            BinaryOperator.UShr -> ::ShiftRightUnsigned
            BinaryOperator.StrictEquals -> ::TestEqualStrict
            BinaryOperator.StrictNotEquals -> ::TestNotEqualStrict
            BinaryOperator.SloppyEquals -> ::TestEqual
            BinaryOperator.SloppyNotEquals -> ::TestNotEqual
            BinaryOperator.LessThan -> ::TestLessThan
            BinaryOperator.LessThanEquals -> ::TestLessThanOrEqual
            BinaryOperator.GreaterThan -> ::TestGreaterThan
            BinaryOperator.GreaterThanEquals -> ::TestGreaterThanOrEqual
            BinaryOperator.Instanceof -> ::TestInstanceOf
            BinaryOperator.In -> ::TestIn
            BinaryOperator.And -> {
                visit(node.lhs)
                generator.ifHelper(::JumpIfToBooleanTrue) {
                    visit(node.rhs)
                }
                return
            }
            BinaryOperator.Or -> {
                visit(node.lhs)
                generator.ifHelper(::JumpIfToBooleanTrue, negateOp = true) {
                    visit(node.rhs)
                }
                return
            }
            BinaryOperator.Coalesce -> {
                visit(node.lhs)
                generator.ifHelper(::JumpIfNullish) {
                    visit(node.rhs)
                }
                return
            }
        }

        visit(node.lhs)

        val lhsReg = generator.reserveRegister()
        generator.add(Star(lhsReg))
        visit(node.rhs)
        generator.add(op(lhsReg))
    }

    override fun visitUnaryExpression(node: UnaryExpressionNode) {
        if (node.op == UnaryOperator.Delete) {
            when (val expr = node.expression) {
                is IdentifierReferenceNode -> generator.add(LdaFalse)
                !is MemberExpressionNode -> generator.add(LdaTrue)
                else -> if (expr.type == MemberExpressionNode.Type.Tagged) {
                    generator.add(LdaTrue)
                } else {
                    visit(expr.lhs)
                    val target = generator.reserveRegister()
                    generator.add(Star(target))

                    if (expr.type == MemberExpressionNode.Type.Computed) {
                        visit(expr.rhs)
                    } else {
                        val cpIndex = generator.intern((expr.rhs as IdentifierNode).identifierName)
                        generator.add(LdaConstant(cpIndex))
                    }

                    val deleteOp = if (node.scope.isStrict) ::DeletePropertyStrict else ::DeletePropertySloppy
                    generator.add(deleteOp(target))
                }
            }

            return
        }

        visit(node.expression)

        when (node.op) {
            UnaryOperator.Void -> generator.add(LdaUndefined)
            UnaryOperator.Typeof -> generator.add(TypeOf)
            UnaryOperator.Plus -> generator.add(ToNumber)
            UnaryOperator.Minus -> generator.add(Negate)
            UnaryOperator.BitwiseNot -> generator.add(BitwiseNot)
            UnaryOperator.Not -> generator.add(ToBooleanLogicalNot)
            else -> unreachable()
        }
    }

    override fun visitUpdateExpression(node: UpdateExpressionNode) {
        val op = if (node.isIncrement) Inc else Dec

        fun postfixGuard(action: () -> Unit) {
            val originalValue = if (node.isPostfix) {
                generator.reserveRegister().also {
                    generator.add(Star(it))
                }
            } else -1

            action()

            if (node.isPostfix)
                generator.add(Ldar(originalValue))
        }

        when (val target = node.target) {
            is IdentifierReferenceNode -> {
                visit(target)
                generator.add(ToNumeric)

                postfixGuard {
                    generator.add(op)
                    storeVariable(target.targetVar)
                }
            }
            is MemberExpressionNode -> {
                val objectReg = generator.reserveRegister()
                visit(target.lhs)
                generator.add(Star(objectReg))

                when (target.type) {
                    MemberExpressionNode.Type.Computed -> {
                        val keyReg = generator.reserveRegister()
                        visit(target.rhs)
                        generator.add(Star(keyReg))
                        generator.add(LdaKeyedProperty(objectReg))
                        postfixGuard {
                            generator.add(ToNumeric)
                            generator.add(op)
                            generator.add(StaKeyedProperty(objectReg, keyReg))
                        }
                    }
                    MemberExpressionNode.Type.NonComputed -> {
                        val nameIndex = generator.intern((target.rhs as IdentifierNode).identifierName)
                        generator.add(LdaNamedProperty(objectReg, nameIndex))
                        postfixGuard {
                            generator.add(ToNumeric)
                            generator.add(op)
                            generator.add(StaNamedProperty(objectReg, nameIndex))
                        }
                    }
                    MemberExpressionNode.Type.Tagged -> TODO()
                }
            }
            else -> TODO()
        }
    }

    override fun visitAssignmentExpression(node: AssignmentExpressionNode) {
        val lhs = node.lhs
        val rhs = node.rhs

        expect(node.op == null || node.op.isAssignable)

        fun loadRhsIntoAcc() {
            if (node.op != null) {
                // First figure out the new value
                visitBinaryExpression(BinaryExpressionNode(lhs, rhs, node.op))
            } else {
                visit(rhs)
            }
        }

        when (lhs) {
            is IdentifierReferenceNode -> {
                if (checkForConstReassignment(lhs))
                    return

                loadRhsIntoAcc()
                storeVariable(lhs.targetVar)

                return
            }
            is MemberExpressionNode -> {
                val objectReg = generator.reserveRegister()
                visit(lhs.lhs)
                generator.add(Star(objectReg))

                when (lhs.type) {
                    MemberExpressionNode.Type.Computed -> {
                        val keyReg = generator.reserveRegister()
                        visit(lhs.rhs)
                        generator.add(Star(keyReg))
                        loadRhsIntoAcc()
                        generator.add(StaKeyedProperty(objectReg, keyReg))
                    }
                    MemberExpressionNode.Type.NonComputed -> {
                        loadRhsIntoAcc()
                        val nameIndex = generator.intern((lhs.rhs as IdentifierNode).identifierName)
                        generator.add(StaNamedProperty(objectReg, nameIndex))
                    }
                    MemberExpressionNode.Type.Tagged -> TODO()
                }
            }
            else -> TODO()
        }
    }

    private fun loadVariable(variable: Variable) {
        if (variable.mode == Variable.Mode.Global) {
            if (variable.name == "undefined") {
                generator.add(LdaUndefined)
            } else {
                expect(variable.type == Variable.Type.Var)
                generator.add(LdaGlobal(generator.intern(variable.name)))
            }
            return
        }

        expect(variable.slot != -1)

        if (variable.isInlineable) {
            generator.add(Ldar(variable.slot))
        } else {
            val distance = currentScope!!.envDistanceFrom(variable.scope)
            if (distance == 0) {
                generator.add(LdaCurrentRecordSlot(variable.slot))
            } else {
                generator.add(LdaRecordSlot(variable.slot, distance))
            }
        }
    }

    private fun storeVariable(variable: Variable) {
        if (variable.mode == Variable.Mode.Global) {
            if (variable.name == "undefined") {
                if (variable.scope.isStrict) {
                    // TODO: Better error
                    val message = generator.intern("cannot assign to constant variable \"undefined\"")
                    generator.add(ThrowConstantError(message))
                } else {
                    return
                }
            } else {
                expect(variable.type == Variable.Type.Var)
                generator.add(StaGlobal(generator.intern(variable.name)))
            }
            return
        }

        expect(variable.slot != -1)

        if (variable.isInlineable) {
            generator.add(Star(variable.slot))
        } else {
            val distance = currentScope!!.envDistanceFrom(variable.scope)
            if (distance == 0) {
                generator.add(StaCurrentRecordSlot(variable.slot))
            } else {
                generator.add(StaRecordSlot(variable.slot, distance))
            }
        }
    }

    private fun checkForConstReassignment(node: VariableRefNode): Boolean {
        return if (node.targetVar.type == Variable.Type.Const) {
            val message = "cannot assign to constant variable \"${node.targetVar.name}\""
            generator.add(ThrowConstantError(generator.intern(message)))
            true
        } else false
    }

    override fun visitAwaitExpression(node: AwaitExpressionNode) {
        TODO()
    }

    override fun visitArgument(node: ArgumentNode) {
        if (node.isSpread)
            TODO()
        visit(node.expression)
    }

    override fun visitCommaExpression(node: CommaExpressionNode) {
        node.expressions.forEach(::visit)
    }

    override fun visitConditionalExpression(node: ConditionalExpressionNode) {
        visit(node.predicate)
        generator.ifElseHelper(::JumpIfToBooleanTrue, {
            visit(node.ifTrue)
        }, {
            visit(node.ifFalse)
        })
    }

    override fun visitMemberExpression(node: MemberExpressionNode) {
        // TODO: Deal with assigning to a MemberExpression

        val objectReg = generator.reserveRegister()
        visit(node.lhs)
        generator.add(Star(objectReg))

        when (node.type) {
            MemberExpressionNode.Type.Computed -> {
                visit(node.rhs)
                generator.add(LdaKeyedProperty(objectReg))
            }
            MemberExpressionNode.Type.NonComputed -> {
                val cpIndex = generator.intern((node.rhs as IdentifierNode).identifierName)
                generator.add(LdaNamedProperty(objectReg, cpIndex))
            }
            MemberExpressionNode.Type.Tagged -> TODO()
        }
    }

    private fun requiredRegisters(arguments: ArgumentList): Int {
        return if (argumentsMode(arguments) == ArgumentsMode.Spread) 1 else arguments.size
    }

    override fun visitCallExpression(node: CallExpressionNode) {
        val callableReg = generator.reserveRegister()
        val receiverReg = generator.reserveRegister()
        val target = node.target

        if (target is MemberExpressionNode) {
            visit(target.lhs)
            generator.add(Star(receiverReg))

            when (target.type) {
                MemberExpressionNode.Type.Computed -> {
                    visit(target.rhs)
                    generator.add(LdaKeyedProperty(receiverReg))
                }
                MemberExpressionNode.Type.NonComputed -> {
                    val cpIndex = generator.intern((target.rhs as IdentifierNode).identifierName)
                    generator.add(LdaNamedProperty(receiverReg, cpIndex))
                }
                MemberExpressionNode.Type.Tagged -> TODO()
            }

            generator.add(Star(callableReg))
        } else {
            visit(target)
            generator.add(Star(callableReg))
            generator.add(LdaUndefined)
            generator.add(Star(receiverReg))
        }

        val (argumentsMode, argumentRegisters) = loadArguments(node.arguments)

        if (argumentsMode == ArgumentsMode.Normal) {
            generator.add(Call(callableReg, receiverReg, argumentRegisters))
        } else {
            expect(argumentRegisters.size == 1)
            generator.add(CallWithArgArray(callableReg, receiverReg, argumentRegisters[0]))
        }
    }

    override fun visitNewExpression(node: NewExpressionNode) {
        val target = generator.reserveRegister()
        visit(node.target)
        generator.add(Star(target))

        val (argumentsMode, argumentRegisters) = loadArguments(node.arguments)

        // TODO: Proper new.target
        if (argumentsMode == ArgumentsMode.Normal) {
            generator.add(Construct(target, target, argumentRegisters))
        } else {
            expect(argumentRegisters.size == 1)
            generator.add(ConstructWithArgArray(target, target, argumentRegisters[0]))
        }
    }

    enum class ArgumentsMode {
        Normal,
        Spread,
    }

    private fun argumentsMode(arguments: ArgumentList): ArgumentsMode {
        return if (arguments.any { it.isSpread }) {
            ArgumentsMode.Spread
        } else ArgumentsMode.Normal
    }

    private fun loadArguments(arguments: ArgumentList): Pair<ArgumentsMode, List<Register>> {
        val mode = argumentsMode(arguments)
        val registers = mutableListOf<Register>()

        return if (mode == ArgumentsMode.Spread) {
            return ArgumentsMode.Spread to listOf(loadArgumentsWithSpread(arguments))
        } else {
            for (argument in arguments) {
                visit(argument.expression)
                val argReg = generator.reserveRegister()
                registers.add(argReg)
                generator.add(Star(argReg))
            }

            ArgumentsMode.Normal to registers
        }
    }

    private fun loadArgumentsWithSpread(arguments: ArgumentList): Register {
        val arrayReg = generator.reserveRegister()
        generator.add(CreateArray)
        generator.add(Star(arrayReg))
        var indexReg = -1
        var indexUsable = true

        for ((index, argument) in arguments.withIndex()) {
            if (argument.isSpread) {
                indexUsable = false
                indexReg = generator.reserveRegister()
                generator.add(LdaInt(index))
                generator.add(Star(indexReg))

                visit(argument.expression)
                generator.add(GetIterator)
                iterateValues(setOf()) {
                    generator.add(StaArray(arrayReg, indexReg))
                    generator.add(Ldar(indexReg))
                    generator.add(Inc)
                    generator.add(Star(indexReg))
                }
            } else {
                visit(argument.expression)
                if (indexUsable) {
                    generator.add(StaArrayIndex(arrayReg, index))
                } else {
                    generator.add(StaArray(arrayReg, indexReg))
                    generator.add(Ldar(indexReg))
                    generator.add(Inc)
                    generator.add(Star(indexReg))
                }
            }
        }

        return arrayReg
    }

    private fun iterateValues(labels: Set<String>, action: () -> Unit) {
        val iteratorReg = generator.reserveRegister()
        generator.add(Star(iteratorReg))

        val headBlock = generator.makeBlock()
        val isExhaustedBlock = generator.makeBlock()
        val isNotExhaustedBlock = generator.makeBlock()

        generator.add(Jump(headBlock))

        generator.currentBlock = headBlock
        val iteratorResultReg = generator.reserveRegister()
        generator.add(Ldar(iteratorReg))
        generator.add(IteratorNext)
        generator.add(Star(iteratorResultReg))
        generator.add(IteratorResultDone)
        generator.add(JumpIfTrue(isExhaustedBlock, isNotExhaustedBlock))

        generator.currentBlock = isNotExhaustedBlock
        generator.add(Ldar(iteratorResultReg))
        generator.add(IteratorResultValue)

        enterBreakableScope(isExhaustedBlock, labels)
        enterContinuableScope(headBlock, labels)
        action()
        exitBreakableScope()
        exitContinuableScope()

        generator.addIfNotTerminated(Jump(headBlock))

        generator.currentBlock = isExhaustedBlock
    }

    override fun visitOptionalExpression(node: OptionalExpressionNode) {
        TODO()
    }

    override fun visitSuperPropertyExpression(node: SuperPropertyExpressionNode) {
        TODO()
    }

    override fun visitSuperCallExpression(node: SuperCallExpressionNode) {
        TODO()
    }

    override fun visitImportCallExpression(node: ImportCallExpressionNode) {
        TODO()
    }

    override fun visitYieldExpression(node: YieldExpressionNode) {
        if (node.expression == null) {
            generator.add(LdaUndefined)
        } else {
            visit(node.expression)
        }

        val continuationBlock = generator.makeBlock()
        generator.add(Yield(continuationBlock))
        generator.currentBlock = continuationBlock
    }

    override fun visitParenthesizedExpression(node: ParenthesizedExpressionNode) {
        visit(node.expression)
    }

    override fun visitTemplateLiteral(node: TemplateLiteralNode) {
        val templateLiteral = generator.reserveRegister()

        for ((index, part) in node.parts.withIndex()) {
            if (part is StringLiteralNode) {
                val reg = generator.intern(part.value)
                generator.add(LdaConstant(reg))
            } else {
                visit(part)
                generator.add(ToString)
            }

            if (index != 0) {
                generator.add(StringAppend(templateLiteral))
            } else {
                generator.add(Star(templateLiteral))
            }
        }

        generator.add(Ldar(templateLiteral))
    }

    override fun visitRegExpLiteral(node: RegExpLiteralNode) {
        TODO()
    }

    override fun visitImportMetaExpression() {
        TODO()
    }

    override fun visitNewTargetExpression() {
        TODO()
    }

    override fun visitArrayLiteral(node: ArrayLiteralNode) {
        generator.add(CreateArray)
        val arrayReg = generator.reserveRegister()
        generator.add(Star(arrayReg))
        for ((index, element) in node.elements.withIndex()) {
            when (element.type) {
                ArrayElementNode.Type.Elision -> continue
                ArrayElementNode.Type.Spread -> TODO()
                ArrayElementNode.Type.Normal -> {
                    visit(element.expression!!)
                    generator.add(StaArrayIndex(arrayReg, index))
                }
            }
        }
        generator.add(Ldar(arrayReg))
    }

    override fun visitObjectLiteral(node: ObjectLiteralNode) {
        generator.add(CreateObject)
        val objectReg = generator.reserveRegister()
        generator.add(Star(objectReg))

        for (property in node.list) {
            when (property) {
                is SpreadProperty -> TODO()
                is MethodProperty -> {
                    val method = property.method

                    fun makeFunction() {
                        // TODO: This probably isn't correct
                        val functionNode = FunctionExpressionNode(
                            null,
                            method.parameters,
                            method.body,
                            method.kind.toFunctionKind(),
                        )
                        functionNode.functionScope = method.functionScope
                        functionNode.scope = method.scope
                        visitFunctionExpression(functionNode)
                    }

                    when (method.kind) {
                        MethodDefinitionNode.Kind.Normal -> storeObjectProperty(
                            objectReg,
                            method.propName,
                            ::makeFunction
                        )
                        MethodDefinitionNode.Kind.Getter, MethodDefinitionNode.Kind.Setter -> {
                            val propertyReg = generator.reserveRegister()
                            val methodReg = generator.reserveRegister()
                            val op = if (method.kind == MethodDefinitionNode.Kind.Getter) {
                                ::DefineGetterProperty
                            } else ::DefineSetterProperty

                            visitPropertyName(method.propName)
                            generator.add(Star(propertyReg))

                            makeFunction()
                            generator.add(Star(methodReg))

                            generator.add(op(objectReg, propertyReg, methodReg))
                        }
                        else -> TODO()
                    }
                }
                is ShorthandProperty -> {
                    visit(property.key)
                    generator.add(StaNamedProperty(objectReg, generator.intern(property.key.identifierName)))
                }
                is KeyValueProperty -> {
                    storeObjectProperty(objectReg, property.key) {
                        visit(property.value)
                    }
                }
            }
        }

        generator.add(Ldar(objectReg))
    }

    override fun visitPropertyName(node: PropertyName) {
        if (node.type == PropertyName.Type.Identifier) {
            val nameIndex = generator.intern((node.expression as IdentifierNode).identifierName)
            generator.add(LdaConstant(nameIndex))
        } else visit(node.expression)
    }

    private fun storeObjectProperty(objectReg: Int, property: PropertyName, valueProducer: () -> Unit) {
        if (property.type == PropertyName.Type.Identifier) {
            valueProducer()
            val name = (property.expression as IdentifierNode).identifierName
            generator.add(StaNamedProperty(objectReg, generator.intern(name)))
            return
        }

        when (property.type) {
            PropertyName.Type.String -> visit(property.expression)
            PropertyName.Type.Number -> visit(property.expression)
            PropertyName.Type.Computed -> {
                visit(property.expression)
                generator.add(ToString)
            }
            PropertyName.Type.Identifier -> unreachable()
        }

        val keyReg = generator.reserveRegister()
        generator.add(Star(keyReg))
        valueProducer()
        generator.add(StaKeyedProperty(objectReg, keyReg))
    }

    override fun visitBooleanLiteral(node: BooleanLiteralNode) {
        generator.add(if (node.value) LdaTrue else LdaFalse)
    }

    override fun visitStringLiteral(node: StringLiteralNode) {
        generator.add(LdaConstant(generator.intern(node.value)))
    }

    override fun visitNumericLiteral(node: NumericLiteralNode) {
        val value = node.value
        if (value.isFinite() && floor(value) == value && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            generator.add(LdaInt(value.toInt()))
        } else {
            generator.add(LdaConstant(generator.intern(value)))
        }
    }

    override fun visitBigIntLiteral(node: BigIntLiteralNode) {
        val bigint = BigInteger(node.value, node.type.radix)
        generator.add(LdaConstant(generator.intern(bigint)))
    }

    override fun visitNullLiteral() {
        generator.add(LdaNull)
    }

    override fun visitThisLiteral() {
        generator.add(Ldar(0))
    }

    private fun enterBreakableScope(targetBlock: Block, labels: Set<String>) {
        breakableScopes.add(LabelledBlock(labels, targetBlock))
    }

    private fun exitBreakableScope() {
        breakableScopes.removeLast()
    }

    private fun enterContinuableScope(targetBlock: Block, labels: Set<String>) {
        continuableScopes.add(LabelledBlock(labels, targetBlock))
    }

    private fun exitContinuableScope() {
        continuableScopes.removeLast()
    }
}
