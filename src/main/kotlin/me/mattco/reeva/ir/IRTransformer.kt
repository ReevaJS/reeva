package me.mattco.reeva.ir

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.interpreter.InterpRuntime
import me.mattco.reeva.ir.FunctionBuilder.*
import me.mattco.reeva.ir.OpcodeType.*
import me.mattco.reeva.ir.opcodes.OpcodeList
import me.mattco.reeva.parser.Scope
import me.mattco.reeva.parser.Variable
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable
import java.math.BigInteger
import kotlin.math.floor

class FunctionInfo(
    val name: String?,
    val code: OpcodeList,
    val constantPool: Array<Any>,
    val handlers: Array<Handler>,
    val registerCount: Int, // includes argCount
    val argCount: Int,
    val topLevelSlots: Int,
    val isStrict: Boolean,
    val isTopLevelScript: Boolean = false,
)

class IRTransformer : ASTVisitor {
    private lateinit var builder: FunctionBuilder

    fun transform(node: ASTNode): FunctionInfo {
        if (::builder.isInitialized)
            throw IllegalStateException("Cannot re-use an IRTransformer")

        if (node is ModuleNode)
            TODO()

        expect(node is ScriptNode)

        builder = FunctionBuilder()

        globalDeclarationInstantiation(node.scope) {
            visit(node.statements)
            add(Return)
        }

        return FunctionInfo(
            null,
            OpcodeList(builder.opcodes),
            builder.constantPool.toTypedArray(),
            builder.handlers.map(IRHandler::toHandler).toTypedArray(),
            builder.registerCount,
            1,
            node.scope.numSlots,
            node.scope.isStrict,
            isTopLevelScript = true
        )
    }

    private fun enterScope(scope: Scope) {
        if (scope.requiresEnv) {
            builder.nestedContexts++
            add(PushEnv, scope.numSlots)
            scope.envVariables.forEach { variable ->
                variable.slot = builder.envVarCount++
            }
        }

        scope.inlineableVariables.filter {
            it.mode != Variable.Mode.Parameter
        }.forEach {
            it.slot = nextFreeReg()
        }
    }

    private fun exitScope(scope: Scope) {
        scope.inlineableVariables.filter {
            it.mode != Variable.Mode.Parameter
        }.forEach {
            markRegFree(it.slot)
        }

        if (scope.requiresEnv) {
            builder.nestedContexts--
            add(PopCurrentEnv)
        }
    }

    override fun visitBlock(node: BlockNode) {
        enterScope(node.scope)
        super.visitBlock(node)
        exitScope(node.scope)
    }

    override fun visitExpressionStatement(node: ExpressionStatementNode) {
        visit(node.node)
    }

    override fun visitIfStatement(node: IfStatementNode) {
        visit(node.condition)

        if (node.falseBlock == null) {
            val endLabel = label()
            jump(endLabel, JumpIfToBooleanFalse)
            visit(node.trueBlock)
            place(endLabel)
        } else {
            val falseLabel = label()
            val endLabel = label()
            jump(falseLabel, JumpIfToBooleanFalse)
            visit(node.trueBlock)
            jump(endLabel)
            place(falseLabel)
            visit(node.falseBlock)
            place(endLabel)
        }
    }

    override fun visitDoWhileStatement(node: DoWhileStatementNode) {
        val loopStart = label()
        val loopEnd = label()

        place(loopStart)

        builder.pushBlock(LoopBlock(loopStart, loopEnd))
        visit(node.body)
        builder.popBlock()

        visit(node.condition)
        jump(loopStart, JumpIfToBooleanTrue)
        place(loopEnd)
    }

    override fun visitWhileStatement(node: WhileStatementNode) {
        val loopStart = label()
        val loopEnd = label()

        place(loopStart)
        visit(node.condition)
        jump(loopEnd, JumpIfToBooleanFalse)

        builder.pushBlock(LoopBlock(loopStart, loopEnd))
        visit(node.body)
        builder.popBlock()

        jump(loopStart)
        place(loopEnd)
    }

    override fun visitForStatement(node: ForStatementNode) {
        if (node.initScope != null) {
            if (node.initScope.requiresEnv) {
                add(PushEnv, node.initScope.numSlots)
                builder.nestedContexts++
                node.initScope.envVariables.forEachIndexed { index, variable ->
                    variable.slot = index
                }
            }

            node.initScope.inlineableVariables.forEach {
                it.slot = nextFreeReg()
            }
        }

        if (node.initializer != null)
            visit(node.initializer)

        val loopStart = label()
        val loopEnd = label()
        val continueTarget = label()
        place(loopStart)

        if (node.condition != null) {
            visit(node.condition)
            jump(loopEnd, JumpIfToBooleanFalse)
        }

        builder.pushBlock(LoopBlock(continueTarget, loopEnd))
        visit(node.body)
        builder.popBlock()

        place(continueTarget)

        if (node.incrementer != null)
            visit(node.incrementer)

        jump(loopStart)

        place(loopEnd)

        if (node.initScope != null) {
            node.initScope.inlineableVariables.forEach {
                markRegFree(it.slot)
            }

            if (node.initScope.requiresEnv) {
                builder.nestedContexts--
                add(PopCurrentEnv)
            }
        }
    }

    override fun visitForIn(node: ForInNode) {
        TODO()
    }

    override fun visitForOf(node: ForOfNode) {
        visit(node.expression)
        add(GetIterator)
        val iter = nextFreeReg()
        add(Star, iter)

        val next = nextFreeReg()
        add(LdaNamedProperty, iter, loadConstant("next"))
        add(Star, next)
        add(CallRuntime, InterpRuntime.ThrowIfIteratorNextNotCallable, next, 1)

        if (node.scope.requiresEnv) {
            node.scope.envVariables.forEachIndexed { index, variable ->
                variable.slot = index
            }
            builder.nestedContexts++
        }

        node.scope.inlineableVariables.forEach {
            it.slot = nextFreeReg()
        }

        val loopStart = label()
        val loopEnd = label()

        place(loopStart)

        if (node.scope.requiresEnv)
            add(PushEnv, node.scope.numSlots)

        add(Call0, next, iter)
        val nextResult = nextFreeReg()
        add(Star, nextResult)
        add(CallRuntime, InterpRuntime.ThrowIfIteratorReturnNotObject, nextResult, 1)
        add(LdaNamedProperty, nextResult, loadConstant("done"))
        jump(loopEnd, JumpIfToBooleanTrue)

        add(LdaNamedProperty, nextResult, loadConstant("value"))

        when (node.decl) {
            is VariableDeclarationNode -> {
                val decl = node.decl.declarations[0]
                val variable = decl.identifier.variable
                if (variable.isInlineable) {
                    add(Star, variable.slot)
                } else {
                    storeEnvVariableRef(variable, node.scope)
                }
            }
            is LexicalDeclarationNode -> {
                val decl = node.decl.declarations[0]
                val variable = decl.identifier.variable
                if (variable.isInlineable) {
                    add(Star, variable.slot)
                } else {
                    storeEnvVariableRef(variable, node.scope)
                }
            }
            is BindingIdentifierNode -> {
                val variable = node.decl.variable
                if (variable.isInlineable) {
                    add(Star, variable.slot)
                } else {
                    storeEnvVariableRef(variable, node.scope)
                }
            }
        }

        visit(node.body)

        if (node.scope.requiresEnv) {
            builder.nestedContexts--
            add(PopCurrentEnv)
        }

        jump(loopStart)
        place(loopEnd)

        node.scope.inlineableVariables.forEach {
            markRegFree(it.slot)
        }
    }

    override fun visitForAwaitOf(node: ForAwaitOfNode) {
        TODO()
    }

    override fun visitLabelledStatement(node: LabelledStatementNode) {
        TODO()
    }

    override fun visitThrowStatement(node: ThrowStatementNode) {
        visit(node.expr)
        add(Throw)
    }

    override fun visitTryStatement(node: TryStatementNode) {
        val (tryStart, tryEnd) = label() to label()
        val (catchStart, catchEnd) = if (node.catchNode != null) {
            label() to label()
        } else null to null
        val finallyStart = if (node.finallyBlock != null) label() else null
        val blockEnd = label()

        val block = TryCatchBlock(tryStart, tryEnd, catchStart, finallyStart, node.finallyBlock)
        builder.pushBlock(block)

        place(tryStart)
        visit(node.tryBlock)
        place(tryEnd)

        if (node.finallyBlock != null) {
            visit(node.finallyBlock)
            jump(blockEnd)
        }

        if (node.catchNode != null) {
            if (finallyStart == null)
                jump(blockEnd)

            place(catchStart!!)
            var mustPopEnv = false

            if (node.catchNode.catchParameter != null) {
                if (node.catchNode.scope.requiresEnv) {
                    builder.nestedContexts++
                    add(PushEnv, node.catchNode.scope.numSlots)
                    node.catchNode.scope.envVariables.forEachIndexed { index, variable ->
                        variable.slot = index
                    }
                    mustPopEnv = true
                }

                val param = node.catchNode.catchParameter
                if (param.variable.isInlineable) {
                    param.variable.slot = nextFreeReg()
                    add(Star, param.variable.slot)
                } else {
                    storeEnvVariableRef(param.variable, node.catchNode.scope)
                }
            }

            visit(node.catchNode.block)
            place(catchEnd!!)

            if (mustPopEnv)
                add(PopCurrentEnv)

            if (node.catchNode.catchParameter?.variable?.isInlineable == true)
                markRegFree(node.catchNode.catchParameter.variable.slot)

            if (node.finallyBlock != null) {
                visit(node.finallyBlock)
                jump(blockEnd)
            }

            val handlers = block.getHandlersForRegion(
                IRHandler(tryStart, tryEnd.shift(-1), catchStart, isCatch = true, builder.nestedContexts),
            )
            builder.handlers.addAll(handlers)
        }

        if (node.finallyBlock != null) {
            // Throw variant of finally block
            val exceptionReg = nextFreeReg()

            place(finallyStart!!)
            add(Star, exceptionReg)
            visit(node.finallyBlock)
            add(Ldar, exceptionReg)
            add(Throw)
            markRegFree(exceptionReg)

            if (node.catchNode != null) {
                builder.addHandler(catchStart!!, catchEnd!!.shift(-1), finallyStart, isCatch = false)
            } else {
                val handlers = block.getHandlersForRegion(
                    IRHandler(tryStart, tryEnd, finallyStart, isCatch = false, builder.nestedContexts),
                )
                builder.handlers.addAll(handlers)
            }
        }

        place(blockEnd)

        builder.popBlock()
    }

    override fun visitBreakStatement(node: BreakStatementNode) {
        if (node.label != null)
            TODO()

        val (targetIndex, targetLabel) = getLoopFlowTarget(true)
        visitScopedFinallyBlocks(targetIndex) {
            builder.goto(targetLabel, builder.blocks[targetIndex].contextDepth)
        }
    }

    override fun visitContinueStatement(node: ContinueStatementNode) {
        if (node.label != null)
            TODO()

        val (targetIndex, targetLabel) = getLoopFlowTarget(false)
        visitScopedFinallyBlocks(targetIndex) {
            builder.goto(targetLabel, builder.blocks[targetIndex].contextDepth)
        }
    }

    override fun visitReturnStatement(node: ReturnStatementNode) {
        node.expression?.also(::visit)
        val resultReg = nextFreeReg()
        add(Star, resultReg)

        visitScopedFinallyBlocks {
            add(Ldar, resultReg)
            add(Return)
        }
    }

    private fun getLoopFlowTarget(isBreak: Boolean): Pair<Int, Label> {
        val targetIndex = builder.blocks.indexOfLast {
            it is LoopBlock || (isBreak && it is SwitchBlock)
        }
        expect(targetIndex >= 0)
        val targetBlock = builder.blocks[targetIndex]
        return targetIndex to if (isBreak) {
            if (targetBlock is SwitchBlock) {
                targetBlock.breakTarget
            } else (targetBlock as LoopBlock).breakTarget
        } else (targetBlock as LoopBlock).continueTarget
    }

    private fun visitScopedFinallyBlocks(blockStartIndex: Int = 0, cleanupBlock: () -> Unit) {
        val tryCatchBlocks = builder.blocks.subList(blockStartIndex, builder.blocks.size)
            .filterIsInstance<TryCatchBlock>()
            .asReversed()

        val blockStartLabels = mutableMapOf<TryCatchBlock, Label>()

        for (block in tryCatchBlocks) {
            if (block.finallyNode == null)
                continue
            blockStartLabels[block] = place(label())
            visit(block.finallyNode)
        }

        cleanupBlock()

        val end = place(label()).shift(-1)

        for ((block, start) in blockStartLabels)
            block.excludedRegions.add(start to end)
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
            add(LdaUndefined)
        }

        storeVariable(variable, declaration.scope)
    }

    override fun visitDebuggerStatement() {
        add(DebugBreakpoint)
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
        loadVariable(node.targetVar, node.scope)
        if (node.targetVar.mode == Variable.Mode.Global)
            return

        val declarationStart = node.targetVar.source.sourceStart
        val useStart = node.sourceStart
        if (useStart.index < declarationStart.index && variable.type != Variable.Type.Var) {
            // We need to check if the variable has been initialized
            add(ThrowUseBeforeInitIfEmpty, loadConstant(node.identifierName))
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
            node.parameterScope,
            node.bodyScope,
            node.scope.isStrict,
            false,
        )
    }

    override fun visitArrowFunction(node: ArrowFunctionNode) {
        visitFunctionHelper(
            "<anonymous>",
            node.parameters,
            node.body,
            node.parameterScope,
            node.bodyScope,
            node.scope.isStrict,
            true,
        )
    }

    private fun globalDeclarationInstantiation(
        scope: Scope,
        evaluationBlock: () -> Unit
    ) {
        enterScope(scope)

        val varDecls = scope.declaredVariables.filter { it.type == Variable.Type.Var }.map { it.source }
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()
        val declaredFunctionNames = mutableSetOf<String>()

        for (varDecl in varDecls.asReversed()) {
            if (varDecl !is FunctionDeclarationNode || varDecl.identifier.identifierName in declaredFunctionNames)
                continue

            functionsToInitialize.add(0, varDecl)
            declaredFunctionNames.add(varDecl.identifier.identifierName)
        }

        val declaredVarNames = mutableSetOf<String>()

        for (varDecl in varDecls) {
            if (varDecl is FunctionDeclarationNode)
                continue

            val name = varDecl.variable.name
            if (name !in declaredFunctionNames && name !in declaredVarNames) {
                declaredVarNames.add(name)
            }
        }

        val lexNames = scope.declaredVariables.filter { it.type != Variable.Type.Var }.map { it.name }

        val array = DeclarationsArray(declaredVarNames.toList(), lexNames, declaredFunctionNames.toList())
        add(DeclareGlobals, loadConstant(array))

        commonInstantiation(varDecls, declaredFunctionNames, functionsToInitialize)

        evaluationBlock()

        exitScope(scope)
    }

    private fun functionDeclarationInstantiation(
        parameters: ParameterList,
        parameterScope: Scope,
        bodyScope: Scope,
        isLexical: Boolean,
        isStrict: Boolean,
        evaluationBlock: () -> Unit,
    ) {
        val parameterNames = parameters.map { it.name }
        val simpleParameterList = parameters.isSimple()
        val hasParameterExpressions = parameters.any { it.initializer != null }

        val lexNames = bodyScope.declaredVariables.filter { it.type != Variable.Type.Var }.map { it.name }
        val varDecls = bodyScope.declaredVariables.filter {
            it.type == Variable.Type.Var && it.mode != Variable.Mode.Parameter
        }.map { it.source }

        val functionNames = mutableSetOf<String>()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()

        for (decl in varDecls.asReversed()) {
            if (decl !is FunctionDeclarationNode || decl.identifier.identifierName in functionNames)
                continue

            functionsToInitialize.add(0, decl)
            functionNames.add(decl.identifier.identifierName)
        }

        val argumentsObjectNeeded = when {
            isLexical -> false
            "arguments" in parameterNames -> false
            !hasParameterExpressions && ("arguments" in functionNames || "arguments" in lexNames) -> false
            else -> true
        }

        if (argumentsObjectNeeded && bodyScope.possiblyReferencesArguments) {
            if (isStrict || !simpleParameterList) {
                add(CreateUnmappedArgumentsObject)
            } else {
                add(CreateMappedArgumentsObject)
            }
        }

        enterScope(parameterScope)

        parameters.distinctBy { it.identifier.identifierName }.forEachIndexed { index, param ->
            val register = index + 1
            val isInlineable = param.variable.isInlineable

            if (isInlineable)
                param.variable.slot = register

            if (param.initializer != null) {
                // Check if the parameter is undefined
                val skipDefault = label()

                add(Ldar, register)
                jump(skipDefault, JumpIfNotUndefined)

                visit(param.initializer)
                if (isInlineable) {
                    add(Star, register)
                } else {
                    add(StaCurrentEnv, param.variable.slot)
                }

                place(skipDefault)
            } else if (!isInlineable) {
                add(Ldar, register)
                add(StaCurrentEnv, param.variable.slot)
            }
        }

        if (bodyScope != parameterScope)
            enterScope(bodyScope)

        commonInstantiation(varDecls, functionNames, functionsToInitialize)

        evaluationBlock()

        if (bodyScope != parameterScope)
            exitScope(bodyScope)
        exitScope(parameterScope)
    }

    private fun commonInstantiation(
        varDecls: List<VariableSourceNode>,
        functionNames: Set<String>,
        functions: List<FunctionDeclarationNode>,
    ) {
        val instantiatedVarNames = functionNames.toMutableSet()

        for (varDecl in varDecls) {
            if (varDecl is FunctionDeclarationNode || varDecl.name in instantiatedVarNames)
                continue

            instantiatedVarNames.add(varDecl.name)
            val variable = varDecl.variable

            if (variable.name !in instantiatedVarNames) {
                instantiatedVarNames.add(variable.name)

                if (variable.possiblyUsedBeforeDecl) {
                    add(LdaUndefined)
                    storeVariable(variable, varDecl.scope)
                }
            }
        }

        for (function in functions) {
            visitFunctionHelper(
                function.name,
                function.parameters,
                function.body,
                function.parameterScope,
                function.bodyScope,
                function.scope.isStrict || function.parameterScope.isStrict || function.bodyScope.isStrict,
                false, // TODO
            )

            storeVariable(function.variable, function.scope)
        }
    }

    private fun visitFunctionHelper(
        name: String,
        parameters: ParameterList,
        body: ASTNode,
        parameterScope: Scope,
        bodyScope: Scope,
        isStrict: Boolean,
        isLexical: Boolean,
    ) {
        val prevBuilder = builder
        builder = FunctionBuilder(parameters.size + 1)

        functionDeclarationInstantiation(
            parameters,
            parameterScope,
            bodyScope,
            isLexical,
            isStrict
        ) {
            // body's scope is the same as the function's scope (the scope we receive
            // as a parameter). We don't want to re-enter the same scope, so we explicitly
            // call super.visitBlock if necessary.
            if (body is BlockNode) {
                super.visitBlock(body)
            } else visit(body)

            if (builder.opcodes.lastOrNull()?.type != Return) {
                add(LdaUndefined)
                add(Return)
            }
        }

        val info = FunctionInfo(
            name,
            OpcodeList(builder.opcodes).also(PeepholeOptimizer::optimize),
            builder.constantPool.toTypedArray(),
            builder.handlers.map(IRHandler::toHandler).toTypedArray(),
            builder.registerCount,
            parameters.size + 1,
            parameterScope.numSlots,
            isStrict,
            isTopLevelScript = false,
        )

        builder = prevBuilder
        add(CreateClosure, loadConstant(info))
    }

    override fun visitClassDeclaration(node: ClassDeclarationNode) {
        TODO()
    }

    override fun visitClassExpression(node: ClassExpressionNode) {
        TODO()
    }

    override fun visitBinaryExpression(node: BinaryExpressionNode) {
        val type = when (node.operator) {
            BinaryOperator.Add -> Add
            BinaryOperator.Sub -> Sub
            BinaryOperator.Mul -> Mul
            BinaryOperator.Div -> Div
            BinaryOperator.Exp -> Exp
            BinaryOperator.Mod -> Mod
            BinaryOperator.And -> {
                visit(node.lhs)
                add(ToBoolean)
                val skip = label()
                jump(skip, JumpIfFalse)
                visit(node.rhs)
                place(skip)
                return
            }
            BinaryOperator.Or -> {
                visit(node.lhs)
                val skip = label()
                jump(skip, JumpIfToBooleanTrue)
                visit(node.rhs)
                place(skip)
                return
            }
            BinaryOperator.Coalesce -> {
                visit(node.lhs)
                val skip = label()
                jump(skip, JumpIfNotNullish)
                visit(node.rhs)
                place(skip)
                return
            }
            BinaryOperator.BitwiseAnd -> BitwiseAnd
            BinaryOperator.BitwiseOr -> BitwiseOr
            BinaryOperator.BitwiseXor -> BitwiseXor
            BinaryOperator.Shl -> ShiftLeft
            BinaryOperator.Shr -> ShiftRight
            BinaryOperator.UShr -> ShiftRightUnsigned
            BinaryOperator.StrictEquals -> TestEqualStrict
            BinaryOperator.StrictNotEquals -> TestNotEqualStrict
            BinaryOperator.SloppyEquals -> TestEqual
            BinaryOperator.SloppyNotEquals -> TestNotEqual
            BinaryOperator.LessThan -> TestLessThan
            BinaryOperator.LessThanEquals -> TestLessThanOrEqual
            BinaryOperator.GreaterThan -> TestGreaterThan
            BinaryOperator.GreaterThanEquals -> TestGreaterThanOrEqual
            BinaryOperator.Instanceof -> TestInstanceOf
            BinaryOperator.In -> TestIn
        }

        visit(node.lhs)
        val reg = nextFreeReg()
        add(Star, reg)
        visit(node.rhs)
        add(type, reg)
        markRegFree(reg)
    }

    override fun visitUnaryExpression(node: UnaryExpressionNode) {
        if (node.op == UnaryOperator.Delete) {
            when (val expr = node.expression) {
                is IdentifierReferenceNode -> add(LdaFalse)
                !is MemberExpressionNode -> add(LdaTrue)
                else -> if (expr.type == MemberExpressionNode.Type.Tagged) {
                    add(LdaTrue)
                } else {
                    visit(expr.lhs)
                    val target = nextFreeReg()
                    add(Star, target)

                    if (expr.type == MemberExpressionNode.Type.Computed) {
                        visit(expr.rhs)
                    } else {
                        add(LdaConstant, loadConstant((expr.rhs as IdentifierNode).identifierName))
                    }

                    if (node.scope.isStrict) {
                        add(DeletePropertyStrict, target)
                    } else add(DeletePropertySloppy, target)

                    markRegFree(target)
                }
            }

            return
        }

        visit(node.expression)

        when (node.op) {
            UnaryOperator.Void -> add(LdaUndefined)
            UnaryOperator.Typeof -> add(TypeOf)
            UnaryOperator.Plus -> TODO()
            UnaryOperator.Minus -> add(Negate)
            UnaryOperator.BitwiseNot -> add(BitwiseNot)
            UnaryOperator.Not -> add(ToBooleanLogicalNot)
            else -> unreachable()
        }
    }

    override fun visitUpdateExpression(node: UpdateExpressionNode) {
        val op = if (node.isIncrement) Inc else Dec

        fun postfixGuard(action: () -> Unit) {
            val originalValue = if (node.isPostfix) {
                nextFreeReg().also { add(Star, it) }
            } else -1

            action()

            if (node.isPostfix) {
                add(Ldar, originalValue)
                markRegFree(originalValue)
            }
        }

        when (val target = node.target) {
            is IdentifierReferenceNode -> {
                visit(target)
                add(ToNumeric)

                postfixGuard {
                    add(op)
                    storeVariable(target.targetVar, target.scope)
                }
            }
            is MemberExpressionNode -> {
                val objectReg = nextFreeReg()
                visit(target.lhs)
                add(Star, objectReg)

                when (target.type) {
                    MemberExpressionNode.Type.Computed -> {
                        val keyReg = nextFreeReg()
                        visit(target.rhs)
                        add(Star, keyReg)
                        add(LdaKeyedProperty, objectReg)
                        postfixGuard {
                            add(ToNumeric)
                            add(op)
                            add(StaKeyedProperty, objectReg, keyReg)
                        }
                        markRegFree(keyReg)
                    }
                    MemberExpressionNode.Type.NonComputed -> {
                        add(LdaNamedProperty, objectReg, loadConstant((target.rhs as IdentifierNode).identifierName))
                        postfixGuard {
                            add(ToNumeric)
                            add(op)
                            add(StaNamedProperty, objectReg, loadConstant(target.rhs.identifierName))
                        }
                    }
                    MemberExpressionNode.Type.Tagged -> TODO()
                }

                markRegFree(objectReg)
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
                storeVariable(lhs.targetVar, lhs.scope)

                return
            }
            is MemberExpressionNode -> {
                val objectReg = nextFreeReg()
                visit(lhs.lhs)
                add(Star, objectReg)

                when (lhs.type) {
                    MemberExpressionNode.Type.Computed -> {
                        val keyReg = nextFreeReg()
                        visit(lhs.rhs)
                        add(Star, keyReg)
                        loadRhsIntoAcc()
                        add(StaKeyedProperty, objectReg, keyReg)
                        markRegFree(keyReg)
                    }
                    MemberExpressionNode.Type.NonComputed -> {
                        loadRhsIntoAcc()
                        add(StaNamedProperty, objectReg, loadConstant((lhs.rhs as IdentifierNode).identifierName))
                    }
                    MemberExpressionNode.Type.Tagged -> TODO()
                }

                markRegFree(objectReg)
            }
            else -> TODO()
        }
    }

    private fun loadVariable(variable: Variable, currentScope: Scope) {
        when {
            variable.mode == Variable.Mode.Global -> {
                if (variable.name == "undefined") {
                    add(LdaUndefined)
                } else add(LdaGlobal, loadConstant(variable.name))
            }
            variable.isInlineable -> add(Ldar, variable.slot)
            else -> loadEnvVariableRef(variable, currentScope)
        }
    }

    private fun storeVariable(variable: Variable, currentScope: Scope) {
        when {
            variable.mode == Variable.Mode.Global -> add(StaGlobal, loadConstant(variable.name))
            variable.isInlineable -> add(Star, variable.slot)
            else -> storeEnvVariableRef(variable, currentScope)
        }
    }

    private fun loadEnvVariableRef(variable: Variable, currentScope: Scope) {
        val distance = currentScope.envDistanceFrom(variable.source.scope)
        if (distance == 0) {
            add(LdaCurrentEnv, variable.slot)
        } else {
            add(LdaEnv, variable.slot, distance)
        }
    }

    private fun storeEnvVariableRef(variable: Variable, currentScope: Scope) {
        val distance = currentScope.distanceFrom(variable.source.scope)
        if (distance == 0) {
            add(StaCurrentEnv, variable.slot)
        } else {
            add(StaEnv, variable.slot, distance)
        }
    }

    private fun checkForConstReassignment(node: VariableRefNode): Boolean {
        return if (node.targetVar.type == Variable.Type.Const) {
            add(ThrowConstReassignment, loadConstant(node.targetVar.name))
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

        val ifFalseLabel = label()
        val endLabel = label()

        jump(ifFalseLabel, JumpIfToBooleanFalse)
        visit(node.ifTrue)
        jump(endLabel)
        place(ifFalseLabel)
        visit(node.ifFalse)
        place(endLabel)
    }

    override fun visitMemberExpression(node: MemberExpressionNode) {
        // TODO: Deal with assigning to a MemberExpression

        val objectReg = nextFreeReg()
        visit(node.lhs)
        add(Star, objectReg)

        when (node.type) {
            MemberExpressionNode.Type.Computed -> {
                visit(node.rhs)
                add(LdaKeyedProperty, objectReg)
            }
            MemberExpressionNode.Type.NonComputed -> {
                val cpIndex = loadConstant((node.rhs as IdentifierNode).identifierName)
                add(LdaNamedProperty, objectReg, cpIndex)
            }
            MemberExpressionNode.Type.Tagged -> TODO()
        }

        markRegFree(objectReg)
    }

    private fun argumentsMode(arguments: ArgumentList): ArgumentsMode {
        return when {
            arguments.isEmpty() -> ArgumentsMode.Normal
            arguments.last().isSpread && arguments.dropLast(1).none { it.isSpread } -> ArgumentsMode.LastSpread
            arguments.any { it.isSpread } -> ArgumentsMode.Spread
            else -> ArgumentsMode.Normal
        }
    }

    private fun requiredRegisters(arguments: ArgumentList): Int {
        return if (argumentsMode(arguments) == ArgumentsMode.Spread) 1 else arguments.size
    }

    override fun visitCallExpression(node: CallExpressionNode) {
        val args = node.arguments
        val callableReg = nextFreeReg()
        val argRegCount = requiredRegisters(args)
        val receiverReg = nextFreeRegBlock(argRegCount + 1)

        when (val target = node.target) {
            is IdentifierReferenceNode -> {
                visit(target)
                add(Star, callableReg)
                add(Mov, receiverReg(), receiverReg)
            }
            is MemberExpressionNode -> {
                visit(target.lhs)
                add(Star, receiverReg)

                when (target.type) {
                    MemberExpressionNode.Type.Computed -> {
                        visit(target.rhs)
                        add(LdaKeyedProperty, receiverReg)
                    }
                    MemberExpressionNode.Type.NonComputed -> {
                        val cpIndex = loadConstant((target.rhs as IdentifierNode).identifierName)
                        add(LdaNamedProperty, receiverReg, cpIndex)
                    }
                    MemberExpressionNode.Type.Tagged -> TODO()
                }

                add(Star, callableReg)
            }
        }

        when {
            args.isEmpty() -> add(Call0, callableReg, receiverReg)
            args.size == 1 && !args[0].isSpread -> {
                visit(args[0].expression)
                add(Star, receiverReg + 1)
                add(Call1, callableReg, RegisterRange(receiverReg, 2))
            }
            else -> {
                val (registers, mode) = loadArguments(args, receiverReg + 1)
                when (mode) {
                    ArgumentsMode.Normal -> add(Call, callableReg, RegisterRange(receiverReg, registers.count + 1))
                    ArgumentsMode.LastSpread -> add(CallLastSpread, callableReg, RegisterRange(receiverReg, registers.count + 1))
                    ArgumentsMode.Spread -> add(CallFromArray, callableReg, RegisterRange(receiverReg, 2))
                }
                registers.markFree()
            }
        }

        markRegFree(callableReg)
        for (i in 0..argRegCount)
            markRegFree(receiverReg + i)
    }

    override fun visitNewExpression(node: NewExpressionNode) {
        val target = nextFreeReg()
        visit(node.target)
        add(Star, target)

        val argumentReg = nextFreeRegBlock(requiredRegisters(node.arguments))

        val (arguments, mode) = if (node.arguments.isNotEmpty()) {
            loadArguments(node.arguments, argumentReg)
        } else null to null

        add(Ldar, target)

        when (mode) {
            ArgumentsMode.Spread -> add(ConstructFromArray, target, arguments!!.start)
            ArgumentsMode.LastSpread -> add(ConstructLastSpread, target, arguments!!)
            ArgumentsMode.Normal -> add(Construct, target, arguments!!)
            null -> add(Construct0, target)
        }

        markRegFree(target)
        arguments?.markFree()
    }

    private fun loadArguments(arguments: ArgumentList, firstReg: Int): Pair<RegisterRange, ArgumentsMode> {
        val mode = argumentsMode(arguments)

        return if (mode == ArgumentsMode.Spread) {
            loadArgumentsWithSpread(arguments, firstReg) to mode
        } else {
            for ((index, argument) in arguments.withIndex()) {
                visit(argument.expression)
                add(Star, firstReg + index)
            }

            RegisterRange(firstReg, arguments.size).also {
                it.markUsed()
            } to mode
        }
    }

    enum class ArgumentsMode {
        Normal,
        LastSpread,
        Spread,
    }

    private fun iterateValues(action: () -> Unit) {
        add(GetIterator)
        val iter = nextFreeReg()
        add(Star, iter)

        val next = nextFreeReg()
        add(LdaNamedProperty, iter, loadConstant("next"))
        add(Star, next)
        add(CallRuntime, InterpRuntime.ThrowIfIteratorNextNotCallable, next, 1)

        val done = label()
        val head = place(label())

        add(Call0, next, iter)
        val nextResult = nextFreeReg()
        add(Star, nextResult)
        add(CallRuntime, InterpRuntime.ThrowIfIteratorReturnNotObject, nextResult, 1)
        add(LdaNamedProperty, nextResult, loadConstant("done"))
        jump(done, JumpIfToBooleanTrue)

        add(LdaNamedProperty, nextResult, loadConstant("value"))
        action()
        jump(head)

        place(done)

        markRegFree(iter)
        markRegFree(next)
    }

    private fun loadArgumentsWithSpread(arguments: ArgumentList, arrayReg: Int): RegisterRange {
        add(CreateArrayLiteral)
        add(Star, arrayReg)
        var indexReg = -1
        var indexUsable = true

        for ((index, argument) in arguments.withIndex()) {
            if (argument.isSpread) {
                indexUsable = false
                indexReg = nextFreeReg()
                add(LdaInt, index)
                add(Star, indexReg)

                visit(argument.expression)
                iterateValues {
                    add(StaArrayLiteral, arrayReg, indexReg)
                    add(Ldar, indexReg)
                    add(Inc)
                    add(Star, indexReg)
                }
            } else {
                visit(argument.expression)
                if (indexUsable) {
                    add(StaArrayLiteralIndex, arrayReg, index)
                } else {
                    add(StaArrayLiteral, arrayReg, indexReg)
                    add(Ldar, indexReg)
                    add(Inc)
                    add(Star, indexReg)
                }
            }
        }

        markRegFree(indexReg)
        return RegisterRange(arrayReg, 1).also {
            it.markUsed()
        }
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
        TODO()
    }

    override fun visitParenthesizedExpression(node: ParenthesizedExpressionNode) {
        visit(node.expression)
    }

    override fun visitTemplateLiteral(node: TemplateLiteralNode) {
        val templateLiteral = nextFreeReg()

        for ((index, part) in node.parts.withIndex()) {
            if (part is StringLiteralNode) {
                val reg = loadConstant(part.value)
                add(LdaConstant, reg)
            } else {
                visit(part)
                add(ToString)
            }

            if (index != 0) {
                add(StringAppend, templateLiteral)
            } else {
                add(Star, templateLiteral)
            }
        }

        add(Ldar, templateLiteral)
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
        add(CreateArrayLiteral)
        val arrayReg = nextFreeReg()
        add(Star, arrayReg)
        for ((index, element) in node.elements.withIndex()) {
            when (element.type) {
                ArrayElementNode.Type.Elision -> continue
                ArrayElementNode.Type.Spread -> TODO()
                ArrayElementNode.Type.Normal -> {
                    visit(element.expression!!)
                    add(StaArrayLiteralIndex, arrayReg, index)
                }
            }
        }
        add(Ldar, arrayReg)
        markRegFree(arrayReg)
    }

    override fun visitObjectLiteral(node: ObjectLiteralNode) {
        add(CreateObjectLiteral)
        val objectReg = nextFreeReg()
        add(Star, objectReg)

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
                            method.parameterScope,
                            method.bodyScope,
                        )
                        functionNode.scope = method.scope
                        visitFunctionExpression(functionNode)
                    }

                    when (method.type) {
                        MethodDefinitionNode.Type.Normal -> storeObjectProperty(objectReg, method.propName, ::makeFunction)
                        MethodDefinitionNode.Type.Getter, MethodDefinitionNode.Type.Setter -> {
                            val propertyReg = nextFreeReg()
                            val methodReg = nextFreeReg()
                            val op = if (method.type == MethodDefinitionNode.Type.Getter) {
                                DefineGetterProperty
                            } else DefineSetterProperty

                            visitPropertyName(method.propName)
                            add(Star, propertyReg)

                            makeFunction()
                            add(Star, methodReg)

                            add(op, objectReg, propertyReg, methodReg)

                            markRegFree(propertyReg)
                            markRegFree(methodReg)
                        }
                        else -> TODO()
                    }
                }
                is ShorthandProperty -> {
                    visit(property.key)
                    add(StaNamedProperty, objectReg, loadConstant(property.key.identifierName))
                }
                is KeyValueProperty -> {
                    storeObjectProperty(objectReg, property.key) {
                        visit(property.value)
                    }
                }
            }
        }

        add(Ldar, objectReg)
        markRegFree(objectReg)
    }

    private fun visitPropertyName(property: PropertyName) {
        if (property.type == PropertyName.Type.Identifier) {
            add(LdaConstant, loadConstant((property.expression as IdentifierNode).identifierName))
        } else visit(property.expression)
    }

    private fun storeObjectProperty(objectReg: Int, property: PropertyName, valueProducer: () -> Unit) {
        if (property.type == PropertyName.Type.Identifier) {
            valueProducer()
            val name = (property.expression as IdentifierNode).identifierName
            add(StaNamedProperty, objectReg, loadConstant(name))
            return
        }

        when (property.type) {
            PropertyName.Type.String -> visit(property.expression)
            PropertyName.Type.Number -> visit(property.expression)
            PropertyName.Type.Computed -> {
                visit(property.expression)
                add(ToString)
            }
            PropertyName.Type.Identifier -> unreachable()
        }

        val keyReg = nextFreeReg()
        add(Star, keyReg)
        valueProducer()
        add(StaKeyedProperty, objectReg, keyReg)
        markRegFree(keyReg)
    }

    override fun visitBooleanLiteral(node: BooleanLiteralNode) {
        add(if (node.value) LdaTrue else LdaFalse)
    }

    override fun visitStringLiteral(node: StringLiteralNode) {
        add(LdaConstant, loadConstant(node.value))
    }

    override fun visitNumericLiteral(node: NumericLiteralNode) {
        val value = node.value
        if (value.isFinite() && floor(value) == value && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            add(LdaInt, value.toInt())
        } else {
            add(LdaConstant, loadConstant(value))
        }
    }

    override fun visitBigIntLiteral(node: BigIntLiteralNode) {
        val bigint = BigInteger(node.value, node.type.radix)
        add(LdaConstant, loadConstant(bigint))
    }

    override fun visitNullLiteral() {
        add(LdaNull)
    }

    override fun visitThisLiteral() {
        add(Ldar, receiverReg())
    }

    private fun getOpcode(index: Int) = builder.getOpcode(index)
    private fun setOpcode(index: Int, value: Opcode) = builder.setOpcode(index, value)
    private fun label() = builder.label()
    private fun jump(label: Label, type: OpcodeType = Jump) = builder.jumpHelper(label, type)
    private fun place(label: Label) = builder.place(label)
    private fun loadConstant(constant: Any) = builder.loadConstant(constant)
    private fun nextFreeReg() = builder.nextFreeReg()
    private fun nextFreeRegBlock(count: Int) = builder.nextFreeRegBlock(count)
    private fun markRegUsed(index: Int) = builder.markRegUsed(index)
    private fun markRegFree(index: Int) = builder.markRegFree(index)
    private fun receiverReg() = builder.receiverReg()
    private fun argReg(index: Int) = builder.argReg(index)
    private fun reg(index: Int) = builder.reg(index)

    private fun add(type: OpcodeType, vararg args: Any) {
        builder.addOpcode(Opcode(type, *args))
    }

    private operator fun Opcode.unaryPlus() {
        builder.addOpcode(this)
    }

    private fun RegisterRange.markUsed() {
        for (i in 0 until count)
            builder.markRegUsed(start + i)
    }

    private fun RegisterRange.markFree() {
        for (i in 0 until count)
            builder.markRegFree(start + i)
    }
}
