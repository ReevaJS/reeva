package me.mattco.reeva.interpreter.transformer.optimization

import me.mattco.reeva.interpreter.transformer.FunctionOpcodes

object RemoveHandlers : Pass {
    override fun evaluate(opcodes: FunctionOpcodes) {
        outer@ for (block in opcodes.blocks) {
            if (block.handler == null)
                continue

            for (opcode in block) {
                if (opcode.isThrowing)
                    continue@outer
            }

            block.handler = null
        }
    }
}