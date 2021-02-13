package me.mattco.reeva.ir

class FunctionBuilder(val argCount: Int = 1) {
    private val registers = mutableListOf<RegState>()

    val opcodes = mutableListOf<Opcode>()
    val constantPool = mutableListOf<Any>()

    // Stores how deep we currently are in the context tree from the
    // scope we started with (this function's HoistingScope)
    var nestedContexts = 0

    enum class RegState { USED, FREE }

    // If a label is jumped to before it is placed, it is placed in this
    // queue, and is "completed" when it is placed
    private val placeholders = mutableMapOf<Label, MutableList<Pair<Int, (Int) -> Opcode>>>()

    val registerCount: Int
        get() = registers.size

    init {
        for (i in 0 until argCount)
            registers.add(RegState.USED)
    }

    fun receiverReg() = 0
    fun argReg(index: Int) = index - 1
    fun reg(index: Int) = argCount - index

    fun opcodeCount() = opcodes.size

    fun getOpcode(index: Int) = opcodes[index]

    fun setOpcode(index: Int, value: Opcode) {
        opcodes[index] = value
    }

    fun addOpcode(opcode: Opcode) = opcodes.add(opcode)

    fun label() = Label(null)

    fun jump(label: Label) = jumpHelper(label, ::Jump)

    fun jumpHelper(label: Label, jumpOp: (Int) -> Opcode) {
        if (label.opIndex != null) {
            // the label has already been placed, so we can directly insert
            // a jump instruction
            opcodes.add(Jump(label.opIndex!!))
        } else {
            // the label has yet to be replaced, so we have to place a marker
            // and wait for the label to be placed
            opcodes.add(JumpPlaceholder)
            val placeholderIndex = opcodes.lastIndex

            if (label !in placeholders)
                placeholders[label] = mutableListOf()
            placeholders[label]!!.add(placeholderIndex to jumpOp)
        }
    }

    fun place(label: Label) {
        val targetIndex = opcodes.lastIndex + 1

        placeholders.forEach {
            if (it.key != label)
                return@forEach

            it.value.forEach { (offset, jumpOp) ->
                if (opcodes[offset] !is JumpPlaceholder) {
                    TODO("expected JumpPlaceholder at offset $offset")
                }
                opcodes[offset] = jumpOp(targetIndex)
            }
            placeholders[label]!!.clear()
        }

        label.opIndex = targetIndex
    }

    fun nextFreeReg(): Int {
        val index = registers.indexOfFirst { it == RegState.FREE }
        if (index == -1) {
            registers.add(RegState.USED)
            return registers.lastIndex
        }
        registers[index] = RegState.USED
        return index
    }

    fun nextFreeRegBlock(count: Int): Int {
        // TODO: Improve
        for (i in registers.indices) {
            if (i + count > registers.size)
                break
            if (registers.subList(i, i + count).all { it == RegState.FREE }) {
                for (j in i until (i + count))
                    registers[j] = RegState.USED
                return i
            }
        }

        val lastFree = registers.indexOfLast { it == RegState.FREE }.let {
            if (it == -1) registers.size else it
        }
        repeat(count - (registers.size - lastFree)) {
            registers.add(RegState.USED)
        }
        return lastFree
    }

    fun markRegUsed(index: Int) {
        registers[index] = RegState.USED
    }

    fun markRegFree(index: Int) {
        registers[index] = RegState.FREE
    }

    fun loadConstant(value: Any): Int {
        constantPool.forEachIndexed { index, constant ->
            if (value == constant)
                return index
        }

        constantPool.add(value)
        return constantPool.lastIndex
    }

    class Label(var opIndex: Int?)
}
