package me.mattco.reeva.pipeline.stages

import me.mattco.reeva.compiler.graph.Graph
import me.mattco.reeva.compiler.graph.GraphBuilder
import me.mattco.reeva.core.Agent
import me.mattco.reeva.ir.FunctionInfo
import me.mattco.reeva.pipeline.PipelineError
import me.mattco.reeva.pipeline.Stage
import me.mattco.reeva.utils.Result

object GraphStage : Stage<FunctionInfo, Graph, PipelineError> {
    override fun process(agent: Agent, input: FunctionInfo): Result<PipelineError, Graph> {
        return try {
            Result.success(GraphBuilder(input).build())
        } catch (e: Throwable) {
            Result.error(PipelineError.Internal(e))
        }
    }
}
