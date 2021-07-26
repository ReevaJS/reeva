package com.reevajs.reeva.interpreter.transformer.optimization

import com.reevajs.reeva.interpreter.transformer.Block
import com.reevajs.reeva.interpreter.transformer.FunctionOpcodes

object PlaceBlocks : Pass {
    override fun evaluate(opcodes: FunctionOpcodes) {
        val cfg = opcodes.analysis

        val reachableBlocks = mutableSetOf<Block>()

        fun visit(block: Block) {
            if (block in reachableBlocks)
                return

            reachableBlocks.add(block)

            cfg.forwardCFG[block]?.forEach(::visit)
        }

        visit(opcodes.blocks.first())

        opcodes.blocks.clear()
        opcodes.blocks.addAll(reachableBlocks)
    }
}