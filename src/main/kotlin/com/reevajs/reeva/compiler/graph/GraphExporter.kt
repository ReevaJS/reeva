package com.reevajs.reeva.compiler.graph

import com.reevajs.kbgv.KBGV
import com.reevajs.kbgv.objects.BGVGraph
import com.reevajs.kbgv.objects.BGVNode
import java.io.File

/**
 * Exports the graph to a  .bgv file
 */
class GraphExporter {
    private val phases = mutableListOf<Pair<Graph, String>>()

    fun register(graph: Graph, phase: String) {
        phases.add(graph to phase)
    }

    fun export(file: File) {

    }

    class GraphPhaseExporter(private val graph: Graph) {
        private val kbgv = KBGV()
        private val nodeMap = mutableMapOf<Node, BGVNode>()

        fun export(): BGVGraph {

        }

        private fun cacheNode(node: Node) {
            kbgv.NodeClassPool(

            )
        }
    }
}
