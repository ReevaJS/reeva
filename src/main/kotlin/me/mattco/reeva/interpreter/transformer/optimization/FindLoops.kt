package me.mattco.reeva.interpreter.transformer.optimization

import me.mattco.reeva.interpreter.transformer.Block
import me.mattco.reeva.interpreter.transformer.FunctionOpcodes
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.getOrPut

object FindLoops : Pass {
    override fun evaluate(opcodes: FunctionOpcodes) {
        expect(opcodes.cfg.entryBlock == opcodes.blocks.first())
        calculateBackEdges(opcodes)
    }

    private fun calculateBackEdges(opcodes: FunctionOpcodes) {
        val loops = findLoops(opcodes, opcodes.blocks.first())

        for (loop in loops) {
            // The back edge is from the end of the loop to the front of the loop
            val backEdges = opcodes.cfg.backEdges.getOrPut(loop.last(), ::mutableSetOf)
            backEdges.add(loop.first())
        }
    }

    private fun findLoops(opcodes: FunctionOpcodes, startingBlock: Block, loopChain: List<Block> = listOf()): List<List<Block>> {
        val chains = mutableListOf<List<Block>>()

        val toBlocks = opcodes.cfg.forward[startingBlock] ?: return chains
        for (toBlock in toBlocks) {
            val existingIndex = loopChain.indexOf(toBlock)
            if (existingIndex != -1) {
                chains.add(loopChain.drop(existingIndex))
            } else {
                chains.addAll(findLoops(opcodes, toBlock, loopChain + listOf(toBlock)))
            }
        }

        return chains
    }
}
