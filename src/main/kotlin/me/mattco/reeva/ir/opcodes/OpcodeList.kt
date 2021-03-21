package me.mattco.reeva.ir.opcodes

import me.mattco.reeva.ir.Opcode
import me.mattco.reeva.ir.OpcodeArgType

class OpcodeList(private val opcodes: MutableList<Opcode>) : MutableList<Opcode> by opcodes {
    private val jumpInstrs = mutableMapOf<Opcode, Int>()

    init {
        for ((index, opcode) in opcodes.withIndex()) {
            if (opcode.type.types.lastOrNull() == OpcodeArgType.InstrIndex)
                jumpInstrs[opcode] = index
        }
    }

    fun getOpcodes() = opcodes

    override fun clear() {
        opcodes.clear()
        jumpInstrs.clear()
    }

    override fun add(element: Opcode): Boolean {
        add(opcodes.size, element)
        return true
    }

    override fun add(index: Int, element: Opcode) {
        for ((jumpOpcode, jumpIndex) in jumpInstrs) {
            if (index >= jumpIndex)
                jumpOpcode.args[0] = (jumpOpcode.args[0] as Int) + 1
            if (index <= jumpIndex)
                jumpInstrs[jumpOpcode] = jumpIndex + 1
        }
        if (element.type.types.lastOrNull() == OpcodeArgType.InstrIndex)
            jumpInstrs[element] = index
        opcodes.add(index, element)
    }

    override fun remove(element: Opcode): Boolean {
        val indexOf = opcodes.indexOf(element)
        if (indexOf == -1)
            return false
        removeAt(opcodes.indexOf(element))
        return true
    }

    override fun removeAt(index: Int): Opcode {
        val opcode = opcodes.removeAt(index)
        jumpInstrs.remove(opcode)

        for ((jumpOpcode, jumpIndex) in jumpInstrs) {
            if (index >= jumpIndex)
                jumpOpcode.args[0] = (jumpOpcode.args[0] as Int) - 1
            if (index <= jumpIndex)
                jumpInstrs[jumpOpcode] = jumpIndex - 1
        }

        return opcode
    }

    override fun set(index: Int, element: Opcode): Opcode {
        val old = opcodes[index]
        if (old in jumpInstrs)
            jumpInstrs.remove(old)
        if (element.type.types.lastOrNull() == OpcodeArgType.InstrIndex)
            jumpInstrs[element] = index
        opcodes[index] = element
        return old
    }
}
