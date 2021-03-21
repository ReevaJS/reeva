package me.mattco.reeva.pipeline.stages

import me.mattco.reeva.core.Agent
import me.mattco.reeva.ir.FunctionInfo
import me.mattco.reeva.ir.PeepholeOptimizer
import me.mattco.reeva.pipeline.PipelineError
import me.mattco.reeva.pipeline.Stage
import me.mattco.reeva.utils.Result

object PeepholeStage : Stage<FunctionInfo, FunctionInfo, PipelineError> {
    override fun process(agent: Agent, input: FunctionInfo): Result<PipelineError, FunctionInfo> {
        return try {
            val newInfo = FunctionInfo(
                input.name,
                input.code.also(PeepholeOptimizer::optimize),
                input.constantPool,
                input.handlers,
                input.registerCount,
                input.argCount,
                input.topLevelSlots,
                input.isStrict,
                input.isTopLevelScript
            )

            Result.success(newInfo)
        } catch (e: Throwable) {
            Result.error(PipelineError.Internal(e))
        }
    }
}
