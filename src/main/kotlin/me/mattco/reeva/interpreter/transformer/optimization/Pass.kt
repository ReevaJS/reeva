package me.mattco.reeva.interpreter.transformer.optimization

import me.mattco.reeva.interpreter.transformer.FunctionInfo
import me.mattco.reeva.interpreter.transformer.opcodes.IrPrinter

interface Pass {
    fun evaluate(info: OptInfo)

    object OptimizationPipeline : Pass {
        override fun evaluate(info: OptInfo) {
            RemoveHandlers.evaluate(info)
            GenerateCFG.evaluate(info)
            MergeBlocks.evaluate(info)
            RemoveHandlers.evaluate(info)
            GenerateCFG.evaluate(info)
            PlaceBlocks.evaluate(info)
            IrPrinter(FunctionInfo("whatever", info.opcodes, false, false, false, null)).print()

            // TODO: This corrupts registers in loop contexts (i.e. it doesn't know how to
            // track register liveness across back-edges)

            // RegisterAllocation().evaluate(info)

            // RegisterAllocation2().evaluate(info)
        }
    }
}