package com.reevajs.reeva.interpreter.transformer.optimization

import com.reevajs.reeva.interpreter.transformer.FunctionOpcodes

interface Pass {
    fun evaluate(opcodes: FunctionOpcodes)

    object OptimizationPipeline : Pass {
        override fun evaluate(opcodes: FunctionOpcodes) {
            RemoveHandlers.evaluate(opcodes)
            GenerateCFG.evaluate(opcodes)
            MergeBlocks.evaluate(opcodes)
            RemoveHandlers.evaluate(opcodes)
            GenerateCFG.evaluate(opcodes)
            PlaceBlocks.evaluate(opcodes)
            FindLoops().evaluate(opcodes)
            LivenessAnalysis.evaluate(opcodes)

            // TODO: This corrupts registers in loop contexts (i.e. it doesn't know how to
            // track register liveness across back-edges)
            // RegisterAllocation().evaluate(info)
        }
    }
}