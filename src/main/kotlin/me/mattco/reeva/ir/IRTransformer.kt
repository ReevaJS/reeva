package me.mattco.reeva.ir

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.interpreter.InterpRuntime
import me.mattco.reeva.utils.unreachable
import java.math.BigInteger

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

        setupScope(node)
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
        val requiresEnv = setupScope(node)
        if (requiresEnv)
            +PushBlockEnv

        super.visitBlock(node)

        node.scope.variables.values.forEach {
            if (it.isInlineable)
                markRegFree(it.index)
        }

        if (requiresEnv)
            +PopBlockEnv
    }

    /**
     * @return true if the scope requires an EnvRecord (i.e. has
     *         non-inlineable variables).
     */
    private fun setupScope(node: NodeWithScope): Boolean {
        val varDecls = node.variableDeclarations()
        val lexDecls = node.lexicalDeclarations()

        val varNames = mutableListOf<String>()
        val lexNames = mutableListOf<String>()
        val constNames = mutableListOf<String>()

        varDecls.forEach { decl ->
            if (!decl.variable.isInlineable)
                varNames.add(decl.boundName())
        }

        lexDecls.forEach { decl ->
            if (!decl.variable.isInlineable) {
                if (decl.isConst) {
                    constNames.add(decl.boundName())
                } else lexNames.add(decl.boundName())
            }
        }

        if (varNames.isEmpty() && lexNames.isEmpty())
            return false

        val index = loadConstant(DeclarationsArray(varNames, lexNames, constNames))
        +DeclareLocals(index)
        return true
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
        node.declarations.forEach { decl ->
            val variable = decl.variable

            if (variable.isInlineable) {
                val reg = nextFreeReg()
                variable.index = reg

                if (decl.initializer != null) {
                    visit(decl.initializer)
                    +Star(reg)
                }
            } else {
                if (decl.initializer != null) {
                    visit(decl.initializer)
                } else +LdaUndefined

                +StaEnv(loadConstant(variable.name))
            }
        }
    }

    override fun visitVariableDeclaration(node: VariableDeclarationNode) {
        node.declarations.forEach { decl ->
            val variable = decl.variable

            if (variable.isInlineable) {
                val reg = nextFreeReg()
                variable.index = reg

                if (decl.initializer != null) {
                    visit(decl.initializer)
                    +Star(reg)
                }
            } else {
                if (decl.initializer != null) {
                    visit(decl.initializer)
                } else +LdaUndefined

                +StaEnv(loadConstant(variable.name))
            }
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
        if (checkForConstReassignment(node))
            return

        val variable = node.variable
        if (variable.kind == Variable.Kind.Global) {
            +LdaGlobal(loadConstant(node.identifierName))
            return
        }

        if (variable.isInlineable) {
            +Ldar(variable.index)
        } else {
            +LdaEnv(loadConstant(variable.name))
        }
    }

    override fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        val prevBuilder = builder
        builder = FunctionBuilder(node.parameters.size + 1)

        node.parameters.forEachIndexed { index, parameter ->
            parameter.variable.index = index + 1
        }

        visit(node.body)
        if (builder.opcodes.last() != Return) {
            +LdaUndefined
            +Return
        }

        val info = FunctionInfo(
            node.identifier.identifierName,
            builder.opcodes.toTypedArray(),
            builder.constantPool.toTypedArray(),
            builder.registerCount,
            node.parameters.size + 1,
            isTopLevelScript = false
        )

        builder = prevBuilder
        +CreateClosure(loadConstant(info))

        if (!node.variable.isInlineable)
            TODO()

        // TODO: Figure out how to free this register
        val reg = nextFreeReg()
        node.variable.index = reg
        +Star(reg)
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

    private fun visitBinaryExpression(node: BinaryExpression, op: (Int) -> Opcode) {
        visit(node.lhs)
        val reg = nextFreeReg()
        +Star(reg)
        visit(node.rhs)
        +op(reg)
        markRegFree(reg)
    }

    override fun visitAdditiveExpression(node: AdditiveExpressionNode) {
        visitBinaryExpression(node, if (node.isSubtraction) ::Sub else ::Add)
    }

    override fun visitBitwiseANDExpression(node: BitwiseANDExpressionNode) {
        visitBinaryExpression(node, ::BitwiseAnd)
    }

    override fun visitBitwiseORExpression(node: BitwiseORExpressionNode) {
        visitBinaryExpression(node, ::BitwiseOr)
    }

    override fun visitBitwiseXORExpression(node: BitwiseXORExpressionNode) {
        visitBinaryExpression(node, ::BitwiseAnd)
    }

    override fun visitCoalesceExpression(node: CoalesceExpressionNode) {
        val rhsLabel = label()
        visit(node.lhs)
        jump(rhsLabel, ::JumpIfNullish)
        visit(node.rhs)
        place(rhsLabel)
    }

    override fun visitEqualityExpression(node: EqualityExpressionNode) {
        visit(node.lhs)
        val reg = nextFreeReg()
        +Star(reg)
        visit(node.rhs)

        when (node.op) {
            EqualityExpressionNode.Operator.StrictEquality -> +TestEqualStrict(reg)
            EqualityExpressionNode.Operator.StrictInequality -> +TestNotEqualStrict(reg)
            EqualityExpressionNode.Operator.NonstrictEquality -> +TestEqual(reg)
            EqualityExpressionNode.Operator.NonstrictInequality -> +TestNotEqual(reg)
        }

        markRegFree(reg)
    }

    override fun visitExponentiationExpression(node: ExponentiationExpressionNode) {
        visitBinaryExpression(node, ::Exp)
    }

    override fun visitLogicalANDExpression(node: LogicalANDExpressionNode) {
        visit(node.lhs)
        val rhsLabel = label()
        jump(rhsLabel, ::JumpIfToBooleanFalse)
        visit(node.rhs)
        place(rhsLabel)
    }

    override fun visitLogicalORExpression(node: LogicalORExpressionNode) {
        visit(node.lhs)
        val rhsLabel = label()
        jump(rhsLabel, ::JumpIfToBooleanTrue)
        visit(node.rhs)
        place(rhsLabel)
    }

    override fun visitMultiplicativeExpression(node: MultiplicativeExpressionNode) {
        visitBinaryExpression(node, when (node.op) {
            MultiplicativeExpressionNode.Operator.Multiply -> ::Mul
            MultiplicativeExpressionNode.Operator.Divide -> ::Div
            MultiplicativeExpressionNode.Operator.Modulo -> ::Mod
        })
    }

    override fun visitRelationalExpression(node: RelationalExpressionNode) {
        val reg = nextFreeReg()
        visit(node.lhs)
        +Star(reg)
        visit(node.rhs)

        when (node.op) {
            RelationalExpressionNode.Operator.LessThan -> +TestLessThan(reg)
            RelationalExpressionNode.Operator.GreaterThan -> +TestGreaterThan(reg)
            RelationalExpressionNode.Operator.LessThanEquals -> +TestLessThanOrEqual(reg)
            RelationalExpressionNode.Operator.GreaterThanEquals -> +TestGreaterThanOrEqual(reg)
            RelationalExpressionNode.Operator.Instanceof -> +TestInstanceOf(reg)
            RelationalExpressionNode.Operator.In -> +TestIn(reg)
        }

        markRegFree(reg)
    }

    override fun visitShiftExpression(node: ShiftExpressionNode) {
        val reg = nextFreeReg()
        visit(node.lhs)
        +Star(reg)
        visit(node.rhs)

        when (node.op) {
            ShiftExpressionNode.Operator.ShiftLeft -> +ShiftLeft(reg)
            ShiftExpressionNode.Operator.ShiftRight -> +ShiftRight(reg)
            ShiftExpressionNode.Operator.UnsignedShiftRight -> +ShiftRightUnsigned(reg)
        }

        markRegFree(reg)
    }

    override fun visitUnaryExpression(node: UnaryExpressionNode) {
        visit(node.expression)

        when (node.op) {
            UnaryExpressionNode.Operator.Delete -> TODO()
            UnaryExpressionNode.Operator.Void -> +LdaUndefined
            UnaryExpressionNode.Operator.Typeof -> +TypeOf
            UnaryExpressionNode.Operator.Plus -> TODO()
            UnaryExpressionNode.Operator.Minus -> +Negate
            UnaryExpressionNode.Operator.BitwiseNot -> +BitwiseNot
            UnaryExpressionNode.Operator.Not -> +ToBooleanLogicalNot
        }
    }

    override fun visitUpdateExpression(node: UpdateExpressionNode) {
        TODO()
    }

    override fun visitAssignmentExpression(node: AssignmentExpressionNode) {
        val lhs = node.lhs
        val rhs = node.rhs

        fun loadRhsIntoAcc() {
            if (node.op != AssignmentExpressionNode.Operator.Equals) {
                // First figure out the new value
                val op = when (node.op) {
                    AssignmentExpressionNode.Operator.Multiply -> ::Mul
                    AssignmentExpressionNode.Operator.Divide -> ::Div
                    AssignmentExpressionNode.Operator.Mod -> ::Mod
                    AssignmentExpressionNode.Operator.Plus -> ::Add
                    AssignmentExpressionNode.Operator.Minus -> ::Sub
                    AssignmentExpressionNode.Operator.ShiftLeft -> ::ShiftLeft
                    AssignmentExpressionNode.Operator.ShiftRight -> ::ShiftRight
                    AssignmentExpressionNode.Operator.UnsignedShiftRight -> ::ShiftRightUnsigned
                    AssignmentExpressionNode.Operator.BitwiseAnd -> ::BitwiseAnd
                    AssignmentExpressionNode.Operator.BitwiseOr -> ::BitwiseOr
                    AssignmentExpressionNode.Operator.BitwiseXor -> ::BitwiseXor
                    AssignmentExpressionNode.Operator.Power -> ::Exp
                    AssignmentExpressionNode.Operator.Equals -> unreachable()
                    AssignmentExpressionNode.Operator.And ->
                        return visitLogicalANDExpression(LogicalANDExpressionNode(lhs, rhs))
                    AssignmentExpressionNode.Operator.Or ->
                        return visitLogicalORExpression(LogicalORExpressionNode(lhs, rhs))
                    AssignmentExpressionNode.Operator.Nullish -> TODO()
                }
                visitBinaryExpression(BinaryExpression(lhs, rhs), op)
            } else {
                visit(rhs)
            }
        }

        if (lhs is IdentifierReferenceNode) {
            if (checkForConstReassignment(lhs))
                return

            loadRhsIntoAcc()

            if (lhs.variable.isInlineable) {
                +Star(lhs.variable.index)
            } else {
                +StaEnv(loadConstant(lhs.variable.name))
            }

            return
        } else if (lhs is MemberExpressionNode) {
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
    }

    private fun checkForConstReassignment(node: VariableRefNode): Boolean {
        return if (node.variable.mode == Variable.Mode.Const) {
            +LdaConstant(loadConstant(node.variable.name))
            val reg = nextFreeReg()
            +Star(reg)
            +CallRuntime(InterpRuntime.ThrowConstReassignment, reg, 1)
            markRegFree(reg)
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
