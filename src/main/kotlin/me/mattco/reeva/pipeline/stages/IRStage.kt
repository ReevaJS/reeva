package me.mattco.reeva.pipeline.stages

import me.mattco.reeva.ast.ASTNode
import me.mattco.reeva.core.Agent
import me.mattco.reeva.ir.FunctionInfo
import me.mattco.reeva.ir.IRTransformer
import me.mattco.reeva.ir.opcodes.IrPrinter
import me.mattco.reeva.pipeline.PipelineError
import me.mattco.reeva.pipeline.Stage
import me.mattco.reeva.utils.Result

object IRStage : Stage<ASTNode, FunctionInfo, PipelineError> {
    override fun process(agent: Agent, input: ASTNode): Result<PipelineError, FunctionInfo> = try {
        val ir = IRTransformer().transform(input)
        if (agent.printIR) {
            IrPrinter.printFunctionInfo(ir)
            println("\n")
        }
        Result.success(ir)
    } catch (e: Throwable) {
        Result.error(PipelineError.Internal(e))
    }
}