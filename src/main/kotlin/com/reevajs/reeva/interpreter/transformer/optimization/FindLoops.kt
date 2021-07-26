package com.reevajs.reeva.interpreter.transformer.optimization

import com.reevajs.reeva.interpreter.transformer.Block
import com.reevajs.reeva.interpreter.transformer.FunctionOpcodes

class FindLoops : Pass {
    private lateinit var domMap: Map<Block, Set<Block>>
    private lateinit var opcodes: FunctionOpcodes
    private lateinit var backEdges: MutableMap<Block, MutableSet<Block>>

    override fun evaluate(opcodes: FunctionOpcodes) {
        if (opcodes.blocks.size <= 1)
            return

        this.opcodes = opcodes
        backEdges = opcodes.analysis.backEdges
        domMap = DominatorTree(opcodes).calculate()
        findBackEdges(opcodes.analysis.entryBlock, mutableSetOf())
    }

    private fun findBackEdges(block: Block, seenBlocks: MutableSet<Block>) {
        seenBlocks.add(block)
        val successors = opcodes.analysis.forwardCFG[block] ?: emptyList()
        for (successor in successors) {
            if (domMap[successor]?.contains(block) == true)
                backEdges.getOrPut(block, ::mutableSetOf).add(successor)

            if (successor !in seenBlocks)
                findBackEdges(successor, seenBlocks)
        }
    }

    // https://www.cl.cam.ac.uk/~mr10/lengtarj.pdf
    class DominatorTree(private val opcodes: FunctionOpcodes) {
        private val entryBlock = opcodes.analysis.entryBlock
        private val blockList = mutableListOf(entryBlock)
        private val indices = mutableMapOf<Block, Int>()

        private val parents = mutableMapOf<Block, Block>()
        private val successors = mutableMapOf<Block, Set<Block>>()
        private val predecessors = mutableMapOf<Block, Set<Block>>()
        private val semi = mutableMapOf<Block, Block>()
        private val idom = mutableMapOf<Block, Block>()
        private val ancestor = mutableMapOf<Block, Block>()
        private val best = mutableMapOf<Block, Block>()
        private val bucket = mutableMapOf<Block, MutableList<Block>>()

        private val forwardCFG: Map<Block, Set<Block>>
            get() = opcodes.analysis.forwardCFG

        private val inverseCFG: Map<Block, Set<Block>>
            get() = opcodes.analysis.invertedCFG

        fun calculate(): Map<Block, Set<Block>> {
            dfs { parent, current ->
                parents[current] = parent
                blockList.add(current)
            }

            blockList.forEachIndexed { index, block -> indices[block] = index }

            for (block in blockList) {
                successors[block] = forwardCFG[block] ?: emptySet()
                predecessors[block] = inverseCFG[block] ?: emptySet()
                semi[block] = block
                idom[block] = entryBlock
                ancestor[block] = entryBlock
                best[block] = block
                bucket[block] = mutableListOf()
            }

            for (w in blockList.drop(1).asReversed()) {
                val p = parents[w]!!

                for (v in predecessors[w]!!) {
                    val u = eval(v)
                    if (semi[w].index() > semi[u].index())
                        semi[w] = semi[u]!!
                }

                bucket[semi[w]]!!.add(w)
                link(p, w)

                for (v in bucket[p]!!) {
                    val u = eval(v)
                    idom[v] = if (semi[u].index() < semi[v].index()) u else p
                }

                bucket[p] = mutableListOf()
            }

            for (w in blockList.drop(1)) {
                if (idom[w] != semi[w])
                    idom[w] = idom[idom[w]]!!
            }

            val flipped = mutableMapOf<Block, MutableSet<Block>>()
            for ((dominated, dominator) in idom.entries) {
                if (dominated != dominator)
                    flipped.getOrPut(dominator, ::mutableSetOf).add(dominated)
            }

            val dom = mutableMapOf<Block, Set<Block>>()

            fun getAndSetDominators(dominator: Block, dominatedSet: Set<Block>): Set<Block> {
                val mutableSet = dominatedSet.toMutableSet()
                for (dominated in dominatedSet) {
                    val subDominated = flipped[dominated]
                    if (subDominated != null)
                        mutableSet += getAndSetDominators(dominated, subDominated)
                }
                dom[dominator] = mutableSet
                return mutableSet
            }

            getAndSetDominators(entryBlock, flipped[entryBlock]!!)

            return dom
        }

        private fun link(v: Block, w: Block) {
            ancestor[w] = v
        }

        private fun eval(v: Block): Block {
            if (ancestor[v] == entryBlock)
                return v
            compress(v)
            return best[v]!!
        }

        private fun compress(v: Block) {
            val a = ancestor[v]!!
            if (a == entryBlock)
                return

            compress(a)

            if (semi[best[v]]!!.index() > semi[best[a]]!!.index())
                best[v] = best[a]!!

            ancestor[v] = ancestor[a]!!
        }

        private fun dfs(action: (parent: Block, current: Block) -> Unit) {
            dfsHelper(opcodes.analysis.entryBlock, mutableSetOf(), action)
        }

        private fun dfsHelper(block: Block, seen: MutableSet<Block>, action: (parent: Block, current: Block) -> Unit) {
            val toSet = forwardCFG[block] ?: return
            for (to in toSet) {
                if (to in seen)
                    continue
                seen.add(to)
                action(block, to)
                dfsHelper(to, seen, action)
            }
        }

        private fun Block?.index() = this@DominatorTree.indices[this!!]!!
    }
}
