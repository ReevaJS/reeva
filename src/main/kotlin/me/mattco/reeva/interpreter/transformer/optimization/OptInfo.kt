package me.mattco.reeva.interpreter.transformer.optimization

import me.mattco.reeva.interpreter.transformer.Block
import me.mattco.reeva.interpreter.transformer.FunctionOpcodes

private typealias CFGMap = MutableMap<Block, MutableSet<Block>>

class CFG private constructor(private val map: CFGMap) : CFGMap by map {
    constructor() : this(mutableMapOf())
}

data class OptInfo(
    val opcodes: FunctionOpcodes,
    var cfg: CFG = CFG(),
    var invertedCfg: CFG = CFG(),
    var exportedBlocks: MutableSet<Block> = mutableSetOf(),
    var entryBlock: Block = opcodes.blocks.first()
)
