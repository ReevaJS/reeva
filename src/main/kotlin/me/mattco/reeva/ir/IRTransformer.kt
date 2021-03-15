package me.mattco.reeva.ir

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.interpreter.InterpRuntime
import me.mattco.reeva.ir.FunctionBuilder.*
import me.mattco.reeva.parser.GlobalSourceNode
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.parser.Scope
import me.mattco.reeva.parser.Variable
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable
import java.io.File
import java.math.BigInteger
import kotlin.math.floor

fun main() {
    val source = File("./demo/index.js").readText()
    val ast = Parser(source).parseScript()
    val info = IRTransformer().transform(ast)
    OpcodePrinter.printFunctionInfo(info)
}

class FunctionInfo(
    val name: String?,
    val code: Array<Opcode>,
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

    fun transform(node: ScriptNode): FunctionInfo {
        if (::builder.isInitialized)
            throw IllegalStateException("Cannot re-use an IRTransformer")

        builder = FunctionBuilder()

        node.scope.envVariables.forEachIndexed { index, variable ->
            variable.slot = index
        }

        node.scope.inlineableVariables.forEach {
            it.slot = nextFreeReg()
        }

        visit(node.statements)
        +Return

        return FunctionInfo(
            null,
            builder.opcodes.toTypedArray(),
            builder.constantPool.toTypedArray(),
            builder.handlers.map(IRHandler::toHandler).toTypedArray(),
            builder.registerCount,
            1,
            node.scope.numSlots,
            node.scope.isStrict,
            isTopLevelScript = true
        )
    }

    private fun enterScope(scope: Scope, block: () -> Unit) {
        if (scope.requiresEnv) {
            builder.nestedContexts++
            +PushEnv(scope.numSlots)
            scope.envVariables.forEach { variable ->
                variable.slot = builder.envVarCount++
            }
        }

        scope.inlineableVariables.filter {
            it.mode != Variable.Mode.Parameter
        }.forEach {
            it.slot = nextFreeReg()
        }

        block()

        scope.inlineableVariables.forEach {
            markRegFree(it.slot)
        }

        if (scope.requiresEnv) {
            builder.nestedContexts--
            +PopCurrentEnv
        }
    }

    override fun visitBlock(node: BlockNode) {
        enterScope(node.scope) {
            super.visitBlock(node)
        }
    }

    /**
     * @return The index of the DeclarationsArray in the constant pool,
     *         or null if no DeclarationsArray is needed (i.e. if there
     *         are no non-inlineable variables)
     */
    private fun loadDeclarations(node: NodeWithScope): Int? {
        val scope = node.scope

        val varNames = mutableListOf<String>()
        val lexNames = mutableListOf<String>()
        val constNames = mutableListOf<String>()

        scope.envVariables.forEach {
            when (it.type) {
                Variable.Type.Var -> varNames.add(it.name)
                Variable.Type.Let -> lexNames.add(it.name)
                Variable.Type.Const -> constNames.add(it.name)
            }
        }

        if (varNames.isEmpty() && lexNames.isEmpty() && constNames.isEmpty())
            return null

        return loadConstant(DeclarationsArray(varNames, lexNames, constNames))
    }

    override fun visitExpressionStatement(node: ExpressionStatementNode) {
        visit(node.node)
    }

    override fun visitIfStatement(node: IfStatementNode) {
        visit(node.condition)

        if (node.falseBlock == null) {
            val endLabel = label()
            jump(endLabel, ::JumpIfToBooleanFalse)
            visit(node.trueBlock)
            place(endLabel)
        } else {
            val falseLabel = label()
            val endLabel = label()
            jump(falseLabel, ::JumpIfToBooleanFalse)
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
        jump(loopStart, ::JumpIfToBooleanTrue)
        place(loopEnd)
    }

    override fun visitWhileStatement(node: WhileStatementNode) {
        val loopStart = label()
        val loopEnd = label()

        place(loopStart)
        visit(node.condition)
        jump(loopEnd, ::JumpIfToBooleanFalse)

        builder.pushBlock(LoopBlock(loopStart, loopEnd))
        visit(node.body)
        builder.popBlock()

        jump(loopStart)
        place(loopEnd)
    }

    override fun visitForStatement(node: ForStatementNode) {
        if (node.scope.requiresEnv) {
            +PushEnv(node.scope.numSlots)
            builder.nestedContexts++
            node.scope.envVariables.forEachIndexed { index, variable ->
                variable.slot = index
            }
        }

        node.scope.inlineableVariables.forEach {
            it.slot = nextFreeReg()
        }

        if (node.initializer != null)
            visit(node.initializer)

        val loopStart = label()
        val loopEnd = label()
        val continueTarget = label()
        place(loopStart)

        if (node.condition != null) {
            visit(node.condition)
            jump(loopEnd, ::JumpIfToBooleanFalse)
        }

        builder.pushBlock(LoopBlock(continueTarget, loopEnd))
        visit(node.body)
        builder.popBlock()

        place(continueTarget)

        if (node.incrementer != null)
            visit(node.incrementer)

        jump(loopStart)

        place(loopEnd)

        node.scope.inlineableVariables.forEach {
            markRegFree(it.slot)
        }

        if (node.scope.requiresEnv) {
            builder.nestedContexts--
            +PopCurrentEnv
        }
    }

    override fun visitForIn(node: ForInNode) {
        TODO()
    }

    override fun visitForOf(node: ForOfNode) {
        visit(node.expression)
        +GetIterator
        val iter = nextFreeReg()
        +Star(iter)

        val next = nextFreeReg()
        +LdaNamedProperty(iter, loadConstant("next"))
        +Star(next)
        +CallRuntime(InterpRuntime.ThrowIfIteratorNextNotCallable, next, 1)

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
            +PushEnv(node.scope.numSlots)

        +Call0(next, iter)
        val nextResult = nextFreeReg()
        +Star(nextResult)
        +CallRuntime(InterpRuntime.ThrowIfIteratorReturnNotObject, nextResult, 1)
        +LdaNamedProperty(nextResult, loadConstant("done"))
        jump(loopEnd, ::JumpIfToBooleanTrue)

        +LdaNamedProperty(nextResult, loadConstant("value"))

        when (node.decl) {
            is VariableDeclarationNode -> {
                val decl = node.decl.declarations[0]
                val variable = decl.identifier.variable
                if (variable.isInlineable) {
                    +Star(variable.slot)
                } else {
                    storeEnvVariableRef(variable, node.scope)
                }
            }
            is LexicalDeclarationNode -> {
                val decl = node.decl.declarations[0]
                val variable = decl.identifier.variable
                if (variable.isInlineable) {
                    +Star(variable.slot)
                } else {
                    storeEnvVariableRef(variable, node.scope)
                }
            }
            is BindingIdentifierNode -> {
                val variable = node.decl.variable
                if (variable.isInlineable) {
                    +Star(variable.slot)
                } else {
                    storeEnvVariableRef(variable, node.scope)
                }
            }
        }

        visit(node.body)

        if (node.scope.requiresEnv) {
            builder.nestedContexts--
            +PopCurrentEnv
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
        +Throw
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
                    +PushEnv(node.catchNode.scope.numSlots)
                    node.catchNode.scope.envVariables.forEachIndexed { index, variable ->
                        variable.slot = index
                    }
                    mustPopEnv = true
                }

                val param = node.catchNode.catchParameter
                if (param.variable.isInlineable) {
                    param.variable.slot = nextFreeReg()
                    +Star(param.variable.slot)
                } else {
                    storeEnvVariableRef(param.variable, node.catchNode.scope)
                }
            }

            visit(node.catchNode.block)
            place(catchEnd!!)

            if (mustPopEnv)
                +PopCurrentEnv

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
            +Star(exceptionReg)
            visit(node.finallyBlock)
            +Ldar(exceptionReg)
            +Throw
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
        +Star(resultReg)

        visitScopedFinallyBlocks {
            +Ldar(resultReg)
            +Return
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
            +LdaUndefined
        }

        if (variable.isInlineable) {
            +Star(variable.slot)
        } else {
            +StaCurrentEnv(variable.slot)
        }
    }

    override fun visitDebuggerStatement() {
        +DebugBreakpoint
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
        if (variable.mode == Variable.Mode.Global) {
            +LdaGlobal(loadConstant(node.identifierName))
            return
        }

        if (variable.isInlineable) {
            +Ldar(variable.slot)
        } else loadEnvVariableRef(variable, node.scope)

        val declarationStart = node.targetVar.source.sourceStart
        val useStart = node.sourceStart
        if (useStart.index < declarationStart.index && variable.type != Variable.Type.Var) {
            // We need to check if the variable has been initialized
            +ThrowUseBeforeInitIfEmpty(loadConstant(node.identifierName))
        }
    }

    override fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        visitFunctionHelper(node.identifier.identifierName, node.parameters, node.body, node.scope, false)

        if (!node.variable.isInlineable) {
            // TODO This needs to be hoisted?
            +StaCurrentEnv(node.variable.slot)
        } else {
            +Star(node.variable.slot)
        }
    }

    override fun visitFunctionExpression(node: FunctionExpressionNode) {
        visitFunctionHelper(
            node.identifier?.identifierName ?: "<anonymous>",
            node.parameters,
            node.body,
            node.scope,
            false
        )
    }

    override fun visitArrowFunction(node: ArrowFunctionNode) {
        visitFunctionHelper("<anonymous>", node.parameters, node.body, node.scope, true)
    }

    private fun visitFunctionHelper(
        name: String,
        parameters: ParameterList,
        body: ASTNode,
        scope: Scope,
        isLexical: Boolean
    ) {
        val prevBuilder = builder
        builder = FunctionBuilder(parameters.size + 1)

        // FunctionDeclarationInstantiation
        val parameterNames = parameters.map { it.identifier.identifierName }
        val hasDuplicates = parameters.containsDuplicates()
        val simpleParameterList = parameters.isSimple()
        val hasParameterExpressions = parameters.any { it.initializer != null }

        val varNames = scope.declaredVariables.filter { it.type == Variable.Type.Var }.map { it.name }
        val varDecls = scope.declaredVariables.filter { it.type == Variable.Type.Var }.map { it.source }
        val lexicalNames = scope.declaredVariables.filter { it.type != Variable.Type.Var }.map { it.name }
        val lexicalDecls = scope.declaredVariables.filter { it.type != Variable.Type.Var }.map { it.source }

        val functionNames = mutableListOf<String>()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()

        varDecls.asReversed().forEach { decl ->
            // TODO: generator/async functions
            if (decl !is FunctionDeclarationNode)
                return@forEach

            val fn = decl.name
            if (fn !in functionNames) {
                functionNames.add(0, fn)
                functionsToInitialize.add(decl)
            }
        }

        // TODO: Use this
        val argumentsObjectNeeded = when {
            isLexical -> false
            "arguments" in parameterNames -> false
            !hasParameterExpressions && ("arguments" in functionNames || "arguments" in lexicalNames) -> false
            else -> true
        }

        enterScope(scope) {
            parameters.forEachIndexed { index, param ->
                val register = index + 1
                val isInlineable = param.variable.isInlineable

                if (isInlineable)
                    param.variable.slot = register

                if (param.initializer != null) {
                    // Check if the parameter is undefined
                    val skipDefault = label()

                    +Ldar(register)
                    jump(skipDefault, ::JumpIfNotUndefined)

                    visit(param.initializer)
                    if (isInlineable) {
                        +Star(register)
                    } else {
                        +StaCurrentEnv(param.variable.slot)
                    }

                    place(skipDefault)
                } else if (!isInlineable) {
                    +Ldar(register)
                    +StaCurrentEnv(param.variable.slot)
                }
            }

            val instantiatedVarNames = parameterNames.toMutableSet()
            for (varDecl in varDecls) {
                val variable = varDecl.variable
                if (variable.name !in instantiatedVarNames) {
                    instantiatedVarNames.add(variable.name)
                    +LdaUndefined
                    if (variable.isInlineable) {
                        +Star(variable.slot)
                    } else {
                        +StaCurrentEnv(variable.slot)
                    }
                }
            }

            // body's scope is the same as the function's scope (the scope we receive
            // as a parameter). We don't want to re-enter the same scope, so we explicitly
            // call super.visitBlock if necessary.
            if (body is BlockNode) {
                super.visitBlock(body)
            } else visit(body)
        }

        // TODO: Does it matter if we pop the env before we return? Might we a bit more
        // efficient if we don't have to worry about maintaining the env tree before a
        // return
        if (builder.opcodes.lastOrNull() != Return) {
            +LdaUndefined
            +Return
        }

        val info = FunctionInfo(
            name,
            builder.opcodes.toTypedArray(),
            builder.constantPool.toTypedArray(),
            builder.handlers.map(IRHandler::toHandler).toTypedArray(),
            builder.registerCount,
            parameters.size + 1,
            scope.numSlots,
            scope.isStrict,
            isTopLevelScript = false,
        )

        builder = prevBuilder
        +CreateClosure(loadConstant(info))
    }

    override fun visitClassDeclaration(node: ClassDeclarationNode) {
        TODO()
    }

    override fun visitClassExpression(node: ClassExpressionNode) {
        TODO()
    }

    override fun visitBinaryExpression(node: BinaryExpressionNode) {
        val opcode = when (node.operator) {
            BinaryOperator.Add -> ::Add
            BinaryOperator.Sub -> ::Sub
            BinaryOperator.Mul -> ::Mul
            BinaryOperator.Div -> ::Div
            BinaryOperator.Exp -> ::Exp
            BinaryOperator.Mod -> ::Mod
            BinaryOperator.And -> {
                visit(node.lhs)
                +ToBoolean
                val skip = label()
                jump(skip, ::JumpIfFalse)
                visit(node.rhs)
                place(skip)
                return
            }
            BinaryOperator.Or -> {
                visit(node.lhs)
                val skip = label()
                jump(skip, ::JumpIfToBooleanTrue)
                visit(node.rhs)
                place(skip)
                return
            }
            BinaryOperator.Coalesce -> {
                visit(node.lhs)
                val skip = label()
                jump(skip, ::JumpIfNotNullish)
                visit(node.rhs)
                place(skip)
                return
            }
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
        }

        visit(node.lhs)
        val reg = nextFreeReg()
        +Star(reg)
        visit(node.rhs)
        +opcode(reg)
        markRegFree(reg)
    }

    override fun visitUnaryExpression(node: UnaryExpressionNode) {
        visit(node.expression)

        when (node.op) {
            UnaryOperator.Delete -> TODO()
            UnaryOperator.Void -> +LdaUndefined
            UnaryOperator.Typeof -> +TypeOf
            UnaryOperator.Plus -> TODO()
            UnaryOperator.Minus -> +Negate
            UnaryOperator.BitwiseNot -> +BitwiseNot
            UnaryOperator.Not -> +ToBooleanLogicalNot
        }
    }

    override fun visitUpdateExpression(node: UpdateExpressionNode) {
        TODO()
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

                when {
                    lhs.targetVar.isInlineable -> +Star(lhs.targetVar.slot)
                    lhs.targetVar.source !is GlobalSourceNode -> storeEnvVariableRef(lhs.targetVar, lhs.scope)
                    else -> +StaGlobal(loadConstant(lhs.targetVar.name))
                }

                return
            }
            is MemberExpressionNode -> {
                val objectReg = nextFreeReg()
                visit(lhs.lhs)
                +Star(objectReg)

                when (lhs.type) {
                    MemberExpressionNode.Type.Computed -> {
                        val keyReg = nextFreeReg()
                        visit(lhs.rhs)
                        +Star(keyReg)
                        loadRhsIntoAcc()
                        +StaKeyedProperty(objectReg, keyReg)
                        markRegFree(keyReg)
                    }
                    MemberExpressionNode.Type.NonComputed -> {
                        loadRhsIntoAcc()
                        +StaNamedProperty(objectReg, loadConstant((lhs.rhs as IdentifierNode).identifierName))
                    }
                    MemberExpressionNode.Type.Tagged -> TODO()
                }

                markRegFree(objectReg)
            }
            else -> TODO()
        }
    }

    private fun loadEnvVariableRef(variable: Variable, currentScope: Scope) {
        val distance = currentScope.envDistanceFrom(variable.source.scope)
        if (distance == 0) {
            +LdaCurrentEnv(variable.slot)
        } else {
            +LdaEnv(variable.slot, distance)
        }
    }

    private fun storeEnvVariableRef(variable: Variable, currentScope: Scope) {
        val distance = currentScope.distanceFrom(variable.source.scope)
        if (distance == 0) {
            +StaCurrentEnv(variable.slot)
        } else {
            +StaEnv(variable.slot, distance)
        }
    }

    private fun checkForConstReassignment(node: VariableRefNode): Boolean {
        return if (node.targetVar.type == Variable.Type.Const) {
            +ThrowConstReassignment(loadConstant(node.targetVar.name))
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

        jump(ifFalseLabel, ::JumpIfToBooleanFalse)
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
        +Star(objectReg)

        when (node.type) {
            MemberExpressionNode.Type.Computed -> {
                visit(node.rhs)
                +LdaKeyedProperty(objectReg)
            }
            MemberExpressionNode.Type.NonComputed -> {
                val cpIndex = loadConstant((node.rhs as IdentifierNode).identifierName)
                +LdaNamedProperty(objectReg, cpIndex)
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
                +Star(callableReg)
                +Mov(receiverReg(), receiverReg)
            }
            is MemberExpressionNode -> {
                visit(target.lhs)
                +Star(receiverReg)

                when (target.type) {
                    MemberExpressionNode.Type.Computed -> {
                        visit(target.rhs)
                        +LdaKeyedProperty(receiverReg)
                    }
                    MemberExpressionNode.Type.NonComputed -> {
                        val cpIndex = loadConstant((target.rhs as IdentifierNode).identifierName)
                        +LdaNamedProperty(receiverReg, cpIndex)
                    }
                    MemberExpressionNode.Type.Tagged -> TODO()
                }

                +Star(callableReg)
            }
        }

        when {
            args.isEmpty() -> +Call0(callableReg, receiverReg)
            args.size == 1 && !args[0].isSpread -> {
                visit(args[0].expression)
                +Call1(callableReg, receiverReg)
            }
            else -> {
                val (registers, mode) = loadArguments(args, receiverReg + 1)
                when (mode) {
                    ArgumentsMode.Normal -> +Call(callableReg, receiverReg, registers.count)
                    ArgumentsMode.LastSpread -> +CallLastSpread(callableReg, receiverReg, registers.count)
                    ArgumentsMode.Spread -> +CallFromArray(callableReg, receiverReg)
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
        +Star(target)

        val argumentReg = nextFreeRegBlock(requiredRegisters(node.arguments))

        val (arguments, mode) = if (node.arguments.isNotEmpty()) {
            loadArguments(node.arguments, argumentReg)
        } else null to null

        +Ldar(target)

        when (mode) {
            ArgumentsMode.Spread -> +ConstructFromArray(target, arguments!!.firstReg)
            ArgumentsMode.LastSpread -> +ConstructLastSpread(target, arguments!!.firstReg, arguments.count)
            ArgumentsMode.Normal -> +Construct(target, arguments!!.firstReg, arguments.count)
            null -> +Construct0(target)
        }

        markRegFree(target)
        arguments?.markFree()
    }

    private fun loadArguments(arguments: ArgumentList, firstReg: Int): Pair<RegList, ArgumentsMode> {
        val mode = argumentsMode(arguments)

        return if (mode == ArgumentsMode.Spread) {
            loadArgumentsWithSpread(arguments, firstReg) to mode
        } else {
            for ((index, argument) in arguments.withIndex()) {
                visit(argument.expression)
                +Star(firstReg + index)
            }

            RegList(firstReg, arguments.size).also {
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
        +GetIterator
        val iter = nextFreeReg()
        +Star(iter)

        val next = nextFreeReg()
        +LdaNamedProperty(iter, loadConstant("next"))
        +Star(next)
        +CallRuntime(InterpRuntime.ThrowIfIteratorNextNotCallable, next, 1)

        val done = label()
        val head = place(label())

        +Call0(next, iter)
        val nextResult = nextFreeReg()
        +Star(nextResult)
        +CallRuntime(InterpRuntime.ThrowIfIteratorReturnNotObject, nextResult, 1)
        +LdaNamedProperty(nextResult, loadConstant("done"))
        jump(done, ::JumpIfToBooleanTrue)

        +LdaNamedProperty(nextResult, loadConstant("value"))
        action()
        jump(head)

        place(done)

        markRegFree(iter)
        markRegFree(next)
    }

    private fun loadArgumentsWithSpread(arguments: ArgumentList, arrayReg: Int): RegList {
        +CreateArrayLiteral
        +Star(arrayReg)
        var indexReg = -1
        var indexUsable = true

        for ((index, argument) in arguments.withIndex()) {
            if (argument.isSpread) {
                indexUsable = false
                indexReg = nextFreeReg()
                +LdaInt(index)
                +Star(indexReg)

                visit(argument.expression)
                iterateValues {
                    +StaArrayLiteral(arrayReg, indexReg)
                    +Ldar(indexReg)
                    +Inc
                    +Star(indexReg)
                }
            } else {
                visit(argument.expression)
                if (indexUsable) {
                    +StaArrayLiteralIndex(arrayReg, index)
                } else {
                    +StaArrayLiteral(arrayReg, indexReg)
                    +Ldar(indexReg)
                    +Inc
                    +Star(indexReg)
                }
            }
        }

        markRegFree(indexReg)
        return RegList(arrayReg, 1).also {
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
                +LdaConstant(reg)
            } else {
                visit(part)
                +ToString
            }

            if (index != 0) {
                +TemplateAppend(templateLiteral)
            } else {
                +Star(templateLiteral)
            }
        }

        +Ldar(templateLiteral)
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
        +CreateArrayLiteral
        val arrayReg = nextFreeReg()
        +Star(arrayReg)
        for ((index, element) in node.elements.withIndex()) {
            when (element.type) {
                ArrayElementNode.Type.Elision -> continue
                ArrayElementNode.Type.Spread -> TODO()
                ArrayElementNode.Type.Normal -> {
                    visit(element.expression!!)
                    +StaArrayLiteralIndex(arrayReg, index)
                }
            }
        }
        +Ldar(arrayReg)
        markRegFree(arrayReg)
    }

    override fun visitObjectLiteral(node: ObjectLiteralNode) {
        +CreateObjectLiteral
        val objectReg = nextFreeReg()
        +Star(objectReg)

        for (property in node.list) {
            when (property) {
                is SpreadProperty -> TODO()
                is MethodProperty -> {
                    val method = property.method

                    storeObjectProperty(objectReg, method.propName) {
                        // TODO: This probably isn't correct
                        val functionNode = FunctionExpressionNode(
                            null,
                            method.parameters,
                            method.body
                        )
                        functionNode.scope = method.scope
                        visitFunctionExpression(functionNode)
                    }
                }
                is ShorthandProperty -> {
                    visit(property.key)
                    +StaNamedProperty(objectReg, loadConstant(property.key.identifierName))
                }
                is KeyValueProperty -> {
                    storeObjectProperty(objectReg, property.key) {
                        visit(property.value)
                    }
                }
            }
        }

        +Ldar(objectReg)
        markRegFree(objectReg)
    }

    private fun storeObjectProperty(objectReg: Int, property: PropertyName, valueProducer: () -> Unit) {
        if (property.type == PropertyName.Type.Identifier) {
            valueProducer()
            val name = (property.expression as IdentifierNode).identifierName
            +StaNamedProperty(objectReg, loadConstant(name))
            return
        }

        when (property.type) {
            PropertyName.Type.String -> visit(property.expression)
            PropertyName.Type.Number -> visit(property.expression)
            PropertyName.Type.Computed -> {
                visit(property.expression)
                +ToString
            }
            PropertyName.Type.Identifier -> unreachable()
        }

        val keyReg = nextFreeReg()
        +Star(keyReg)
        valueProducer()
        +StaKeyedProperty(objectReg, keyReg)
        markRegFree(keyReg)
    }

    override fun visitBooleanLiteral(node: BooleanLiteralNode) {
        if (node.value) +LdaTrue else +LdaFalse
    }

    override fun visitStringLiteral(node: StringLiteralNode) {
        +LdaConstant(loadConstant(node.value))
    }

    override fun visitNumericLiteral(node: NumericLiteralNode) {
        val value = node.value
        if (value.isFinite() && floor(value) == value && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            +LdaInt(value.toInt())
        } else {
            +LdaDouble(value)
        }
    }

    override fun visitBigIntLiteral(node: BigIntLiteralNode) {
        val bigint = BigInteger(node.value, node.type.radix)
        +LdaConstant(loadConstant(bigint))
    }

    override fun visitNullLiteral() {
        +LdaNull
    }

    override fun visitThisLiteral() {
        +Ldar(receiverReg())
    }

    private fun getOpcode(index: Int) = builder.getOpcode(index)
    private fun setOpcode(index: Int, value: Opcode) = builder.setOpcode(index, value)
    private fun label() = builder.label()
    private fun jump(label: Label, op: (Int) -> Opcode = ::Jump) = builder.jumpHelper(label, op)
    private fun place(label: Label) = builder.place(label)
    private fun loadConstant(constant: Any) = builder.loadConstant(constant)
    private fun nextFreeReg() = builder.nextFreeReg()
    private fun nextFreeRegBlock(count: Int) = builder.nextFreeRegBlock(count)
    private fun markRegUsed(index: Int) = builder.markRegUsed(index)
    private fun markRegFree(index: Int) = builder.markRegFree(index)
    private fun receiverReg() = builder.receiverReg()
    private fun argReg(index: Int) = builder.argReg(index)
    private fun reg(index: Int) = builder.reg(index)

    private operator fun Opcode.unaryPlus() {
        builder.addOpcode(this)
    }

    inner class RegList(val firstReg: Int, val count: Int) {
        fun markUsed() {
            for (i in 0 until count)
                builder.markRegUsed(firstReg + i)
        }

        fun markFree() {
            for (i in 0 until count)
                builder.markRegFree(firstReg + i)
        }
    }
}
