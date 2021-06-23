package me.mattco.reeva.interpreter.transformer

import me.mattco.reeva.interpreter.transformer.opcodes.Jump
import me.mattco.reeva.interpreter.transformer.opcodes.Opcode
import me.mattco.reeva.utils.expect

data class FunctionOpcodes(
    val blocks: List<Block>,
    val constantPool: List<Any>,
    val registerCount: Int,
)

class Generator {
    private val constantPool = mutableListOf<Any>()
    private val breakableScopes = mutableListOf<Block>()
    private val continuableScopes = mutableListOf<Block>()
    private val blocks = mutableListOf<Block>()
    private var nextRegister = 0
    private var nextBlock = 1
    var currentBlock = makeBlock()

    val breakableScope: Block
        get() = breakableScopes.last()

    val continuableScope: Block
        get() = continuableScopes.last()

    fun makeBlock() = Block(nextBlock++).also(blocks::add)

    fun reserveRegister() = nextRegister++

    fun add(opcode: Opcode) {
        expect(!currentBlock.isTerminated)
        currentBlock.add(opcode)
    }

    fun intern(obj: Any): Int {
        val index = constantPool.indexOf(obj)
        if (index != -1)
            return index

        constantPool.add(obj)
        return constantPool.lastIndex
    }

    fun enterBreakableScope(targetBlock: Block) {
        breakableScopes.add(targetBlock)
    }

    fun exitBreakableScope() {
        breakableScopes.removeLast()
    }

    fun enterContinuableScope(targetBlock: Block) {
        continuableScopes.add(targetBlock)
    }

    fun exitContinuableScope() {
        continuableScopes.removeLast()
    }

    fun ifHelper(op: (Block, Block) -> Opcode, negateOp: Boolean = false, ifTrue: () -> Unit) {
        var trueBlock = makeBlock()
        var doneBlock = makeBlock()

        if (negateOp) {
            val temp = trueBlock
            trueBlock = doneBlock
            doneBlock = temp
        }

        add(op(trueBlock, doneBlock))
        currentBlock = trueBlock
        ifTrue()
        add(Jump(doneBlock))
        currentBlock = doneBlock
    }

    fun ifElseHelper(op: (Block, Block) -> Opcode, ifTrue: () -> Unit, ifFalse: () -> Unit) {
        val trueBlock = makeBlock()
        val falseBlock = makeBlock()
        val doneBlock = makeBlock()

        add(op(trueBlock, falseBlock))
        currentBlock = trueBlock
        ifTrue()
        add(Jump(doneBlock, null))
        currentBlock = falseBlock
        ifFalse()
        add(Jump(doneBlock, null))
        currentBlock = doneBlock
    }

    fun finish() = FunctionOpcodes(
        blocks,
        constantPool,
        nextRegister
    )
}
