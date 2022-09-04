package com.reevajs.reeva.compiler.graph

import com.reevajs.reeva.transformer.TransformedSource
import com.reevajs.reeva.transformer.opcodes.*
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.unreachable
import kotlin.collections.ArrayDeque

class GraphBuilder(private val transformedSource: TransformedSource) : OpcodeVisitor {
    private var currentControlNode = Node(NodeType.Start)
    private val graph = Graph(currentControlNode)
    private val stack = ArrayDeque<Node>()
    private val locals = Array<Node?>(transformedSource.functionInfo.ir.locals.size) { null }

    companion object {
        private val NullNode = Node(NodeType.Null)
        private val UndefinedNode = Node(NodeType.Undefined)
        private val numberNodeCache = mutableMapOf<Int, Node>()
        private val stringCache = mutableMapOf<String, Node>()
    }

    fun build(): Graph {
        transformedSource.functionInfo.ir.opcodes.forEach(::visit)

        return graph
    }

    private fun setCurrentControlNode(node: Node) {
        expect(node.type.isControl)
        currentControlNode.next = node
        node.prev = currentControlNode
        currentControlNode = node
    }

    private fun make(node: Node, vararg inputs: Node): Node {
        graph.nodes.add(node)

        expect(inputs.count { it == currentControlNode } <= 1)

        var setControlNode = false

        for (input in inputs) {
            node.inputs.add(input)
            if (input == currentControlNode) {
                expect(input.prev != null)
                expect(input.next == null)

                input.prev!!.next = node
                node.prev = input.prev
                input.prev = null

                currentControlNode = node
                setControlNode = true
            }
        }

        if (node.type.isControl && !setControlNode)
            setCurrentControlNode(node)

        return node
    }

    private fun push(node: Node) {
        stack.addLast(node)
    }

    private fun pop() = stack.removeLast()

    override fun visitPushNull() {
        push(make(NullNode))
    }

    override fun visitPushUndefined() {
        push(make(UndefinedNode))
    }

    override fun visitPushConstant(opcode: PushConstant) {
        val (type, constant) = when (val c = opcode.literal) {
            is Int -> NodeType.Number to c.toDouble()
            is Double -> NodeType.Number to c
            is Boolean -> NodeType.Bool to c
            is String -> NodeType.String to c
            else -> unreachable()
        }

        push(make(ConstantNode(type, constant)))
    }

    override fun visitPop() {
        val node = pop()
        if (stack.isEmpty() && node.type.isControl)
            setCurrentControlNode(node)
    }

    override fun visitDup() {
        push(stack.last())
    }

    override fun visitDupX1() {
        stack.add(stack.lastIndex - 1, stack.last())
    }

    override fun visitDupX2() {
        stack.add(stack.lastIndex - 2, stack.last())
    }

    override fun visitSwap() {
        val top = pop()
        val bottom = pop()
        push(top)
        push(bottom)
    }

    override fun visitLoadInt(opcode: LoadInt) {
        val intNode = locals[opcode.local.value]!!
        expect(intNode.type == NodeType.Int && intNode is ConstantNode)
        push(intNode)
    }

    override fun visitStoreInt(opcode: StoreInt) {
        val intNode = pop()
        expect(intNode.type == NodeType.Int && intNode is ConstantNode)
        locals[opcode.local.value] = intNode
    }

    override fun visitIncInt(opcode: IncInt) {
        val intNode = locals[opcode.local.value]!!
        expect(intNode.type == NodeType.Int && intNode is ConstantNode)
        make(Node(NodeType.IncInt), intNode)
    }

    override fun visitLoadValue(opcode: LoadValue) {
        val valueNode = locals[opcode.local.value]!!
        expect(valueNode.type != NodeType.Int)
        push(valueNode)
    }

    override fun visitStoreValue(opcode: StoreValue) {
        val valueNode = pop()
        locals[opcode.local.value] = valueNode
    }

    override fun visitAdd() = visitBinaryExpression(NodeType.Add)

    override fun visitSub() = visitBinaryExpression(NodeType.Sub)

    override fun visitMul() = visitBinaryExpression(NodeType.Mul)

    override fun visitDiv() = visitBinaryExpression(NodeType.Div)

    override fun visitExp() = visitBinaryExpression(NodeType.Exp)

    override fun visitMod() = visitBinaryExpression(NodeType.Mod)

    override fun visitBitwiseAnd() = visitBinaryExpression(NodeType.BitwiseAnd)

    override fun visitBitwiseOr() = visitBinaryExpression(NodeType.BitwiseOr)

    override fun visitBitwiseXor() = visitBinaryExpression(NodeType.BitwiseXor)

    override fun visitShiftLeft() = visitBinaryExpression(NodeType.ShiftLeft)

    override fun visitShiftRight() = visitBinaryExpression(NodeType.ShiftRight)

    override fun visitShiftRightUnsigned() = visitBinaryExpression(NodeType.ShiftRightUnsigned)

    private fun visitBinaryExpression(type: NodeType) {
        val rhs = pop()
        val lhs = pop()
        push(make(Node(type), lhs, rhs))
    }

    override fun visitTestEqualStrict() {
        TODO("Not yet implemented")
    }

    override fun visitTestNotEqualStrict() {
        TODO("Not yet implemented")
    }

    override fun visitTestEqual() {
        TODO("Not yet implemented")
    }

    override fun visitTestNotEqual() {
        TODO("Not yet implemented")
    }

    override fun visitTestLessThan() {
        TODO("Not yet implemented")
    }

    override fun visitTestLessThanOrEqual() {
        TODO("Not yet implemented")
    }

    override fun visitTestGreaterThan() {
        TODO("Not yet implemented")
    }

    override fun visitTestGreaterThanOrEqual() {
        TODO("Not yet implemented")
    }

    override fun visitTestInstanceOf() {
        val ctor = pop()
        val value = pop()
        push(make(Node(NodeType.TestInstanceOf), value, ctor))
    }

    override fun visitTestIn() {
        val rhs = pop()
        val lhs = pop()
        val actualLhs = make(Node(NodeType.ToPropertyKey), lhs)
        push(make(Node(NodeType.TestIn), actualLhs, rhs))
    }

    override fun visitTypeOf() {
        push(make(Node(NodeType.TypeOf), pop()))
    }

    override fun visitTypeOfGlobal(opcode: TypeOfGlobal) {
        push(make(Node(NodeType.TypeOfGlobal), pop()))
    }

    override fun visitToNumber() {
        push(make(Node(NodeType.ToNumber), pop()))
    }

    override fun visitToNumeric() {
        push(make(Node(NodeType.ToNumeric), pop()))
    }

    override fun visitToString() {
        push(make(Node(NodeType.ToString), pop()))
    }

    override fun visitNegate() {
        push(make(Node(NodeType.Negate), pop()))
    }

    override fun visitBitwiseNot() {
        push(make(Node(NodeType.BitwiseNot), pop()))
    }

    override fun visitToBooleanLogicalNot() {
        push(make(Node(NodeType.ToBooleanLogicalNot), pop()))
    }

    override fun visitInc() {
        push(make(Node(NodeType.Inc), pop()))
    }

    override fun visitDec() {
        push(make(Node(NodeType.Dec), pop()))
    }

    override fun visitLoadKeyedProperty() {
        val key = pop()
        val obj = pop()

        val keyNode = make(Node(NodeType.ToPropertyKey), key)
        val objNode = make(Node(NodeType.ToObject), obj)
        push(make(Node(NodeType.LoadKeyedProperty), obj, key))
    }

    override fun visitStoreKeyedProperty() {
        val value = pop()
        val key = pop()
        val obj = pop()

        val keyNode = make(Node(NodeType.ToPropertyKey), key)
        val objNode = make(Node(NodeType.ToObject), obj)
        push(make(Node(NodeType.StoreKeyedProperty), obj, key, value))
    }

    override fun visitLoadNamedProperty(opcode: LoadNamedProperty) {
        val obj = pop()

        val objNode = make(Node(NodeType.ToObject), obj)
        push(make(ConstantNode(NodeType.LoadNamedProperty, opcode.name), objNode))
    }

    override fun visitStoreNamedProperty(opcode: StoreNamedProperty) {
        val value = pop()
        val obj = pop()

        val objNode = make(Node(NodeType.ToObject), obj)
        push(make(ConstantNode(NodeType.LoadNamedProperty, opcode.name), objNode, value))
    }

    override fun visitCreateObject() {
        push(make(Node(NodeType.CreateObject)))
    }

    override fun visitCreateArray() {
        push(make(Node(NodeType.CreateArray)))
    }

    override fun visitStoreArray(opcode: StoreArray) {
        TODO("Not yet implemented")
    }

    override fun visitStoreArrayIndexed(opcode: StoreArrayIndexed) {
        TODO("Not yet implemented")
    }

    override fun visitDeletePropertyStrict() {
        TODO("Not yet implemented")
    }

    override fun visitDeletePropertySloppy() {
        TODO("Not yet implemented")
    }

    override fun visitGetIterator() {
        TODO("Not yet implemented")
    }

    override fun visitIteratorNext() {
        TODO("Not yet implemented")
    }

    override fun visitIteratorResultDone() {
        TODO("Not yet implemented")
    }

    override fun visitIteratorResultValue() {
        TODO("Not yet implemented")
    }

    override fun visitCall(opcode: Call) {
        val args = (0 until opcode.argCount).map { pop() }.asReversed()

        val receiver = pop()
        val target = pop()

        push(make(Node(NodeType.Call), target, receiver, *args.toTypedArray()))
    }

    override fun visitCallArray() {
        val args = pop()
        val receiver = pop()
        val target = pop()
        push(make(Node(NodeType.CallArray), target, receiver, args))
    }

    override fun visitConstruct(opcode: Construct) {
        val args = (0 until opcode.argCount).map { pop() }.asReversed()

        val receiver = pop()
        val target = pop()

        push(make(Node(NodeType.Construct), target, receiver, *args.toTypedArray()))
    }

    override fun visitConstructArray() {
        val args = pop()
        val receiver = pop()
        val target = pop()

        push(make(Node(NodeType.ConstructArray), target, receiver, args))
    }

    override fun visitDeclareGlobals(opcode: DeclareGlobals) {
        // TODO: Eventually make DeclareGlobals have a single object which resides in
        //       the constant pool
        push(make(ConstantNode(NodeType.DeclareGlobals, opcode)))
    }

    override fun visitPushDeclarativeEnvRecord(opcode: PushDeclarativeEnvRecord) {
        val node = make(Node(NodeType.PushDeclarativeEnvRecord))
        setCurrentControlNode(node)
    }

    override fun visitPushModuleEnvRecord() {
        val node = make(Node(NodeType.PushModuleEnvRecord))
        setCurrentControlNode(node)
    }

    override fun visitPopEnvRecord() {
        val node = make(Node(NodeType.PopEnvRecord))
        setCurrentControlNode(node)
    }

    override fun visitLoadGlobal(opcode: LoadGlobal) {
        push(make(ConstantNode(NodeType.LoadGlobal, opcode.name)))
    }

    override fun visitStoreGlobal(opcode: StoreGlobal) {
        val value = pop()
        val node = make(ConstantNode(NodeType.StoreGlobal, opcode.name), value)
        setCurrentControlNode(node)
    }

    override fun visitLoadCurrentEnvSlot(opcode: LoadCurrentEnvSlot) {
        TODO("Not yet implemented")
    }

    override fun visitStoreCurrentEnvSlot(opcode: StoreCurrentEnvSlot) {
        TODO("Not yet implemented")
    }

    override fun visitLoadEnvSlot(opcode: LoadEnvSlot) {
        TODO("Not yet implemented")
    }

    override fun visitStoreEnvSlot(opcode: StoreEnvSlot) {
        TODO("Not yet implemented")
    }

    override fun visitJump(opcode: Jump) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfTrue(opcode: JumpIfTrue) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfFalse(opcode: JumpIfFalse) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfToBooleanTrue(opcode: JumpIfToBooleanTrue) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfToBooleanFalse(opcode: JumpIfToBooleanFalse) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfUndefined(opcode: JumpIfUndefined) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfNotUndefined(opcode: JumpIfNotUndefined) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfNotNullish(opcode: JumpIfNotNullish) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfNullish(opcode: JumpIfNullish) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfNotEmpty(opcode: JumpIfNotEmpty) {
        TODO("Not yet implemented")
    }

    override fun visitCreateRegExpObject(opcode: CreateRegExpObject) {
        TODO("Not yet implemented")
    }

    override fun visitCreateTemplateLiteral(opcode: CreateTemplateLiteral) {
        TODO("Not yet implemented")
    }

    override fun visitForInEnumerate() {
        TODO("Not yet implemented")
    }

    override fun visitCreateClosure(opcode: CreateClosure) {
        TODO("Not yet implemented")
    }

    override fun visitCreateGeneratorClosure(opcode: CreateGeneratorClosure) {
        TODO("Not yet implemented")
    }

    override fun visitCreateAsyncClosure(opcode: CreateAsyncClosure) {
        TODO("Not yet implemented")
    }

    override fun visitCreateAsyncGeneratorClosure(opcode: CreateAsyncGeneratorClosure) {
        TODO("Not yet implemented")
    }

    override fun visitGetSuperConstructor() {
        TODO("Not yet implemented")
    }

    override fun visitCreateUnmappedArgumentsObject() {
        TODO("Not yet implemented")
    }

    override fun visitCreateMappedArgumentsObject() {
        TODO("Not yet implemented")
    }

    override fun visitThrowSuperNotInitializedIfEmpty() {
        TODO("Not yet implemented")
    }

    override fun visitThrowConstantReassignmentError(opcode: ThrowConstantReassignmentError) {
        TODO("Not yet implemented")
    }

    override fun visitThrowLexicalAccessError(opcode: ThrowLexicalAccessError) {
        TODO("Not yet implemented")
    }

    override fun visitPushClosure() {
        TODO("Not yet implemented")
    }

    override fun visitThrow() {
        TODO("Not yet implemented")
    }

    override fun visitReturn() {
        val value = pop()
        val node = make(Node(NodeType.Return), value)
        setCurrentControlNode(node)
    }

    override fun visitDefineGetterProperty() {
        TODO("Not yet implemented")
    }

    override fun visitDefineSetterProperty() {
        TODO("Not yet implemented")
    }

    override fun visitGetGeneratorPhase() {
        TODO("Not yet implemented")
    }

    override fun visitGetSuperBase() {
        TODO("Not yet implemented")
    }

    override fun visitJumpTable(opcode: JumpTable) {
        TODO("Not yet implemented")
    }

    override fun visitPushBigInt(opcode: PushBigInt) {
        TODO("Not yet implemented")
    }

    override fun visitPushEmpty() {
        TODO("Not yet implemented")
    }

    override fun visitSetGeneratorPhase(opcode: SetGeneratorPhase) {
        TODO("Not yet implemented")
    }

    override fun visitGeneratorSentValue() {
        TODO("Not yet implemented")
    }

    override fun visitCopyObjectExcludingProperties(opcode: CopyObjectExcludingProperties) {
        TODO("Not yet implemented")
    }

    override fun visitLoadBoolean(opcode: LoadBoolean) {
        TODO("Not yet implemented")
    }

    override fun visitStoreBoolean(opcode: StoreBoolean) {
        TODO("Not yet implemented")
    }

    override fun visitPushJVMFalse() {
        TODO("Not yet implemented")
    }

    override fun visitPushJVMTrue() {
        TODO("Not yet implemented")
    }

    override fun visitPushJVMInt(opcode: PushJVMInt) {
        TODO("Not yet implemented")
    }

    override fun visitCreateClassConstructor(opcode: CreateMethod) {
        TODO("Not yet implemented")
    }

    override fun visitCreateClass() {
        TODO("Not yet implemented")
    }

    override fun visitAttachClassMethod(opcode: AttachClassMethod) {
        TODO("Not yet implemented")
    }

    override fun visitAttachComputedClassMethod(opcode: AttachComputedClassMethod) {
        TODO("Not yet implemented")
    }

    override fun visitFinalizeClass() {
        TODO("Not yet implemented")
    }

    override fun visitPushToGeneratorState() {
        TODO("Not yet implemented")
    }

    override fun visitPopFromGeneratorState() {
        TODO("Not yet implemented")
    }

    override fun visitLoadModuleVar(opcode: LoadModuleVar) {
        TODO("Not yet implemented")
    }

    override fun visitStoreModuleVar(opcode: StoreModuleVar) {
        TODO("Not yet implemented")
    }

    override fun visitCollectRestArgs() {
        TODO("Not yet implemented")
    }
}
