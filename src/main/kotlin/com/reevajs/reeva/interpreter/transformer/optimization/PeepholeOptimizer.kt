package com.reevajs.reeva.interpreter.transformer.optimization

import com.reevajs.reeva.interpreter.transformer.FunctionOpcodes
import com.reevajs.reeva.interpreter.transformer.opcodes.Jump
import com.reevajs.reeva.interpreter.transformer.opcodes.JumpAbsolute

object PeepholeOptimizer : Pass {
    override fun evaluate(opcodes: FunctionOpcodes) {
        for (block in opcodes.blocks) {
            for (opcode in block) {
                if (opcode is Jump && opcode !is JumpAbsolute) {

                }
            }
        }
    }
}