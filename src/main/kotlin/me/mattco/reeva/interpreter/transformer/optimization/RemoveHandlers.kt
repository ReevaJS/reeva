package me.mattco.reeva.interpreter.transformer.optimization

object RemoveHandlers : Pass {
    override fun evaluate(info: OptInfo) {
        outer@ for (block in info.opcodes.blocks) {
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