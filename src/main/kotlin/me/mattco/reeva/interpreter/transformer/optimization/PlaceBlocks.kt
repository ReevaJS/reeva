package me.mattco.reeva.interpreter.transformer.optimization

import me.mattco.reeva.interpreter.transformer.Block

object PlaceBlocks : Pass {
    override fun evaluate(info: OptInfo) {
        val cfg = info.cfg

        val reachableBlocks = mutableSetOf<Block>()

        fun visit(block: Block) {
            if (block in reachableBlocks)
                return

            reachableBlocks.add(block)

            cfg[block]?.forEach(::visit)
        }

        visit(info.opcodes.blocks.first())

        info.opcodes.blocks.clear()
        info.opcodes.blocks.addAll(reachableBlocks)
    }
}