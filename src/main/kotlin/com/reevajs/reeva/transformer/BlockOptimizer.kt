package com.reevajs.reeva.transformer

import com.reevajs.reeva.transformer.opcodes.*
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.unreachable

data class BlockMetadata(
    var stackHeightDelta: Int,
    var initialStackHeight: Int? = null,
    val predecessorBlocks: MutableSet<BlockIndex> = mutableSetOf(),
    val successorBlocks: MutableSet<BlockIndex> = mutableSetOf(),
    var isHandlerBlock: Boolean = false,
)

class BlockOptimizer(private val ir: IR) {
    private var blocks = ir.blocks.toMutableMap()
    private var locals = ir.locals.toMutableList()
    private val blockMetadata = mutableMapOf<BlockIndex, BlockMetadata>()

    fun optimize(): IR {
        collectMetadata()

        // TODO: Investigate whether or not it is actually worth it to do things such as
        //       block inlining/peephole optimization/local lifetime analysis. It is likely
        //       that the time taken to do it here is less than the time the JVM will take to
        //       perform the same optimizations on the class files we eventually generate.
        //       For the time being, this is more of a verifier than an optimizer.

        return IR(
            ir.argCount,
            blocks,
            locals,
        )
    }

    private fun collectMetadata() {
        blockMetadata.clear()

        for ((index, block) in ir.blocks) {
            var delta = 0
            for (opcode in block.opcodes)
                delta += opcode.stackHeightModifier

            blockMetadata[index] = BlockMetadata(delta)
        }

        initializeGraph()

        for ((_, block) in ir.blocks) {
            if (block.handlerBlock != null) {
                blockMetadata[block.handlerBlock]!!.also {
                    it.isHandlerBlock = true
                    it.initialStackHeight = 1
                }
            }
        }

        // DCE is part of the metadata phase since it is only ever performed once. Further
        // optimizations will not render a block unvisited
        performDCE()

        determineStackHeights()

        blockMetadata.filter {
            it.value.successorBlocks.isEmpty()
        }.forEach {
            expect(it.value.initialStackHeight!! + it.value.stackHeightDelta == 0) {
                val height = it.value.initialStackHeight!! + it.value.stackHeightDelta
                "Expected terminating blocks to have stack height of 0, but found $height"
            }
        }
    }

    /**
     * Populates BlockMetadata::predecessorBlocks and BlockMetadata::successorBlocks
     * for all blocks
     */
    private fun initializeGraph() {
        for (block in ir.blocks.values) {
            val metadata = blockMetadata[block.index]!!

            expect(block.opcodes.isNotEmpty()) {
                "Block ${block.index} is empty"
            }

            val lastOpcode = block.opcodes.last()

            expect(lastOpcode is TerminatingOpcode) {
                "Block ${block.index} does not end in a terminating opcode"
            }

            val successorBlocks = when (lastOpcode) {
                is Return, is Throw -> emptyList()
                is Yield -> listOf(lastOpcode.target)
                is Await -> listOf(lastOpcode.target)
                is Jump -> listOf(lastOpcode.target)
                is JumpIfTrue -> listOf(lastOpcode.trueTarget, lastOpcode.falseTarget)
                is JumpIfToBooleanTrue -> listOf(lastOpcode.trueTarget, lastOpcode.falseTarget)
                is JumpIfUndefined -> listOf(lastOpcode.undefinedTarget, lastOpcode.elseTarget)
                is JumpIfNullish -> listOf(lastOpcode.nullishTarget, lastOpcode.elseTarget)
                else -> unreachable()
            }

            metadata.successorBlocks.addAll(successorBlocks)

            successorBlocks.forEach {
                blockMetadata[it]!!.predecessorBlocks.add(block.index)
            }
        }
    }

    /**
     * Eliminates unreferenced blocks
     */
    private fun performDCE() {
        val visited = mutableSetOf<BlockIndex>()

        fun visit(block: BlockIndex) {
            if (visited.add(block)) {
                blocks[block]!!.handlerBlock?.let(::visit)
                blockMetadata[block]!!.successorBlocks.forEach(::visit)
            }
        }

        visit(BlockIndex(0))

        for (index in blocks.keys.toSet()) {
            if (index !in visited) {
                blocks.remove(index)
                val metadata = blockMetadata.remove(index)!!
                for (successor in metadata.successorBlocks)
                    blockMetadata[successor]?.predecessorBlocks?.remove(index)
            }
        }
    }

    private fun determineStackHeights() {
        blockMetadata[BlockIndex(0)]!!.initialStackHeight = 0

        fun visit(block: BlockIndex) {
            val metadata = blockMetadata[block]!!
            val finalStackHeight = metadata.initialStackHeight!! + metadata.stackHeightDelta

            for (successor in metadata.successorBlocks) {
                val successorMetadata = blockMetadata[successor]!!
                if (successorMetadata.initialStackHeight != null) {
                    expect(successorMetadata.initialStackHeight == finalStackHeight) {
                        "Expected block $successor to have initial stack height of $finalStackHeight, but found " +
                            successorMetadata.initialStackHeight.toString()
                    }
                } else {
                    successorMetadata.initialStackHeight = finalStackHeight
                    visit(successor)
                }
            }
        }

        visit(BlockIndex(0))

        for ((index, metadata) in blockMetadata) {
            if (metadata.isHandlerBlock)
                visit(index)
        }
    }
}
