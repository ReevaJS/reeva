package me.mattco.reeva.interpreter.transformer

import me.mattco.reeva.interpreter.transformer.opcodes.Opcode

class Block private constructor(val name: String, private val opcodes: MutableList<Opcode>) : MutableList<Opcode> by opcodes {
    var handler: Block? = null

    constructor(index: Int) : this(index.toString(), mutableListOf())
    constructor(name: String) : this(name, mutableListOf())

    val isTerminated: Boolean
        get() = lastOrNull()?.isTerminator ?: false

    override fun toString() = "Block #$name"
}
