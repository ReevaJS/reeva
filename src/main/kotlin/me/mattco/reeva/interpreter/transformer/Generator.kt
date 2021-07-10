package me.mattco.reeva.interpreter.transformer

import me.mattco.reeva.interpreter.transformer.opcodes.Jump
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

data class FunctionOpcodes(
    val blocks: List<Block>,
    val constantPool: List<Any>,
    val registerCount: Int,
)

class Generator(
    argCount: Int,
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
        addIfNotTerminated(Jump(doneBlock))
        currentBlock = doneBlock
    }

    fun ifElseHelper(op: (Block, Block) -> Opcode, ifTrue: () -> Unit, ifFalse: () -> Unit) {
        val trueBlock = makeBlock()
        val falseBlock = makeBlock()
        val doneBlock = makeBlock()

        add(op(trueBlock, falseBlock))
        currentBlock = trueBlock
        ifTrue()
        addIfNotTerminated(Jump(doneBlock, null))
        currentBlock = falseBlock
        ifFalse()
        addIfNotTerminated(Jump(doneBlock, null))
        currentBlock = doneBlock
    }

    fun finish() = FunctionOpcodes(
        blocks,
        constantPool,
        nextRegister,
    )
}
