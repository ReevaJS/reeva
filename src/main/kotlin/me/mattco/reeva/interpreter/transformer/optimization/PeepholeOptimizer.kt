package me.mattco.reeva.interpreter.transformer.optimization

import me.mattco.reeva.interpreter.transformer.FunctionOpcodes
import me.mattco.reeva.interpreter.transformer.opcodes.Jump
import me.mattco.reeva.interpreter.transformer.opcodes.JumpAbsolute

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