package me.mattco.reeva.ir

class OpcodeBuilder {
    private val opcodes = mutableListOf<Opcode>()

    // true -> in use, false -> free
    private val registers = mutableListOf<Boolean>()

    // If a label is jumped to before it is placed, it is placed in this
    // queue, and is "completed" when it is placed
    private val placeholders = mutableMapOf<Label, MutableList<Pair<Int, (Int) -> Opcode>>>()

    private fun label() = Label(null)

    private fun jump(label: Label) = jumpHelper(label, ::Jump)

    private fun jumpHelper(label: Label, jumpOp: (Int) -> Opcode) {
        if (label.opIndex != null) {
            // the label has already been placed, so we can directly insert
            // a jump instruction
            +Jump(label.opIndex!!)
        } else {
            // the label has yet to be replaced, so we have to place a marker
            // and wait for the label to be placed
            +JumpPlaceholder
            val placeholderIndex = opcodes.lastIndex

            if (label !in placeholders)
                placeholders[label] = mutableListOf()
            placeholders[label]!!.add(placeholderIndex to jumpOp)
        }
    }

    private fun place(label: Label) {
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
        val index = registers.indexOfFirst { !it }
        if (index == -1) {
            registers.add(false)
            return registers.lastIndex
        }
        return index
    }

    fun markRegUsed(index: Int) {
        registers[index] = true
    }

    fun markRegFree(index: Int) {
        registers[index] = false
    }

    fun build() = opcodes.toTypedArray().also {
        opcodes.clear()
    }

    operator fun Opcode.unaryPlus() = opcodes.add(this)

    data class Label(var opIndex: Int?)
}
