package me.mattco.reeva.interpreter.transformer.optimization

import me.mattco.reeva.interpreter.transformer.Block
import me.mattco.reeva.interpreter.transformer.opcodes.Register
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.getOrPut

class RegisterAllocation2 : Pass {
    private val blockLiveness = mutableMapOf<Block, BlockLiveness>()
    private val backEdges = mutableMapOf<Block, MutableSet<Block>>()

    data class BlockLiveness(
        val inLiveness: MutableSet<Register>,
        val outLiveness: MutableSet<Register>,
        val usedRegisters: MutableSet<Register>,
    )

    private fun calculateBackEdges(info: OptInfo) {
        val loops = findLoops(info, info.opcodes.blocks.first())

        for (loop in loops) {
            // The back edge is from the end of the loop to the front of the loop
            val backEdges = this.backEdges.getOrPut(loop.last(), ::mutableSetOf)
            backEdges.add(loop.first())
        }
    }

    private fun findLoops(info: OptInfo, startingBlock: Block, loopChain: List<Block> = listOf()): List<List<Block>> {
        val chains = mutableListOf<List<Block>>()

        val toBlocks = info.cfg[startingBlock] ?: return chains
        for (toBlock in toBlocks) {
            val existingIndex = loopChain.indexOf(toBlock)
            if (existingIndex != -1) {
                chains.add(loopChain.drop(existingIndex))
            } else {
                chains.addAll(findLoops(info, toBlock, loopChain + listOf(toBlock)))
            }
        }

        return chains
    }

    private fun calculateLivenessForBlock(info: OptInfo, block: Block, seenBlocks: MutableSet<Block>) {
        seenBlocks.add(block)

        val (inLiveness, outLiveness, usedRegisters) = blockLiveness[block]!!
        outLiveness.addAll(inLiveness)
        val liveRegisters = inLiveness.toMutableSet()

        for (opcode in block) {
            for (readReg in opcode.readRegisters()) {
                expect(readReg in liveRegisters)
                usedRegisters.add(readReg)
            }

            for (writeReg in opcode.writeRegisters()) {
                outLiveness.add(writeReg)
                usedRegisters.add(writeReg)
                liveRegisters.add(writeReg)
            }
        }

        val successors = info.cfg[block]
        if (successors == null) {
            outLiveness.clear()
            return
        }

        for (successor in successors) {
            val isBackEdge = backEdges[block]?.contains(successor) == true
            if (!isBackEdge) {
                val successorInRegisters = blockLiveness[successor]!!.inLiveness

                if (successor in seenBlocks) {
                    val copy = successorInRegisters.toSet()
                    successorInRegisters.clear()
                    successorInRegisters.addAll(copy.intersect(outLiveness))
                } else {
                    successorInRegisters.addAll(outLiveness)
                    calculateLivenessForBlock(info, successor, seenBlocks)
                }

            }
        }
    }

    private fun calculateBlockLiveness(info: OptInfo) {
        for (block in info.opcodes.blocks)
            blockLiveness[block] = BlockLiveness(mutableSetOf(), mutableSetOf(), mutableSetOf())

        calculateLivenessForBlock(info, info.entryBlock, mutableSetOf())
    }

    private fun reduceBlockLivenessHelper(info: OptInfo, block: Block, seenBlocks: MutableList<Block>) {
        seenBlocks.add(block)

        val (inLiveness, outLiveness, usedRegisters) = blockLiveness[block]!!

        val successors = info.cfg[block]
        if (successors == null) {
            expect(outLiveness.isEmpty())
            inLiveness.retainAll(usedRegisters)
            return
        }

        val newOutLiveness = mutableSetOf<Register>()

        for (successor in successors) {
            val successorLiveness = blockLiveness[successor]!!
            if (successor !in seenBlocks) {
                reduceBlockLivenessHelper(info, successor, seenBlocks)
                newOutLiveness.addAll(successorLiveness.inLiveness)
            } else {
                val relevantRegisters = successorLiveness.inLiveness.intersect(successorLiveness.usedRegisters)
                newOutLiveness.addAll(relevantRegisters)
            }
        }

        // TODO: This is relatively expensive, remove once I know this works correctly
        expect(newOutLiveness.none { it !in outLiveness })
        outLiveness.clear()
        outLiveness.addAll(newOutLiveness)

        inLiveness.retainAll(outLiveness.union(usedRegisters))
    }

    private fun reduceBlockLiveness(info: OptInfo) {
        reduceBlockLivenessHelper(info, info.entryBlock, mutableListOf())
    }

    override fun evaluate(info: OptInfo) {
        expect(info.entryBlock == info.opcodes.blocks.first())

        calculateBackEdges(info)
        calculateBlockLiveness(info)
        reduceBlockLiveness(info)
    }
}