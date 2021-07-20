package me.mattco.reeva.interpreter.transformer

import me.mattco.reeva.interpreter.transformer.opcodes.JumpAbsolute
import me.mattco.reeva.interpreter.transformer.opcodes.Opcode
import me.mattco.reeva.interpreter.transformer.opcodes.Register
import me.mattco.reeva.utils.expect
import java.util.*

class HandlerScope(
    val catchBlock: Block?,
    val finallyBlock: Block?
) {
    private var actionRegisterBacker: Register? = null
    private var scratchRegisterBacker: Register? = null

    fun actionRegister(generator: Generator): Register {
        if (actionRegisterBacker == null)
            actionRegisterBacker = generator.reserveRegister()
        return actionRegisterBacker!!
    }

    fun scratchRegister(generator: Generator): Register {
        if (scratchRegisterBacker == null)
            scratchRegisterBacker = generator.reserveRegister()
        return scratchRegisterBacker!!
    }
}

data class BlockLiveness(
    val inLiveness: MutableSet<Register>,
    val outrLiveness: MutableSet<Register>,
)

class Analysis(var entryBlock: Block) {
    // Map of blocks to the blocks it jumps to
    val forwardCFG = mutableMapOf<Block, MutableSet<Block>>()
    // Map of blocks to the blocks that jump to it
    val invertedCFG = mutableMapOf<Block, MutableSet<Block>>()

    val backEdges = mutableMapOf<Block, MutableSet<Block>>()

    // Blocks which are exported via a Yield terminator
    val exportedBlocks = mutableSetOf<Block>()

    val blockLiveness = mutableMapOf<Block, BlockLiveness>()
}

data class FunctionOpcodes(
    val blocks: MutableList<Block>,
    val constantPool: MutableList<Any>,
    var registerCount: Int,
    val argCount: Int,
    val feedbackCount: Int,
    var analysis: Analysis = Analysis(blocks.first()),
)

class Generator(
    private val argCount: Int,
    additionalInlineableRegisterCount: Int,
    val isDerivedClassConstructor: Boolean = false,
) {
    private val blocks = mutableListOf<Block>()
    private val constantPool = mutableListOf<Any>()

    private val handlerScopeStack = LinkedList<HandlerScope>()
    val handlerScope: HandlerScope?
        get() = handlerScopeStack.firstOrNull()

    private var nextRegister = argCount + additionalInlineableRegisterCount
    private var nextBlock = 1
    var currentBlock = makeBlock()
        set(value) {
            expect(field.isTerminated) {
                "Attempt to change block when current block is not yet terminated"
            }
            field = value
        }

    private var nextFeedbackSlot = 0

    fun makeBlock(): Block {
        val block = Block(nextBlock++)
        blocks.add(block)

        val catchScope = handlerScopeStack.firstOrNull { it.catchBlock != null }?.catchBlock
        if (catchScope != null)
            block.handler = catchScope

        return block
    }

    fun enterHandlerScope(catchBlock: Block?, finallyBlock: Block?) {
        expect(catchBlock != null || finallyBlock != null)
        handlerScopeStack.push(HandlerScope(catchBlock, finallyBlock))
    }

    fun exitHandlerScope(): HandlerScope {
        return handlerScopeStack.pop()
    }

    fun reserveRegister() = nextRegister++

    fun reserveFeedbackSlot() = nextFeedbackSlot++

    fun add(opcode: Opcode) {
        expect(!currentBlock.isTerminated)
        currentBlock.add(opcode)
    }

    fun addIfNotTerminated(opcode: Opcode) {
        if (!currentBlock.isTerminated)
            currentBlock.add(opcode)
    }

    fun intern(obj: Any): Int {
        val index = constantPool.indexOf(obj)
        if (index != -1)
            return index

        constantPool.add(obj)
        return constantPool.lastIndex
    }

    fun ifHelper(op: (Block, Block) -> Opcode, negateOp: Boolean = false, ifTrue: () -> Unit) {
        val trueBlock = makeBlock()
        val doneBlock = makeBlock()

        val firstBlock = if (negateOp) doneBlock else trueBlock
        val secondBlock = if (negateOp) trueBlock else doneBlock

        add(op(firstBlock, secondBlock))
        currentBlock = trueBlock
        ifTrue()
        addIfNotTerminated(JumpAbsolute(doneBlock))
        currentBlock = doneBlock
    }

    fun ifElseHelper(op: (Block, Block) -> Opcode, ifTrue: () -> Unit, ifFalse: () -> Unit) {
        val trueBlock = makeBlock()
        val falseBlock = makeBlock()
        val doneBlock = makeBlock()

        add(op(trueBlock, falseBlock))
        currentBlock = trueBlock
        ifTrue()
        addIfNotTerminated(JumpAbsolute(doneBlock))
        currentBlock = falseBlock
        ifFalse()
        addIfNotTerminated(JumpAbsolute(doneBlock))
        currentBlock = doneBlock
    }

    fun finish() = FunctionOpcodes(
        blocks,
        constantPool,
        nextRegister,
        argCount,
        nextFeedbackSlot,
    )
}
