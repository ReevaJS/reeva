package me.mattco.reeva.compiler.graph

import java.util.*

typealias RegisterLiveness = BitSet

class Registers(size: Int) {
    private val nodes = Array<Node?>(size) { null }
    var accumulator: Node? = null
    private val livenessMap = mutableMapOf<Node, RegisterLiveness>()

    fun liveness() = BitSet(nodes.size).also { bs ->
        nodes.forEachIndexed { index, node ->
            bs[index] = node != null
        }
    }

    fun isLive(index: Int) = nodes[index] != null

    fun kill(index: Int) {
        nodes[index] = null
    }

    operator fun get(index: Int) = nodes[index]!!

    operator fun set(index: Int, node: Node) {
        nodes[index] = node
    }

    fun isAccumulatorLive() = accumulator != null

    fun killAccumulator() {
        accumulator = null
    }
}
