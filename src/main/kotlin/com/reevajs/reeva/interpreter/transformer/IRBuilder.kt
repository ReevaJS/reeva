package com.reevajs.reeva.interpreter.transformer

import com.reevajs.reeva.interpreter.transformer.opcodes.Jump
import com.reevajs.reeva.interpreter.transformer.opcodes.JumpInstr
import com.reevajs.reeva.interpreter.transformer.opcodes.Opcode
import com.reevajs.reeva.interpreter.transformer.opcodes.Return

@JvmInline
value class Local(val value: Int) {
    override fun toString() = value.toString()
}

class IRBuilder(
    val argCount: Int,
    additionalReservedLocals: Int,
    val isDerivedClassConstructor: Boolean = false,
) {
    private val opcodes = mutableListOf<Opcode>()
    private val locals = mutableListOf<LocalKind>()
    private val childFunctions = mutableListOf<FunctionInfo>()

    val isDone: Boolean
        get() = opcodes.lastOrNull() === Return

    init {
        // Receiver + new.target
        locals.add(LocalKind.Value)
        locals.add(LocalKind.Value)

        repeat(additionalReservedLocals) {
            locals.add(LocalKind.Value)
        }
    }

    fun addChildFunction(function: FunctionInfo) {
        childFunctions.add(function)
    }

    fun getChildFunctions(): List<FunctionInfo> = childFunctions

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

    fun newLocalSlot(kind: LocalKind): Local {
        locals.add(kind)
        return Local(locals.lastIndex)
    }
}
