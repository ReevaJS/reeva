package me.mattco.reeva.ir

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.parser.Scope
import me.mattco.reeva.parser.Variable
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable
import java.io.File
import java.math.BigInteger

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
    val registerCount: Int, // includes argCount
    val argCount: Int,
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

        setupGlobalScope(node)
        visit(node.statements)
        +Return

        return FunctionInfo(
            null,
            builder.opcodes.toTypedArray(),
            builder.constantPool.toTypedArray(),
            builder.registerCount,
            1,
            isTopLevelScript = true
        )
    }

    override fun visitBlock(node: BlockNode) {
        if (node.scope.requiresEnv) {
            +PushEnv(node.scope.numSlots)
            node.scope.envVariables.forEachIndexed { index, variable ->
                variable.slot = index
            }
        }

        node.scope.inlineableVariables.forEach {
            it.slot = nextFreeReg()
        }

        super.visitBlock(node)

        node.scope.inlineableVariables.forEach {
            markRegFree(it.slot)
        }

        if (node.scope.requiresEnv)
            +PopEnv
    }

    /**
     * @return true if the scope requires an EnvRecord (i.e. has
     *         non-inlineable variables).
     */
    private fun loadDeclarations(node: NodeWithScope): Int {
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

        return loadConstant(DeclarationsArray(varNames, lexNames, constNames))
    }

    private fun setupGlobalScope(node: NodeWithScope) {
        +DeclareGlobals(loadDeclarations(node))
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
        val loopHead = label()
        place(loopHead)
        visit(node.body)
        visit(node.condition)
        jump(loopHead, ::JumpIfToBooleanTrue)
    }

    override fun visitWhileStatement(node: WhileStatementNode) {
        val loopHead = label()
        val loopEnd = label()

        place(loopHead)
        visit(node.condition)
        jump(loopEnd, ::JumpIfToBooleanFalse)
        visit(node.body)
        jump(loopHead)
        place(loopEnd)
    }

    override fun visitForStatement(node: ForStatementNode) {
        TODO()
    }

    override fun visitForIn(node: ForInNode) {
        TODO()
    }

    override fun visitForOf(node: ForOfNode) {
        TODO()
    }

    override fun visitForAwaitOf(node: ForAwaitOfNode) {
        TODO()
    }

    override fun visitLabelledStatement(node: LabelledStatementNode) {
        TODO()
    }

    override fun visitThrowStatement(node: ThrowStatementNode) {
        TODO()
    }

    override fun visitTryStatement(node: TryStatementNode) {
        TODO()
    }

    override fun visitBreakStatement(node: BreakStatementNode) {
        TODO()
    }

    override fun visitReturnStatement(node: ReturnStatementNode) {
        node.expression?.also(::visit)
        +Return
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
        if (useStart.index < declarationStart.index) {
            // We need to check if the variable has been initialized
            +ThrowUseBeforeInitIfEmpty(loadConstant(node.identifierName))
        }
    }

    override fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        val prevBuilder = builder
        builder = FunctionBuilder(node.parameters.size + 1)

        visit(node.body)
        if (builder.opcodes.last() != Return) {
            +LdaUndefined
            +Return
        }

        val info = FunctionInfo(
            node.identifier?.identifierName ?: "TODO: Function name inferring",
            builder.opcodes.toTypedArray(),
            builder.constantPool.toTypedArray(),
            builder.registerCount,
            node.parameters.size + 1,
            isTopLevelScript = false
        )

        builder = prevBuilder
        +CreateClosure(loadConstant(info))

        if (!node.variable.isInlineable) {
            // TODO This needs to be hoisted?
            +StaCurrentEnv(node.variable.slot)
        } else {
            +Star(node.variable.slot)
        }
    }

    override fun visitFunctionExpression(node: FunctionExpressionNode) {
        val prevBuilder = builder
        builder = FunctionBuilder(node.parameters.size + 1)

        visit(node.body)
        if (builder.opcodes.last() != Return) {
            +LdaUndefined
            +Return
        }

        val info = FunctionInfo(
            node.identifier?.identifierName ?: "<anonymous>",
            builder.opcodes.toTypedArray(),
            builder.constantPool.toTypedArray(),
            builder.registerCount,
            node.parameters.size + 1,
            isTopLevelScript = false
        )

        builder = prevBuilder
        +CreateClosure(loadConstant(info))
    }

    override fun visitArrowFunction(node: ArrowFunctionNode) {
        val prevBuilder = builder
        builder = FunctionBuilder(node.parameters.size + 1)

        visit(node.body)
        if (builder.opcodes.last() != Return) {
            +LdaUndefined
            +Return
        }

        val info = FunctionInfo(
            "<anonymous>",
            builder.opcodes.toTypedArray(),
            builder.constantPool.toTypedArray(),
            builder.registerCount,
            node.parameters.size + 1,
            isTopLevelScript = false
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
            BinaryOperator.Mul -> ::Mul
            BinaryOperator.Div -> ::Div
            BinaryOperator.Mod -> ::Mod
            BinaryOperator.Add -> ::Add
            BinaryOperator.Sub -> ::Sub
            BinaryOperator.Shl -> ::ShiftLeft
            BinaryOperator.Shr -> ::ShiftRight
            BinaryOperator.UShr -> ::ShiftRightUnsigned
            BinaryOperator.BitwiseAnd -> ::BitwiseAnd
            BinaryOperator.BitwiseOr -> ::BitwiseOr
            BinaryOperator.BitwiseXor -> ::BitwiseXor
            BinaryOperator.Exp -> ::Exp
            BinaryOperator.And -> TODO()
            BinaryOperator.Or -> TODO()
            BinaryOperator.Coalesce -> TODO()
            else -> unreachable()
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

                if (lhs.targetVar.isInlineable) {
                    +Star(lhs.targetVar.slot)
                } else {
                    loadEnvVariableRef(lhs.targetVar, lhs.scope)
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
        val distance = currentScope.distanceFrom(variable.source.scope)
        if (distance == 0) {
            +LdaCurrentEnv(variable.slot)
        } else {
            +LdaEnv(variable.slot, distance)
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

    override fun visitCallExpression(node: CallExpressionNode) {
        val args = node.arguments

        val callableReg = nextFreeReg()
        val receiverReg = nextFreeRegBlock(args.size + 1)

        when (val target = node.target) {
            is IdentifierReferenceNode -> {
                visit(target)
                +Star(callableReg)
                +Mov(receiverReg(), receiverReg)
                args.forEachIndexed { index, arg ->
                    visit(arg)
                    +Star(receiverReg + index + 1)
                }
                +CallAnyReceiver(callableReg, receiverReg, args.size)
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

                when (args.size) {
                    0 -> +CallProperty0(callableReg, receiverReg)
                    1 -> {
                        visit(args[0])
                        +Star(receiverReg + 1)
                        +CallProperty1(callableReg, receiverReg, receiverReg + 1)
                    }
                    else -> {
                        args.forEachIndexed { index, arg ->
                            visit(arg)
                            +Star(receiverReg + index + 1)
                        }
                        +CallProperty(callableReg, receiverReg, args.size)
                    }
                }
            }
            else -> TODO()
        }

        markRegFree(callableReg)
        for (i in args.indices)
            markRegFree(receiverReg + i)
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

    override fun visitNewExpression(node: NewExpressionNode) {
        val regList = if (node.arguments.isNotEmpty()) {
            loadArguments(node.arguments)
        } else null

        val target = nextFreeReg()
        visit(node.target)
        +Star(target)

        // At this point, the target is still in the accumulator,
        // which is necessary as the target is also the new.target
        // here

        if (regList != null) {
            +Construct(target, regList.firstReg, regList.count)
        } else +Construct0(target)

        markRegFree(target)
        regList?.markFree()
    }

    private fun loadArguments(arguments: ArgumentList): RegList {
        val firstReg = nextFreeRegBlock(arguments.size)
        arguments.forEachIndexed { index, argument ->
            if (argument.isSpread)
                TODO()
            visit(argument.expression)
            +Star(firstReg + index)
        }

        return RegList(firstReg, arguments.size).also {
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
        TODO()
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
        TODO()
    }

    override fun visitObjectLiteral(node: ObjectLiteralNode) {
        TODO()
    }

    override fun visitBooleanLiteral(node: BooleanLiteralNode) {
        if (node.value) +LdaTrue else +LdaFalse
    }

    override fun visitStringLiteral(node: StringLiteralNode) {
        +LdaConstant(loadConstant(node.value))
    }

    override fun visitNumericLiteral(node: NumericLiteralNode) {
        +LdaConstant(loadConstant(node.value))
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
    private fun jump(label: FunctionBuilder.Label, op: (Int) -> Opcode = ::Jump) = builder.jumpHelper(label, op)
    private fun place(label: FunctionBuilder.Label) = builder.place(label)
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
