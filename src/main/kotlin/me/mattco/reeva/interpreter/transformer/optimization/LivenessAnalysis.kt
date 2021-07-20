package me.mattco.reeva.interpreter.transformer.optimization

import me.mattco.reeva.interpreter.transformer.Block
import me.mattco.reeva.interpreter.transformer.BlockLiveness
import me.mattco.reeva.interpreter.transformer.FunctionOpcodes
import me.mattco.reeva.utils.expect

object LivenessAnalysis : Pass {
    override fun evaluate(opcodes: FunctionOpcodes) {
        expect(opcodes.cfg.entryBlock == opcodes.blocks.first())
        calculateBlockLiveness(opcodes)
    }

    private fun calculateLivenessForBlock(opcodes: FunctionOpcodes, block: Block, seenBlocks: MutableSet<Block>) {
        seenBlocks.add(block)

        val (inLiveness, outLiveness) = opcodes.cfg.blockLiveness[block]!!
        outLiveness.addAll(inLiveness)
        val liveRegisters = inLiveness.toMutableSet()

        for (opcode in block) {
            for (readReg in opcode.readRegisters()) {
                expect(readReg in liveRegisters)
            }

            for (writeReg in opcode.writeRegisters()) {
                outLiveness.add(writeReg)
                liveRegisters.add(writeReg)
            }
        }

        val successors = opcodes.cfg.forward[block]
        if (successors == null) {
            outLiveness.clear()
            return
        }

        for (successor in successors) {
            val isBackEdge = opcodes.cfg.backEdges[block]?.contains(successor) == true
            if (!isBackEdge) {
                val successorInRegisters = opcodes.cfg.blockLiveness[successor]!!.inLiveness

                if (successor in seenBlocks) {
                    val copy = successorInRegisters.toSet()
                    successorInRegisters.clear()
                    successorInRegisters.addAll(copy.intersect(outLiveness))
                } else {
                    successorInRegisters.addAll(outLiveness)
                    calculateLivenessForBlock(opcodes, successor, seenBlocks)
                }

            }
        }
    }

    private fun calculateBlockLiveness(opcodes: FunctionOpcodes) {
        for (block in opcodes.blocks)
            opcodes.cfg.blockLiveness[block] = BlockLiveness(mutableSetOf(), mutableSetOf())

        calculateLivenessForBlock(opcodes, opcodes.cfg.entryBlock, mutableSetOf())
    }

    // private fun reduceBlockLivenessHelper(opcodes: FunctionOpcodes, block: Block, seenBlocks: MutableList<Block>) {
    //     seenBlocks.add(block)
    //
    //     val (inLiveness, outLiveness, usedRegisters) = blockLiveness[block]!!
    //
    //     val successors = opcodes.cfg.forward[block]
    //     if (successors == null) {
    //         expect(outLiveness.isEmpty())
    //         inLiveness.retainAll(usedRegisters)
    //         return
    //     }
    //
    //     val newOutLiveness = mutableSetOf<Register>()
    //
    //     for (successor in successors) {
    //         val successorLiveness = blockLiveness[successor]!!
    //         if (successor !in seenBlocks) {
    //             reduceBlockLivenessHelper(opcodes, successor, seenBlocks)
    //             newOutLiveness.addAll(successorLiveness.inLiveness)
    //         } else {
    //             val relevantRegisters = successorLiveness.inLiveness.intersect(successorLiveness.usedRegisters)
    //             newOutLiveness.addAll(relevantRegisters)
    //         }
    //     }
    //
    //     // TODO: This is relatively expensive, remove once I know this works correctly
    //     expect(newOutLiveness.none { it !in outLiveness })
    //     outLiveness.clear()
    //     outLiveness.addAll(newOutLiveness)
    //
    //     inLiveness.retainAll(outLiveness.union(usedRegisters))
    // }
    //
    // private fun reduceBlockLiveness(opcodes: FunctionOpcodes) {
    //     reduceBlockLivenessHelper(opcodes, opcodes.cfg.entryBlock, mutableListOf())
    // }
}