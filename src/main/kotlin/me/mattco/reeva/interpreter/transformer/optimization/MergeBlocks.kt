package me.mattco.reeva.interpreter.transformer.optimization

import me.mattco.reeva.interpreter.JumpTable
import me.mattco.reeva.interpreter.transformer.Block
import me.mattco.reeva.interpreter.transformer.opcodes.JumpAbsolute
import me.mattco.reeva.utils.expect

object MergeBlocks : Pass {
    override fun evaluate(info: OptInfo) {
        val (opcodes, cfg, invertedCfg, exported) = info

        var blocksToMerge = mutableSetOf<Block>()
        val blocksToReplace = mutableMapOf<Block, Block>()
        val blocksToRemove = mutableSetOf<Block>()

        for (entry in cfg) {
            if (entry.value.size != 1)
                continue

            val destinationBlock = entry.value.first()

            if (destinationBlock in exported)
                continue

            val firstInstr = entry.key.first()
            if (firstInstr is JumpAbsolute) {
                blocksToReplace[entry.key] = firstInstr.ifBlock
                continue
            }

            if (invertedCfg[destinationBlock]?.size != 1)
                continue

            if (entry.key.handler != destinationBlock.handler)
                continue

            blocksToMerge.add(entry.key)
        }

        for (entry in blocksToReplace) {
            var replacement = entry.value
            while (replacement in blocksToReplace) {
                val newReplacement = blocksToReplace[replacement]!!
                if (newReplacement == replacement)
                    break
                replacement = newReplacement
            }
            blocksToReplace[entry.key] = replacement
        }

        val jumpTables = info.opcodes.constantPool.filterIsInstance<JumpTable>()

        fun replaceBlocks(blocks: List<Block>, replacement: Block): Int? {
            var firstSuccessorPosition: Int? = null

            for (block in blocks) {
                blocksToRemove.add(block)
                if (firstSuccessorPosition == null) {
                    firstSuccessorPosition = opcodes.blocks.indexOf(block)
                    expect(firstSuccessorPosition != -1)
                }

                if (block == info.entryBlock)
                    info.entryBlock = replacement

                for (jumpTable in jumpTables) {
                    for ((value, targetBlock) in jumpTable) {
                        if (targetBlock == block)
                            jumpTable[value] = replacement
                    }
                }
            }

            for (block in opcodes.blocks) {
                for (opcode in block) {
                    for (entry in blocks) {
                        opcode.replaceBlock(entry, replacement)
                    }
                }
            }

            return firstSuccessorPosition
        }

        for (entry in blocksToReplace)
            replaceBlocks(listOf(entry.key), entry.value)

        while (blocksToMerge.isNotEmpty()) {
            val currentBlock = blocksToMerge.first()
            blocksToMerge.remove(currentBlock)
            val successors = mutableListOf(currentBlock)

            while (true) {
                val last = successors.last()
                val entry = cfg[last] ?: break
                val successor = entry.first()
                successors.add(successor)
                if (successor in blocksToMerge) {
                    blocksToMerge.remove(successor)
                } else break
            }

            val blocksToMergeCopy = blocksToMerge.toMutableSet()

            for (last in blocksToMerge) {
                val entry = cfg[last] ?: continue
                val successor = entry.first()
                val index = successors.indexOf(successor)
                if (index != -1) {
                    successors.add(index, last)
                    blocksToMergeCopy.remove(last)
                }
            }

            blocksToMerge = blocksToMergeCopy

            val name = successors.joinToString(separator = ":") { it.name }
            val newBlock = Block(name)

            expect(successors.distinctBy { it.handler }.size == 1)

            for (successor in successors)
                newBlock.addAll(successor.takeWhile { !it.isTerminator })
            newBlock.add(successors.last().last())

            val firstSuccessorPosition = replaceBlocks(successors, newBlock)
            expect(firstSuccessorPosition != null)
            opcodes.blocks.add(firstSuccessorPosition, newBlock)
        }

        opcodes.blocks.removeAll(blocksToRemove)

        val entryIndex = opcodes.blocks.indexOf(info.entryBlock)
        expect(entryIndex != -1)
        if (entryIndex != 0) {
            opcodes.blocks.removeAt(entryIndex)
            opcodes.blocks.add(0, info.entryBlock)
        }
    }
}