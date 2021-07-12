package me.mattco.reeva.interpreter.transformer.optimization

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
        }
    }
}