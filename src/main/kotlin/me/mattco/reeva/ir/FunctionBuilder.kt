package me.mattco.reeva.ir

import me.mattco.reeva.ast.statements.BlockNode
import java.util.*

class FunctionBuilder(val argCount: Int = 1) {
    private val registers = mutableListOf<RegState>()

    val opcodes = mutableListOf<Opcode>()
    val constantPool = mutableListOf<Any>()
    val handlers = mutableListOf<IRHandler>()
    val blocks = Stack<Block>()

    // Count of env variables in current scope
    var envVarCount = 0

    // Stores how deep we currently are in the context tree from the
    // scope we started with (this function's HoistingScope)
    var nestedContexts = 0

    enum class RegState { USED, FREE }

    // If a label is jumped to before it is placed, it is placed in this
    // queue, and is "completed" when it is placed
    private val placeholders = mutableMapOf<Label, MutableList<Pair<Int, OpcodeType>>>()

    val registerCount: Int
        get() = registers.size

    init {
        for (i in 0 until argCount)
            registers.add(RegState.USED)
    }

    fun receiverReg() = 0
    fun argReg(index: Int) = index - 1
    fun reg(index: Int) = argCount - index

    fun opcodeCount() = opcodes.size

    fun getOpcode(index: Int) = opcodes[index]

    fun setOpcode(index: Int, value: Opcode) {
        opcodes[index] = value
    }

    fun addOpcode(opcode: Opcode) = opcodes.add(opcode)

    fun label() = Label(null)

    fun jump(label: Label) = jumpHelper(label, OpcodeType.Jump)

    fun jumpHelper(label: Label, type: OpcodeType) {
        if (label.opIndex != null) {
            // the label has already been placed, so we can directly insert
            // a jump instruction
            opcodes.add(Opcode(type, label.opIndex!!))
        } else {
            // the label has yet to be placed, so we have to place a marker
            // and wait for the label to be placed
            opcodes.add(Opcode(OpcodeType.JumpPlaceholder))
            val placeholderIndex = opcodes.lastIndex

            if (label !in placeholders)
                placeholders[label] = mutableListOf()
            placeholders[label]!!.add(placeholderIndex to type)
        }
    }

    fun place(label: Label): Label {
        val targetIndex = opcodes.lastIndex + 1

        placeholders.forEach {
            if (it.key != label)
                return@forEach

            it.value.forEach { (offset, jumpOp) ->
                if (opcodes[offset].type != OpcodeType.JumpPlaceholder) {
                    TODO("expected JumpPlaceholder at offset $offset")
                }
                opcodes[offset].replaceJumpPlaceholder(jumpOp, targetIndex)
            }
            placeholders[label]!!.clear()
        }

        label.opIndex = targetIndex
        return label
    }

    fun nextFreeReg(): Int {
        val index = registers.indexOfFirst { it == RegState.FREE }
        if (index == -1) {
            registers.add(RegState.USED)
            return registers.lastIndex
        }
        registers[index] = RegState.USED
        return index
    }

    fun nextFreeRegBlock(count: Int): Int {
        // TODO: Improve
        for (i in registers.indices) {
            if (i + count > registers.size)
                break
            if (registers.subList(i, i + count).all { it == RegState.FREE }) {
                for (j in i until (i + count))
                    registers[j] = RegState.USED
                return i
            }
        }

        val lastFree = registers.indexOfLast { it == RegState.FREE }.let {
            if (it == -1) registers.size else it
        }
        repeat(count - (registers.size - lastFree)) {
            registers.add(RegState.USED)
        }
        return lastFree
    }

    fun markRegUsed(index: Int) {
        registers[index] = RegState.USED
    }

    fun markRegFree(index: Int) {
        registers[index] = RegState.FREE
    }

    fun loadConstant(value: Any): Int {
        constantPool.forEachIndexed { index, constant ->
            if (value == constant)
                return index
        }

        constantPool.add(value)
        return constantPool.lastIndex
    }

    fun goto(label: Label, contextDepth: Int) {
        if (contextDepth == nestedContexts - 1) {
            opcodes.add(Opcode(OpcodeType.PopCurrentEnv))
        } else {
            opcodes.add(Opcode(OpcodeType.PopEnvs, nestedContexts - contextDepth))
        }
        jump(label)
    }

    fun pushBlock(block: Block) {
        block.contextDepth = nestedContexts
        blocks.push(block)
    }

    fun popBlock() {
        blocks.pop()
    }

    fun addHandler(start: Label, end: Label, handler: Label, isCatch: Boolean) {
        handlers.add(IRHandler(start, end, handler, isCatch, nestedContexts))
    }

    class Label(var opIndex: Int?) {
        fun shift(n: Int) = Label(opIndex!! + n)

        override fun toString() = "Label @${opIndex ?: "<null>"}"
    }

    data class IRHandler(
        val start: Label,
        val end: Label,
        val handler: Label,
        val isCatch: Boolean,
        val contextDepth: Int,
    ) {
        fun toHandler() = Handler(start.opIndex!!, end.opIndex!!, handler.opIndex!!, isCatch, contextDepth)
    }

    data class Handler(
        val start: Int,
        val end: Int,
        val handler: Int,
        val isCatch: Boolean,
        val contextDepth: Int,
    )

    abstract class Block {
        open val jsLabel: String? = null
        var contextDepth = -1
    }

    class LoopBlock(
        val continueTarget: Label,
        val breakTarget: Label,
        override val jsLabel: String? = null,
    ) : Block()

    class SwitchBlock(
        val breakTarget: Label,
        override val jsLabel: String? = null,
    ) : Block()

    class TryCatchBlock(
        val tryStart: Label,
        val tryEnd: Label,
        val catchStart: Label?,
        val finallyStart: Label?,
        val finallyNode: BlockNode?,
    ) : Block() {
        /**
         * Regions which should not be handled by this block's catch
         * or finally statement. These regions are finally blocks
         * which do not fall under the catch or finally block's
         * protection
         */
        val excludedRegions = mutableListOf<Pair<Label, Label>>()

        fun getHandlersForRegion(handler: IRHandler): List<IRHandler> {
            return getHandlersForRegion(listOf(handler), handler.isCatch)
        }

        private fun getHandlersForRegion(handlers: List<IRHandler>, isCatch: Boolean): List<IRHandler> {
            if (excludedRegions.isEmpty())
                return handlers

            val newHandlers = mutableListOf<IRHandler>()
            var split = false

            for (excludedRegion in excludedRegions) {
                val exclStart = excludedRegion.first.opIndex!!
                val exclEnd = excludedRegion.second.opIndex!!

                for (handler in handlers) {
                    val start = handler.start.opIndex!!
                    val end = handler.end.opIndex!!

                    /*
                     * There are five possible scenarios for region overlap:
                     *
                     * 1.
                     * |------------------------------|
                     * ^         |------------|       ^
                     * start     ^            ^      end
                     *        exclStart    exclEnd
                     *
                     * We have to split the current handler into two
                     * regions: [start, exclStart) and (exclEnd, end]
                     *
                     * 2.
                     *
                     * |----------------------------|
                     * ^            |--------|      ^
                     * exclStart    ^        ^   exclEnd
                     *            start     end
                     *
                     * The current handler is discarded
                     *
                     * 3.
                     * |----------------| < end
                     * ^         |-------------|
                     * start     ^             ^
                     *       exclStart      exclEnd
                     *
                     * We have to truncate the current handler into
                     * [start, exclStart)
                     *
                     * 4.
                     *    start > |--------------|
                     * |------------------|      ^
                     * ^                  ^     end
                     * exclStart         exclEnd
                     *
                     * We have to truncate the current handler into
                     * (exclEnd, end]
                     *
                     * 5.
                     * |----------|
                     * ^          ^   |------------|
                     * start     end  ^            ^
                     *            exclStart     exclEnd
                     *
                     * (...or vice-versa, with start > exclEnd)
                     *
                     * The current handler is fine
                     */

                    when {
                        start < exclStart && end > exclEnd -> {
                            split = true
                            newHandlers.add(IRHandler(handler.start, Label(exclStart - 1), handler.handler, isCatch, handler.contextDepth))
                            newHandlers.add(IRHandler(Label(exclEnd + 1), handler.end, handler.handler, isCatch, handler.contextDepth))
                        }
                        exclStart <= start && exclEnd >= end -> { /* nop */ }
                        exclStart in (start + 1)..end && end <= exclEnd -> {
                            split = true
                            newHandlers.add(IRHandler(handler.start, Label(exclStart - 1), handler.handler, isCatch, handler.contextDepth))
                        }
                        start in exclStart..exclEnd && exclEnd < end -> {
                            split = true
                            newHandlers.add(IRHandler(Label(exclEnd + 1), handler.end, handler.handler, isCatch, handler.contextDepth))
                        }
                        start > exclEnd || end < exclStart -> newHandlers.add(handler)
                    }
                }
            }

            return if (split) getHandlersForRegion(newHandlers, isCatch) else newHandlers
        }
    }
}
