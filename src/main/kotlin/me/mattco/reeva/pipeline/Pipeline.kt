package me.mattco.reeva.pipeline

import me.mattco.reeva.core.Agent
import me.mattco.reeva.pipeline.stages.IRStage
import me.mattco.reeva.pipeline.stages.InterpreterStage
import me.mattco.reeva.pipeline.stages.ModuleParserStage
import me.mattco.reeva.pipeline.stages.ScriptParserStage
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.utils.Result

object Pipeline {
    private val scriptInterpretStage: Stage<String, JSValue, PipelineError>
    private val moduleInterpretStage: Stage<String, JSValue, PipelineError>

    init {
        scriptInterpretStage = ScriptParserStage.chain(IRStage).chain(InterpreterStage)
        moduleInterpretStage = ModuleParserStage.chain(IRStage).chain(InterpreterStage)

    }

    fun interpret(agent: Agent, script: String, asModule: Boolean): Result<PipelineError, JSValue> {
        return if (asModule) {
            moduleInterpretStage.process(agent, script)
        } else scriptInterpretStage.process(agent, script)
    }
}
