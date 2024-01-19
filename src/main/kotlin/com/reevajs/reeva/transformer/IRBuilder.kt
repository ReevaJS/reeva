package com.reevajs.reeva.transformer

import com.reevajs.reeva.transformer.opcodes.*

@JvmInline
value class Local(val value: Int) {
    override fun toString() = value.toString()
}

@JvmInline
value class BlockIndex(val value: Int) {
    override fun toString() = value.toString()
}

class BasicBlock(
    val index: BlockIndex,
    val identifier: String?,
    val opcodes: MutableList<Opcode>,
    var handlerBlock: BlockIndex?,
    val envDepth: Int,
) {
    override fun toString() = "Block($identifier)"
}

data class IR(
    val argCount: Int,
    val blocks: Map<BlockIndex, BasicBlock>,
    val locals: List<LocalKind>,
    val strict: Boolean,

    // Just so we can print them with the top-level script, not actually
    // necessary for function.
    val nestedFunctions: List<FunctionInfo>,
)

data class VarName(val name: String, val isFunc: Boolean)
data class LexName(val name: String, val isConst: Boolean)

// Used for global/functionDeclarationInstantiation
data class IRScope(
    val varNames: List<VarName>,
    val lexNames: List<LexName>,
)

class IRBuilder(
    val argCount: Int,
    additionalReservedLocals: Int, 
    private val strict: Boolean,
    initialEnvDepth: Int,
) {
    private val locals = mutableListOf<LocalKind>()
    private val nestedFunctions = mutableListOf<FunctionInfo>()
    private val blocks = mutableMapOf<BlockIndex, BasicBlock>()
    private val handlerBlocks = mutableListOf<BlockIndex>()
    private var activeBlock: BasicBlock
    private var nextBlockIndex = 0

    var envDepth = initialEnvDepth
        private set

    init {
        // Receiver + new.target
        locals.add(LocalKind.Value)
        locals.add(LocalKind.Value)

        repeat(additionalReservedLocals) {
            locals.add(LocalKind.Value)
        }

        activeBlock = blocks[makeBlock("Start")]!!
    }

    fun activeBlockReturns() = activeBlock.opcodes.lastOrNull().let {
        it == Return || it == Throw
    }

    fun activeBlockIsTerminated() = activeBlock.opcodes.lastOrNull() is TerminatingOpcode

    fun enterBlock(index: BlockIndex) {
        activeBlock = blocks[index]!!
    }

    fun pushHandlerBlock(block: BlockIndex) {
        handlerBlocks.add(block)
    }

    fun popHandlerBlock() {
        handlerBlocks.removeLast()
    }

    fun addNestedFunction(function: FunctionInfo) {
        nestedFunctions.add(function)
    }

    fun addOpcode(opcode: Opcode) {
        activeBlock.opcodes.add(opcode)
        if (opcode == PushDeclarativeEnvRecord || opcode == PushModuleEnvRecord) {
            envDepth++
        } else if (opcode == PopEnvRecord) {
            envDepth--
        }
    }

    fun removeLastOpcodeIfPop(): Boolean {
        return if (activeBlock.opcodes.lastOrNull() == Pop) {
            activeBlock.opcodes.removeLast()
            true
        } else false
    }

    fun makeBlock(name: String? = null): BlockIndex {
        val index = BlockIndex(nextBlockIndex++)
        blocks[index] = BasicBlock(index, name, mutableListOf(), handlerBlocks.lastOrNull(), envDepth)
        return index
    }

    fun newLocalSlot(kind: LocalKind): Local {
        locals.add(kind)
        return Local(locals.lastIndex)
    }

    fun build() = BlockOptimizer(IR(
        argCount,
        blocks,
        locals,
        strict,
        nestedFunctions,
    )).optimize()
}
