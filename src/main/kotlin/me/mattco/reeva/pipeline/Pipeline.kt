package me.mattco.reeva.pipeline

import me.mattco.reeva.ast.ASTNode
import me.mattco.reeva.compiler.graph.Graph
import me.mattco.reeva.core.Agent
import me.mattco.reeva.ir.FunctionInfo
import me.mattco.reeva.pipeline.stages.*
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.utils.Result

object Pipeline {
    private val irStage: Stage<ASTNode, FunctionInfo, PipelineError>

    private val scriptInterpretStage: Stage<String, JSValue, PipelineError>
    private val moduleInterpretStage: Stage<String, JSValue, PipelineError>

    private val scriptGraphStage: Stage<String, Graph, PipelineError>


    init {
        irStage = IRStage.chain(PeepholeStage)

        scriptInterpretStage = ScriptParserStage.chain(irStage).chain(InterpreterStage)
        moduleInterpretStage = ModuleParserStage.chain(irStage).chain(InterpreterStage)
        scriptGraphStage = ScriptParserStage.chain(irStage).chain(GraphStage)
    }

    fun interpret(agent: Agent, script: String, asModule: Boolean): Result<PipelineError, JSValue> {
        return if (asModule) {
            moduleInterpretStage.process(agent, script)
        } else scriptInterpretStage.process(agent, script)
    }

    fun graph(agent: Agent, script: String): Result<PipelineError, Graph> {
        return scriptGraphStage.process(agent, script)
    }
}
