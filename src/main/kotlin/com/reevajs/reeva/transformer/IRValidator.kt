package com.reevajs.reeva.transformer

import com.reevajs.reeva.transformer.opcodes.JumpInstr
import com.reevajs.reeva.transformer.opcodes.Return
import com.reevajs.reeva.utils.expect

class IRValidator(val ir: IR) {
    private var stackHeight = 0

    private val postStackHeights = mutableMapOf<Int, Int>()

    fun validate() {
        for ((index, opcode) in ir.opcodes.withIndex()) {
            for (handler in ir.handlers) {
                if (index == handler.handler) {
                    expect(stackHeight == 0) {
                        "Expected stack height to be 1 going into handler at @$index"
                    }

                    // In a handler, the only thing on the stack is the exception
                    stackHeight = 1
                }
            }

            expect(opcode !is Return || stackHeight == 1) {
                "Expected a returning stack height of 1 at opcode $index but found " +
                    "a height of $stackHeight"
            }

            stackHeight += opcode.stackHeightModifier

            if (index != 0) {
                val existingHeight = postStackHeights[index]
                expect(existingHeight == null || existingHeight == stackHeight) {
                    // TODO: Include jump location
                    "Expected stack height of $existingHeight at opcode $index, but found " +
                        "a height of $stackHeight from a previously encountered jump"
                }
            }

            postStackHeights[index] = stackHeight

            if (opcode is JumpInstr) {
                val existingHeight = postStackHeights[opcode.to - 1]
                if (existingHeight != null) {
                    expect(existingHeight == stackHeight) {
                        "Expected stack height of $existingHeight when jumping from opcode $index " +
                            "to ${opcode.to}, but found a height of $stackHeight"
                    }
                } else {
                    postStackHeights[opcode.to - 1] = stackHeight
                }
            }
        }
    }
}
