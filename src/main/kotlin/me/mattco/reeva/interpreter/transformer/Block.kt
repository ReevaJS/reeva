package me.mattco.reeva.interpreter.transformer

import me.mattco.reeva.interpreter.transformer.opcodes.Opcode

class Block private constructor(val index: Int, private val opcodes: MutableList<Opcode>) : MutableList<Opcode> by opcodes {
    constructor(index: Int) : this(index, mutableListOf())

    val isTerminated: Boolean
        get() = lastOrNull()?.isTerminator ?: false

    override fun toString() = "Block #$index"
}
