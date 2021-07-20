package me.mattco.reeva.interpreter.transformer.optimization

import me.mattco.reeva.interpreter.JumpTable
import me.mattco.reeva.interpreter.transformer.Block
import me.mattco.reeva.interpreter.transformer.CFG
import me.mattco.reeva.interpreter.transformer.FunctionOpcodes
import me.mattco.reeva.interpreter.transformer.opcodes.*
import me.mattco.reeva.utils.getOrPut

object GenerateCFG : Pass {
    override fun evaluate(opcodes: FunctionOpcodes) {
        val cfg = CFG(opcodes.blocks.first())
        opcodes.cfg = cfg
        val firstBlock = opcodes.blocks.first()

        val seenBlocks = mutableSetOf(firstBlock)
        val enteredBlocks = mutableListOf(firstBlock)
        val iterators = mutableListOf<Iterator<Opcode>>(firstBlock.iterator())

        fun enterBlock(fromBlock: Block, toBlock: Block, exported: Boolean = false) {
            val entry = cfg.forward.getOrPut(fromBlock, ::mutableSetOf)
            entry.add(toBlock)
            val inverseEntry = cfg.inverted.getOrPut(toBlock, ::mutableSetOf)
            inverseEntry.add(fromBlock)

            if (exported)
                cfg.exportedBlocks.add(toBlock)

            if (toBlock !in seenBlocks) {
                seenBlocks.add(toBlock)
                enteredBlocks.add(toBlock)
                iterators.add(toBlock.iterator())

                if (toBlock.handler != null)
                    enterBlock(toBlock, toBlock.handler!!)
            }
        }

        if (firstBlock.handler != null)
            enterBlock(firstBlock, firstBlock.handler!!)

        while (enteredBlocks.isNotEmpty()) {
            if (!iterators.last().hasNext()) {
                enteredBlocks.removeLast()
                iterators.removeLast()
                continue
            }

            val instruction = iterators.last().next()
            if (!instruction.isTerminator)
                continue

            val currentBlock = enteredBlocks.last()

            if (instruction is JumpFromTable) {
                val jumpTable = opcodes.constantPool[instruction.table] as JumpTable
                for (target in jumpTable.values)
                    enterBlock(currentBlock, target)
                continue
            }

            if (instruction is Jump) {
                enterBlock(currentBlock, instruction.ifBlock)
                if (instruction.elseBlock != null)
                    enterBlock(currentBlock, instruction.elseBlock!!)
                continue
            }

            if (instruction is Yield) {
                enterBlock(currentBlock, instruction.continuationBlock, exported = true)
                continue
            }

            if (instruction is Await) {
                enterBlock(currentBlock, instruction.continuationBlock, exported = true)
                continue
            }

            iterators.removeLast()
            enteredBlocks.removeLast()
        }
    }
}