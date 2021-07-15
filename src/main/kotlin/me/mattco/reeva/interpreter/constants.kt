package me.mattco.reeva.interpreter

import me.mattco.reeva.interpreter.transformer.Block
import me.mattco.reeva.interpreter.transformer.opcodes.ConstantIndex
import me.mattco.reeva.runtime.Operations

class DeclarationsArray(
    varDecls: List<String>,
    lexDecls: List<String>,
    funcDecls: List<String>,
) : Iterable<String> {
    private val values = varDecls.toTypedArray() + lexDecls.toTypedArray() + funcDecls.toTypedArray()
    private val firstLex = varDecls.size
    private val firstFunc = firstLex + lexDecls.size

    val size: Int
        get() = values.size

    override fun iterator() = values.iterator()

    fun varIterator() = getValuesIterator(0, firstLex)
    fun lexIterator() = getValuesIterator(firstLex, firstFunc)
    fun funcIterator() = getValuesIterator(firstFunc, values.size)

    private fun getValuesIterator(start: Int, end: Int) = object : Iterable<String> {
        override fun iterator() = object : Iterator<String> {
            private var i = start

            override fun hasNext() = i < end
            override fun next() = values[i++]
        }
    }
}

class JumpTable private constructor(
    private val table: MutableMap<Int, Block>
) : MutableMap<Int, Block> by table {
    constructor() : this(mutableMapOf())

    companion object {
        const val FALLTHROUGH = 0
        const val RETURN = 1
        const val THROW = 2
    }
}

data class MethodDescriptor(
    val name: String?, // null indicates a computed name
    val isStatic: Boolean,
    val kind: Operations.FunctionKind,
    val isGetter: Boolean,
    val isSetter: Boolean,
    val methodInfo: ConstantIndex,
)

data class ClassDescriptor(val methodDescriptors: List<ConstantIndex>)
