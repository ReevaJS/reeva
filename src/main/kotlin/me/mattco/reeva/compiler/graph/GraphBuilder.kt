package me.mattco.reeva.compiler.graph

import me.mattco.reeva.ir.FunctionInfo
import me.mattco.reeva.ir.opcodes.IrOpcodeVisitor
import me.mattco.reeva.ir.opcodes.RegisterRange
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

    override fun visitLdaConstant(cpIndex: Int) {
        registers.accumulator = Node(NodeDescriptors.constant(
            info.constantPool[cpIndex].toValue()
        ))
    }

    override fun visitLdaInt(int: Int) {
        registers.accumulator = Node(NodeDescriptors.int(int))
    }

    override fun visitLdar(reg: Int) {
        registers.accumulator = registers[reg]
    }

    override fun visitStar(reg: Int) {
        registers[reg] = registers.accumulator!!
    }

    override fun visitMov(fromReg: Int, toReg: Int) {
        registers[toReg] = registers[fromReg]
    }

    override fun visitLdaNamedProperty(objReg: Int, nameCpIndex: Int, slot: Int) {
        val obj = registers[objReg]
        val property = Node(NodeDescriptors.string(info.constantPool[nameCpIndex] as String))
        registers.accumulator = Node(NodeDescriptors.load, obj, property, currentEffect).also {
            currentEffect = it
        }
    }

    override fun visitLdaKeyedProperty(objReg: Int, slot: Int) {
        val obj = registers[objReg]
        val property = registers.accumulator!!
        val node = Node(NodeDescriptors.load, obj, property, currentEffect)
        registers.accumulator = node
        currentEffect = node
    }

    override fun visitStaNamedProperty(objReg: Int, nameCpIndex: Int, slot: Int) {
        val obj = registers[objReg]
        val property = Node(NodeDescriptors.string(info.constantPool[nameCpIndex] as String))
        currentEffect = Node(NodeDescriptors.store, obj, property, currentEffect)
    }

    override fun visitStaKeyedProperty(objReg: Int, propertyReg: Int, slot: Int) {
        val obj = registers[objReg]
        val property = registers[propertyReg]
        currentEffect = Node(NodeDescriptors.store, obj, property, currentEffect)
    }

    override fun visitCreateArrayLiteral() {
        registers.accumulator = Node(NodeDescriptors.createArrayLiteral)
    }

    override fun visitStaArrayLiteral(arrayReg: Int, indexReg: Int) {
        val array = registers[arrayReg]
        val index = registers[indexReg]
        currentEffect = Node(
            NodeDescriptors.staArrayLiteral,
            array,
            index,
            registers.accumulator!!,
            currentEffect
        )
    }

    override fun visitStaArrayLiteralIndex(arrayReg: Int, index: Int) {
        val array = registers[arrayReg]
        currentEffect = Node(
            NodeDescriptors.staArrayLiteral,
            array,
            Node(NodeDescriptors.int(info.constantPool[index] as Int)),
            registers.accumulator!!,
            currentEffect
        )
    }

    override fun visitCreateObjectLiteral() {
        registers.accumulator = Node(NodeDescriptors.objectLiteral)
    }

    override fun visitAdd(lhsReg: Int, slot: Int) {
        doBinaryOp(NodeType.Add, lhsReg)
    }

    override fun visitSub(lhsReg: Int, slot: Int) {
        doBinaryOp(NodeType.Sub, lhsReg)
    }

    override fun visitMul(lhsReg: Int, slot: Int) {
        doBinaryOp(NodeType.Mul, lhsReg)
    }

    override fun visitDiv(lhsReg: Int, slot: Int) {
        doBinaryOp(NodeType.Div, lhsReg)
    }

    override fun visitMod(lhsReg: Int, slot: Int) {
        doBinaryOp(NodeType.Mod, lhsReg)
    }

    override fun visitExp(lhsReg: Int, slot: Int) {
        doBinaryOp(NodeType.Exp, lhsReg)
    }

    override fun visitBitwiseOr(lhsReg: Int, slot: Int) {
        doBinaryOp(NodeType.BitwiseOr, lhsReg)
    }

    override fun visitBitwiseXor(lhsReg: Int, slot: Int) {
        doBinaryOp(NodeType.BitwiseXor, lhsReg)
    }

    override fun visitBitwiseAnd(lhsReg: Int, slot: Int) {
        doBinaryOp(NodeType.BitwiseAnd, lhsReg)
    }

    override fun visitShiftLeft(lhsReg: Int, slot: Int) {
        doBinaryOp(NodeType.ShiftLeft, lhsReg)
    }

    override fun visitShiftRight(lhsReg: Int, slot: Int) {
        doBinaryOp(NodeType.ShiftRight, lhsReg)
    }

    override fun visitShiftRightUnsigned(lhsReg: Int, slot: Int) {
        doBinaryOp(NodeType.ShiftRightUnsigned, lhsReg)
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

    override fun visitStringAppend(lhsReg: Int) {
        currentEffect = Node(
            NodeDescriptors.stringAppend,
            registers[lhsReg],
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

    override fun visitDeletePropertySloppy(objReg: Int) {
        currentEffect = Node(
            NodeDescriptors.deletePropertySloppy,
            registers[objReg],
            registers.accumulator!!,
            currentEffect,
        )
    }

    override fun visitDeletePropertyStrict(objReg: Int) {
        currentEffect = Node(
            NodeDescriptors.deletePropertyStrict,
            registers[objReg],
            registers.accumulator!!,
            currentEffect,
        )
    }

    override fun visitLdaGlobal(nameCpIndex: Int) {
        val name = Node(NodeDescriptors.constant(info.constantPool[nameCpIndex]))
        currentEffect = Node(NodeDescriptors.ldaGlobal, name, currentEffect)
    }

    override fun visitStaGlobal(nameCpIndex: Int) {
        val name = Node(NodeDescriptors.constant(info.constantPool[nameCpIndex]))
        currentEffect = Node(NodeDescriptors.ldaGlobal, name, currentEffect)
    }

    override fun visitLdaCurrentEnv(envSlot: Int) {
        TODO()
    }

    override fun visitStaCurrentEnv(envSlot: Int) {
        TODO()
    }

    override fun visitLdaEnv(contextReg: Int, envSlot: Int) {
        TODO()
    }

    override fun visitStaEnv(contextReg: Int, envSlot: Int) {
        TODO()
    }

    override fun visitCreateBlockScope(numSlots: Int) {
        TODO()
    }

    override fun visitPushEnv(envReg: Int) {
        TODO()
    }

    override fun visitPopCurrentEnv(envReg: Int) {
        TODO()
    }

    override fun visitCall(targetReg: Int, args: RegisterRange) {
        TODO()
    }

    override fun visitCall0(targetReg: Int, receiverReg: Int) {
        TODO()
    }

    override fun visitCallLastSpread(targetReg: Int, args: RegisterRange) {
        TODO()
    }

    override fun visitCallFromArray(targetReg: Int, arrayReg: Int) {
        TODO()
    }

    override fun visitCallRuntime(functionId: Int, args: RegisterRange) {
        TODO()
    }

    override fun visitConstruct(targetReg: Int, args: RegisterRange) {
        TODO()
    }

    override fun visitConstruct0(targetReg: Int) {
        TODO()
    }

    override fun visitConstructLastSpread(targetReg: Int, args: RegisterRange) {
        TODO()
    }

    override fun visitConstructFromArray(targetReg: Int, arrayReg: Int) {
        TODO()
    }

    override fun visitTestEqual(lhsReg: Int) {
        doTest(NodeType.TestEqual, lhsReg)
    }

    override fun visitTestNotEqual(lhsReg: Int) {
        doTest(NodeType.TestNotEqual, lhsReg)
    }

    override fun visitTestEqualStrict(lhsReg: Int) {
        doTest(NodeType.TestEqualStrict, lhsReg)
    }

    override fun visitTestNotEqualStrict(lhsReg: Int) {
        doTest(NodeType.TestNotEqualStrict, lhsReg)
    }

    override fun visitTestLessThan(lhsReg: Int) {
        doTest(NodeType.TestLessThan, lhsReg)
    }

    override fun visitTestGreaterThan(lhsReg: Int) {
        doTest(NodeType.TestGreaterThan, lhsReg)
    }

    override fun visitTestLessThanOrEqual(lhsReg: Int) {
        doTest(NodeType.TestLessThanOrEqual, lhsReg)
    }

    override fun visitTestGreaterThanOrEqual(lhsReg: Int) {
        doTest(NodeType.TestGreaterThanOrEqual, lhsReg)
    }

    override fun visitTestReferenceEqual(lhsReg: Int) {
        doTest(NodeType.TestReferenceEqual, lhsReg)
    }

    override fun visitTestInstanceOf(lhsReg: Int) {
        doTest(NodeType.TestInstanceOf, lhsReg)
    }

    override fun visitTestIn(lhsReg: Int) {
        doTest(NodeType.TestIn, lhsReg)
    }

    override fun visitTestNullish(lhsReg: Int) {
        doTest(NodeType.TestNullish, lhsReg)
    }

    override fun visitTestNull(lhsReg: Int) {
        doTest(NodeType.TestNull, lhsReg)
    }

    override fun visitTestUndefined(lhsReg: Int) {
        doTest(NodeType.TestUndefined, lhsReg)
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

    override fun visitJump(targetInstr: Int) {
        TODO()
    }

    override fun visitJumpIfTrue(targetInstr: Int) {
        TODO()
    }

    override fun visitJumpIfFalse(targetInstr: Int) {
        TODO()
    }

    override fun visitJumpIfToBooleanTrue(targetInstr: Int) {
        TODO()
    }

    override fun visitJumpIfToBooleanFalse(targetInstr: Int) {
        TODO()
    }

    override fun visitJumpIfNull(targetInstr: Int) {
        TODO()
    }

    override fun visitJumpIfNotNull(targetInstr: Int) {
        TODO()
    }

    override fun visitJumpIfUndefined(targetInstr: Int) {
        TODO()
    }

    override fun visitJumpIfNotUndefined(targetInstr: Int) {
        TODO()
    }

    override fun visitJumpIfNullish(targetInstr: Int) {
        TODO()
    }

    override fun visitJumpIfNotNullish(targetInstr: Int) {
        TODO()
    }

    override fun visitJumpIfObject(targetInstr: Int) {
        TODO()
    }

    override fun visitReturn() {
        TODO()
    }

    override fun visitThrow() {
        TODO()
    }

    override fun visitThrowConstReassignment(nameCpIndex: Int) {
        TODO()
    }

    override fun visitThrowUseBeforeInitIfEmpty(nameCpIndex: Int) {
        TODO()
    }

    override fun visitDefineGetterProperty(targetReg: Int, propertyReg: Int, methodReg: Int) {
        TODO()
    }

    override fun visitDefineSetterProperty(targetReg: Int, propertyReg: Int, methodReg: Int) {
        TODO()
    }

    override fun visitDeclareGlobals(globalsCpIndex: Int) {
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

    override fun visitCreateClosure(infoCpIndex: Int) {
        TODO()
    }

    override fun visitDebugBreakpoint() {
        TODO()
    }

    private fun doUnaryOp(type: NodeType) {
        currentEffect = Node(NodeDescriptors.unaryOp(type), registers.accumulator!!, currentEffect)
    }

    private fun doBinaryOp(type: NodeType, lhsReg: Int) {
        currentEffect = Node(
            NodeDescriptors.binaryOp(type),
            registers[lhsReg],
            registers.accumulator!!,
            currentEffect,
        )
    }

    private fun doTest(type: NodeType, lhsReg: Int) {
        currentEffect = Node(
            NodeDescriptors.test(type),
            registers[lhsReg],
            registers.accumulator!!,
            currentEffect
        )
    }
}
