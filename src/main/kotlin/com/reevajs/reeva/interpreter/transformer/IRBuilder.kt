package com.reevajs.reeva.interpreter.transformer

class IRBuilder private constructor(
    private val opcodes: MutableList<Opcode>,
) : MutableList<Opcode> by opcodes {
    private val locals = mutableListOf<LocalKind>()

    constructor() : this(mutableListOf())

    fun ifHelper(jumpBuilder: (to: Int) -> JumpInstr, block: () -> Unit) {
        val jump = jumpBuilder(-1)
        add(jump)
        block()
        jump.to = lastIndex
    }

    fun ifElseHelper(jumpBuilder: (to: Int) -> JumpInstr, firstBlock: () -> Unit, secondBlock: () -> Unit) {
        val firstJump = jumpBuilder(-1)
        add(firstJump)
        firstBlock()

        val secondJump = Jump(-1)
        add(secondJump)
        firstJump.to = lastIndex
        secondBlock()
        secondJump.to = lastIndex
    }

    fun newLocalSlot(kind: LocalKind): Int {
        locals.add(kind)
        return locals.lastIndex
    }

    fun build(): IRPackage {
        return IRPackage(opcodes, locals)
    }
}
