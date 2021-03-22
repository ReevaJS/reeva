package me.mattco.reeva.ir.opcodes

class IrOpcodeList(private val opcodes: MutableList<IrOpcode>) : MutableList<IrOpcode> by opcodes {
    private val jumpInstrs = mutableMapOf<IrOpcode, Int>()

    init {
        for ((index, opcode) in opcodes.withIndex()) {
            if (opcode.type.types.lastOrNull() == IrOpcodeArgType.InstrIndex)
                jumpInstrs[opcode] = index
        }
    }

    fun getOpcodes() = opcodes

    override fun clear() {
        opcodes.clear()
        jumpInstrs.clear()
    }

    override fun add(element: IrOpcode): Boolean {
        add(opcodes.size, element)
        return true
    }

    override fun add(index: Int, element: IrOpcode) {
        for ((jumpOpcode, jumpIndex) in jumpInstrs) {
            if (index < jumpOpcode.args[0] as Int)
                jumpOpcode.args[0] = (jumpOpcode.args[0] as Int) + 1
            if (index <= jumpIndex)
                jumpInstrs[jumpOpcode] = jumpIndex + 1
        }
        if (element.type.types.lastOrNull() == IrOpcodeArgType.InstrIndex)
            jumpInstrs[element] = index
        opcodes.add(index, element)
    }

    override fun remove(element: IrOpcode): Boolean {
        val indexOf = opcodes.indexOf(element)
        if (indexOf == -1)
            return false
        removeAt(opcodes.indexOf(element))
        return true
    }

    override fun removeAt(index: Int): IrOpcode {
        val opcode = opcodes.removeAt(index)
        jumpInstrs.remove(opcode)

        for ((jumpOpcode, jumpIndex) in jumpInstrs) {
            if (index < jumpOpcode.args[0] as Int)
                jumpOpcode.args[0] = (jumpOpcode.args[0] as Int) - 1
            if (index <= jumpIndex)
                jumpInstrs[jumpOpcode] = jumpIndex - 1
        }

        return opcode
    }

    override fun set(index: Int, element: IrOpcode): IrOpcode {
        val old = opcodes[index]
        if (old in jumpInstrs)
            jumpInstrs.remove(old)
        if (element.type.types.lastOrNull() == IrOpcodeArgType.InstrIndex)
            jumpInstrs[element] = index
        opcodes[index] = element
        return old
    }
}
