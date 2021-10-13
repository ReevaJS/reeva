package com.reevajs.reeva.interpreter.transformer

import com.reevajs.reeva.interpreter.transformer.opcodes.JumpInstr
import com.reevajs.reeva.interpreter.transformer.opcodes.Opcode
import com.reevajs.reeva.interpreter.transformer.opcodes.Return

class IRValidator(val opcodes: List<Opcode>) {
    private var stackHeight = 0

    private val stackHeights = mutableMapOf<Int, Int>()

    fun validate() {
        for ((index, opcode) in opcodes.withIndex()) {
            var existingHeight = stackHeights[index]
            if (existingHeight != null && existingHeight != stackHeight) {
                // TODO: Include jump location
                throw IllegalStateException(
                    "Expected stack height of $existingHeight at opcode ${index}, but found " +
                        "a height of $stackHeight from a previously encountered jump"
                )
            }

            if (opcode is Return && stackHeight != 1) {
                throw IllegalStateException(
                    "Expected a returning stack height of 1 at opcode $index but found " +
                        "a height of $stackHeight"
                )
            }

            stackHeight += opcode.stackHeightModifier
            stackHeights[index] = stackHeight

            if (opcode is JumpInstr) {
                existingHeight = stackHeights[opcode.to]
                if (existingHeight != null) {
                    if (existingHeight != stackHeight) {
                        throw IllegalStateException(
                            "Expected stack height of $existingHeight when jumping from opcode $index " +
                                "to ${opcode.to}, but found a height of $stackHeight"
                        )
                    }
                } else {
                    stackHeights[opcode.to] = stackHeight
                }
            }
        }
    }
}
