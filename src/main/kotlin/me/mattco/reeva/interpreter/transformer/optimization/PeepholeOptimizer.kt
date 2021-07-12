package me.mattco.reeva.interpreter.transformer.optimization

import me.mattco.reeva.interpreter.transformer.opcodes.Jump
import me.mattco.reeva.interpreter.transformer.opcodes.JumpAbsolute

object PeepholeOptimizer : Pass {
    override fun evaluate(info: OptInfo) {
        for (block in info.opcodes.blocks) {
            for (opcode in block) {
                if (opcode is Jump && opcode !is JumpAbsolute) {

                }
            }
        }
    }
}