package com.reevajs.reeva.interpreter.transformer

class IRBuilder(
    val argCount: Int,
    additionalReservedLocals: Int,
    val isDerivedClassConstructor: Boolean = false,
) {
    private val opcodes = mutableListOf<Opcode>()
    private val locals = mutableListOf<LocalKind>()

    init {
        repeat(additionalReservedLocals) {
            locals.add(LocalKind.Value)
        }
    }

    fun getOpcodes(): List<Opcode> = opcodes

    fun getLocals(): List<LocalKind> = locals

    fun addOpcode(opcode: Opcode) {
        opcodes.add(opcode)
    }

    fun opcodeCount(): Int = opcodes.size

    fun ifHelper(jumpBuilder: (to: Int) -> JumpInstr, block: () -> Unit) {
        val jump = jumpBuilder(-1)
        addOpcode(jump)
        block()
        jump.to = opcodeCount() - 1
    }

    fun ifElseHelper(jumpBuilder: (to: Int) -> JumpInstr, firstBlock: () -> Unit, secondBlock: () -> Unit) {
        val firstJump = jumpBuilder(-1)
        addOpcode(firstJump)
        firstBlock()

        val secondJump = Jump(-1)
        addOpcode(secondJump)
        firstJump.to = opcodeCount() - 1
        secondBlock()
        secondJump.to = opcodeCount() - 1
    }

    fun newLocalSlot(kind: LocalKind): Int {
        locals.add(kind)
        return locals.lastIndex
    }
}
