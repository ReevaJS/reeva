package com.reevajs.reeva.interpreter.transformer

class IRBuilder(
    val argCount: Int,
    additionalReservedLocals: Int,
    val isDerivedClassConstructor: Boolean = false,
) {
    private val opcodes = mutableListOf<Opcode>()
    private val locals = mutableListOf<LocalKind>()

    val isDone: Boolean
        get() = opcodes.lastOrNull() === Return

    init {
        repeat(additionalReservedLocals) {
            locals.add(LocalKind.Value)
        }
    }

    fun finalizeOpcodes(): List<Opcode> {
        // TODO: Figure out how to do this here but also print
        //       the opcodes for debugging purposes
        // IRValidator(opcodes).validate()
        return opcodes
    }

    fun getLocals(): List<LocalKind> = locals

    fun addOpcode(opcode: Opcode) {
        opcodes.add(opcode)
    }

    fun opcodeCount(): Int = opcodes.size

    fun ifHelper(jumpBuilder: (to: Int) -> JumpInstr, block: () -> Unit) {
        val jump = jumpBuilder(-1)
        addOpcode(jump)
        block()
        jump.to = opcodeCount()
    }

    fun ifElseHelper(jumpBuilder: (to: Int) -> JumpInstr, firstBlock: () -> Unit, secondBlock: () -> Unit) {
        val firstJump = jumpBuilder(-1)
        addOpcode(firstJump)
        firstBlock()

        val secondJump = Jump(-1)
        addOpcode(secondJump)
        firstJump.to = opcodeCount()
        secondBlock()
        secondJump.to = opcodeCount()
    }

    fun newLocalSlot(kind: LocalKind): Int {
        locals.add(kind)
        return locals.lastIndex
    }
}
