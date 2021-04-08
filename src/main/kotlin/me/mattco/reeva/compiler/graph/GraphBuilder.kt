package me.mattco.reeva.compiler.graph

import me.mattco.reeva.ir.FunctionInfo
import me.mattco.reeva.ir.opcodes.IrOpcode
import me.mattco.reeva.ir.opcodes.IrOpcodeVisitor
import me.mattco.reeva.utils.toValue

class GraphBuilder(val info: FunctionInfo) : IrOpcodeVisitor() {
    val registers = Registers(info.registerCount)
    private val graph = Graph()

    lateinit var currentEffect: Node
    lateinit var currentControl: Node

    fun build(): Graph {
        graph.start = Node(NodeDescriptors.start)
        graph.end = Node(NodeDescriptors.end)

        currentEffect = graph.start
        currentControl = graph.end

        for (opcode in info.opcodes)
            visit(opcode)

        graph.end.addInput(currentEffect)

        return graph
    }

    override fun visitLdaTrue() {
        registers.accumulator = Node(NodeDescriptors.true_)
    }

    override fun visitLdaFalse() {
        registers.accumulator = Node(NodeDescriptors.false_)
    }

    override fun visitLdaUndefined() {
        registers.accumulator = Node(NodeDescriptors.undefined)
    }

    override fun visitLdaNull() {
        registers.accumulator = Node(NodeDescriptors.null_)
    }

    override fun visitLdaZero() {
        registers.accumulator = Node(NodeDescriptors.int(0))
    }

    override fun visitLdaConstant(opcode: IrOpcode) {
        registers.accumulator = Node(NodeDescriptors.constant(
            info.constantPool[opcode.cpAt(0)].toValue()
        ))
    }

    override fun visitLdaInt(opcode: IrOpcode) {
        registers.accumulator = Node(NodeDescriptors.int(opcode.literalAt(0)))
    }

    override fun visitLdar(opcode: IrOpcode) {
        registers.accumulator = registers[opcode.regAt(0)]
    }

    override fun visitStar(opcode: IrOpcode) {
        registers[opcode.regAt(0)] = registers.accumulator!!
    }

    override fun visitMov(opcode: IrOpcode) {
        registers[opcode.regAt(1)] = registers[opcode.regAt(0)]
    }

    override fun visitLdaNamedProperty(opcode: IrOpcode) {
        val obj = registers[opcode.regAt(0)]
        val property = Node(NodeDescriptors.string(info.constantPool[opcode.cpAt(1)] as String))
        registers.accumulator = Node(NodeDescriptors.load, obj, property, currentEffect).also {
            currentEffect = it
        }
    }

    override fun visitLdaKeyedProperty(opcode: IrOpcode) {
        val obj = registers[opcode.regAt(0)]
        val property = registers[opcode.regAt(1)]
        val node = Node(NodeDescriptors.load, obj, property, currentEffect)
        registers.accumulator = node
        currentEffect = node
    }

    override fun visitStaNamedProperty(opcode: IrOpcode) {
        val obj = registers[opcode.regAt(0)]
        val property = Node(NodeDescriptors.string(info.constantPool[opcode.cpAt(1)] as String))
        currentEffect = Node(NodeDescriptors.store, obj, property, currentEffect)
    }

    override fun visitStaKeyedProperty(opcode: IrOpcode) {
        val obj = registers[opcode.regAt(0)]
        val property = registers[opcode.regAt(1)]
        currentEffect = Node(NodeDescriptors.store, obj, property, currentEffect)
    }

    override fun visitCreateArrayLiteral() {
        registers.accumulator = Node(NodeDescriptors.createArrayLiteral)
    }

    override fun visitStaArrayLiteral(opcode: IrOpcode) {
        val array = registers[opcode.regAt(0)]
        val index = registers[opcode.regAt(1)]
        currentEffect = Node(
            NodeDescriptors.staArrayLiteral,
            array,
            index,
            registers.accumulator!!,
            currentEffect
        )
    }

    override fun visitStaArrayLiteralIndex(opcode: IrOpcode) {
        val array = registers[opcode.regAt(0)]
        val index = Node(NodeDescriptors.int(info.constantPool[opcode.literalAt(1)] as Int))
        currentEffect = Node(
            NodeDescriptors.staArrayLiteral,
            array,
            index,
            registers.accumulator!!,
            currentEffect
        )
    }

    override fun visitCreateObjectLiteral() {
        registers.accumulator = Node(NodeDescriptors.objectLiteral)
    }

    override fun visitAdd(opcode: IrOpcode) {
        doBinaryOp(NodeType.Add, opcode)
    }

    override fun visitSub(opcode: IrOpcode) {
        doBinaryOp(NodeType.Sub, opcode)
    }

    override fun visitMul(opcode: IrOpcode) {
        doBinaryOp(NodeType.Mul, opcode)
    }

    override fun visitDiv(opcode: IrOpcode) {
        doBinaryOp(NodeType.Div, opcode)
    }

    override fun visitMod(opcode: IrOpcode) {
        doBinaryOp(NodeType.Mod, opcode)
    }

    override fun visitExp(opcode: IrOpcode) {
        doBinaryOp(NodeType.Exp, opcode)
    }

    override fun visitBitwiseOr(opcode: IrOpcode) {
        doBinaryOp(NodeType.BitwiseOr, opcode)
    }

    override fun visitBitwiseXor(opcode: IrOpcode) {
        doBinaryOp(NodeType.BitwiseXor, opcode)
    }

    override fun visitBitwiseAnd(opcode: IrOpcode) {
        doBinaryOp(NodeType.BitwiseAnd, opcode)
    }

    override fun visitShiftLeft(opcode: IrOpcode) {
        doBinaryOp(NodeType.ShiftLeft, opcode)
    }

    override fun visitShiftRight(opcode: IrOpcode) {
        doBinaryOp(NodeType.ShiftRight, opcode)
    }

    override fun visitShiftRightUnsigned(opcode: IrOpcode) {
        doBinaryOp(NodeType.ShiftRightUnsigned, opcode)
    }

    override fun visitInc() {
        doUnaryOp(NodeType.Inc)
    }

    override fun visitDec() {
        doUnaryOp(NodeType.Dec)
    }

    override fun visitNegate() {
        doUnaryOp(NodeType.Negate)
    }

    override fun visitBitwiseNot() {
        doUnaryOp(NodeType.BitwiseNot)
    }

    override fun visitStringAppend(opcode: IrOpcode) {
        currentEffect = Node(
            NodeDescriptors.stringAppend,
            registers[opcode.regAt(0)],
            registers.accumulator!!,
            currentEffect
        )
    }

    override fun visitToBooleanLogicalNot() {
        doUnaryOp(NodeType.ToBooleanLogicalNot)
    }

    override fun visitLogicalNot() {
        doUnaryOp(NodeType.LogicalNot)
    }

    override fun visitTypeOf() {
        currentEffect = Node(NodeDescriptors.typeof_, registers.accumulator!!, currentEffect)
    }

    override fun visitDeletePropertySloppy(opcode: IrOpcode) {
        currentEffect = Node(
            NodeDescriptors.deletePropertySloppy,
            registers[opcode.regAt(0)],
            registers.accumulator!!,
            currentEffect,
        )
    }

    override fun visitDeletePropertyStrict(opcode: IrOpcode) {
        currentEffect = Node(
            NodeDescriptors.deletePropertyStrict,
            registers[opcode.regAt(0)],
            registers.accumulator!!,
            currentEffect,
        )
    }

    override fun visitLdaGlobal(opcode: IrOpcode) {
        val name = Node(NodeDescriptors.constant(info.constantPool[opcode.regAt(0)]))
        currentEffect = Node(NodeDescriptors.ldaGlobal, name, currentEffect)
    }

    override fun visitStaGlobal(opcode: IrOpcode) {
        val name = Node(NodeDescriptors.constant(info.constantPool[opcode.regAt(0)]))
        currentEffect = Node(NodeDescriptors.ldaGlobal, name, currentEffect)
    }

    override fun visitLdaCurrentEnv(opcode: IrOpcode) {
        TODO()
    }

    override fun visitStaCurrentEnv(opcode: IrOpcode) {
        TODO()
    }

    override fun visitLdaEnv(opcode: IrOpcode) {
        TODO()
    }

    override fun visitStaEnv(opcode: IrOpcode) {
        TODO()
    }

    override fun visitCreateBlockScope(opcode: IrOpcode) {
        TODO()
    }

    override fun visitPushEnv(opcode: IrOpcode) {
        TODO()
    }

    override fun visitPopCurrentEnv(opcode: IrOpcode) {
        TODO()
    }

    override fun visitCall(opcode: IrOpcode) {
        TODO()
    }

    override fun visitCall0(opcode: IrOpcode) {
        TODO()
    }

    override fun visitCall1(opcode: IrOpcode) {
        TODO()
    }

    override fun visitCallLastSpread(opcode: IrOpcode) {
        TODO()
    }

    override fun visitCallFromArray(opcode: IrOpcode) {
        TODO()
    }

    override fun visitCallRuntime(opcode: IrOpcode) {
        TODO()
    }

    override fun visitConstruct(opcode: IrOpcode) {
        TODO()
    }

    override fun visitConstruct0(opcode: IrOpcode) {
        TODO()
    }

    override fun visitConstructLastSpread(opcode: IrOpcode) {
        TODO()
    }

    override fun visitConstructFromArray(opcode: IrOpcode) {
        TODO()
    }

    override fun visitTestEqual(opcode: IrOpcode) {
        doTest(NodeType.TestEqual, opcode)
    }

    override fun visitTestNotEqual(opcode: IrOpcode) {
        doTest(NodeType.TestNotEqual, opcode)
    }

    override fun visitTestEqualStrict(opcode: IrOpcode) {
        doTest(NodeType.TestEqualStrict, opcode)
    }

    override fun visitTestNotEqualStrict(opcode: IrOpcode) {
        doTest(NodeType.TestNotEqualStrict, opcode)
    }

    override fun visitTestLessThan(opcode: IrOpcode) {
        doTest(NodeType.TestLessThan, opcode)
    }

    override fun visitTestGreaterThan(opcode: IrOpcode) {
        doTest(NodeType.TestGreaterThan, opcode)
    }

    override fun visitTestLessThanOrEqual(opcode: IrOpcode) {
        doTest(NodeType.TestLessThanOrEqual, opcode)
    }

    override fun visitTestGreaterThanOrEqual(opcode: IrOpcode) {
        doTest(NodeType.TestGreaterThanOrEqual, opcode)
    }

    override fun visitTestReferenceEqual(opcode: IrOpcode) {
        doTest(NodeType.TestReferenceEqual, opcode)
    }

    override fun visitTestInstanceOf(opcode: IrOpcode) {
        doTest(NodeType.TestInstanceOf, opcode)
    }

    override fun visitTestIn(opcode: IrOpcode) {
        doTest(NodeType.TestIn, opcode)
    }

    override fun visitTestNullish(opcode: IrOpcode) {
        doTest(NodeType.TestNullish, opcode)
    }

    override fun visitTestNull(opcode: IrOpcode) {
        doTest(NodeType.TestNull, opcode)
    }

    override fun visitTestUndefined(opcode: IrOpcode) {
        doTest(NodeType.TestUndefined, opcode)
    }

    override fun visitToBoolean() {
        doUnaryOp(NodeType.ToBoolean)
    }

    override fun visitToNumber() {
        doUnaryOp(NodeType.ToNumber)
    }

    override fun visitToNumeric() {
        doUnaryOp(NodeType.ToNumeric)
    }

    override fun visitToObject() {
        doUnaryOp(NodeType.ToObject)
    }

    override fun visitToString() {
        doUnaryOp(NodeType.ToString)
    }

    override fun visitJump(opcode: IrOpcode) {
        TODO()
    }

    override fun visitJumpIfTrue(opcode: IrOpcode) {
        TODO()
    }

    override fun visitJumpIfFalse(opcode: IrOpcode) {
        TODO()
    }

    override fun visitJumpIfToBooleanTrue(opcode: IrOpcode) {
        TODO()
    }

    override fun visitJumpIfToBooleanFalse(opcode: IrOpcode) {
        TODO()
    }

    override fun visitJumpIfNull(opcode: IrOpcode) {
        TODO()
    }

    override fun visitJumpIfNotNull(opcode: IrOpcode) {
        TODO()
    }

    override fun visitJumpIfUndefined(opcode: IrOpcode) {
        TODO()
    }

    override fun visitJumpIfNotUndefined(opcode: IrOpcode) {
        TODO()
    }

    override fun visitJumpIfNullish(opcode: IrOpcode) {
        TODO()
    }

    override fun visitJumpIfNotNullish(opcode: IrOpcode) {
        TODO()
    }

    override fun visitJumpIfObject(opcode: IrOpcode) {
        TODO()
    }

    override fun visitReturn() {
        TODO()
    }

    override fun visitThrow() {
        TODO()
    }

    override fun visitThrowConstReassignment(opcode: IrOpcode) {
        TODO()
    }

    override fun visitThrowUseBeforeInitIfEmpty(opcode: IrOpcode) {
        TODO()
    }

    override fun visitDefineGetterProperty(opcode: IrOpcode) {
        TODO()
    }

    override fun visitDefineSetterProperty(opcode: IrOpcode) {
        TODO()
    }

    override fun visitDeclareGlobals(opcode: IrOpcode) {
        // nop
    }

    override fun visitCreateMappedArgumentsObject() {
        TODO()
    }

    override fun visitCreateUnmappedArgumentsObject() {
        TODO()
    }

    override fun visitGetIterator() {
        TODO()
    }

    override fun visitCreateClosure(opcode: IrOpcode) {
        TODO()
    }

    override fun visitDebugBreakpoint() {
        TODO()
    }

    private fun doUnaryOp(type: NodeType) {
        currentEffect = Node(NodeDescriptors.unaryOp(type), registers.accumulator!!, currentEffect)
    }

    private fun doBinaryOp(type: NodeType, opcode: IrOpcode) {
        currentEffect = Node(
            NodeDescriptors.binaryOp(type),
            registers[opcode.regAt(0)],
            registers.accumulator!!,
            currentEffect,
        )
    }

    private fun doTest(type: NodeType, opcode: IrOpcode) {
        currentEffect = Node(
            NodeDescriptors.test(type),
            registers[opcode.regAt(0)],
            registers.accumulator!!,
            currentEffect
        )
    }
}
