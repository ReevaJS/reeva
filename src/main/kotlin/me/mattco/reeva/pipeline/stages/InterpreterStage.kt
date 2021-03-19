package me.mattco.reeva.pipeline.stages

import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.interpreter.IRInterpreter
import me.mattco.reeva.ir.FunctionInfo
import me.mattco.reeva.pipeline.PipelineError
import me.mattco.reeva.pipeline.Stage
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.utils.Result

object InterpreterStage : Stage<FunctionInfo, JSValue, PipelineError> {
    override fun process(agent: Agent, input: FunctionInfo): Result<PipelineError, JSValue> {
        val function = IRInterpreter.wrap(input, agent.activeRealm)

        return try {
            Result.success(function.call(JSArguments(emptyList(), agent.activeRealm.globalObject)))
        } catch (e: ThrowException) {
            Result.error(PipelineError.Runtime(agent.activeRealm, e.value))
        }
    }
}
