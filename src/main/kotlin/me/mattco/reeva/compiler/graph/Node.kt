package me.mattco.reeva.compiler.graph

import me.mattco.reeva.utils.expect

class Node(val descriptor: Descriptor, vararg inputNodes: Node) {
    val inputs = mutableListOf<Node>()
    val uses = mutableListOf<Use>()

    init {
        inputNodes.forEach(::addInput)
    }

    fun addInput(node: Node) {
        inputs.add(node)
        node.addUse(inputs.lastIndex, this)
    }

    fun addInput(index: Int, node: Node) {
        inputs.drop(index).forEach { input ->
            input.getUse(this).index++
        }
        inputs.add(index, node)
        node.addUse(index, this)
    }

    fun removeInput(node: Node) {
        removeInput(inputs.indexOfFirst { it == node })
    }

    fun removeInput(index: Int) {
        expect(index >= 0)
        val removedNode = inputs.removeAt(index)
        removedNode.removeUse(this)

        inputs.drop(index).forEach { input ->
            input.getUse(this).index--
        }
    }

    fun replaceInput(index: Int, node: Node) {
        inputs[index].removeUse(this)
        inputs[index] = node
        node.addUse(index, this)
    }

    fun addUse(index: Int, node: Node) {
        expect(uses.none { it.node == node })
        uses.add(Use(index, node))
    }

    fun removeUse(node: Node) {
        uses.removeIf { it.node == node }
    }

    fun getUse(node: Node) = uses.first { it.node == node }

    data class Counts(val inputs: Int, val outputs: Int)

    data class Use(var index: Int, var node: Node) {
        fun isValue() = index < node.descriptor.valuesIn

        fun isEffect() = node.descriptor.let {
            index in it.valuesIn..(it.valuesIn + it.effectsIn)
        }

        fun isControl() = node.descriptor.let {
            val s = it.valuesIn + it.effectsIn
            index in s..(s + it.controlsIn)
        }
    }

    open class Descriptor(
        val type: NodeType,
        val valuesIn: Int,
        val effectsIn: Int,
        val controlsIn: Int,
        val valuesOut: Int,
        val effectsOut: Int,
        val controlsOut: Int
    )

    class DescriptorWithConst<T>(
        type: NodeType,
        valuesIn: Int,
        effectsIn: Int,
        controlsIn: Int,
        valuesOut: Int,
        effectsOut: Int,
        controlsOut: Int,
        val param: T,
    ) : Descriptor(type, valuesIn, effectsIn, controlsIn, valuesOut, effectsOut, controlsOut)
}
