package me.mattco.reeva.pipeline.stages

import me.mattco.reeva.ast.ModuleNode
import me.mattco.reeva.core.Agent
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.pipeline.PipelineError
import me.mattco.reeva.pipeline.Stage
import me.mattco.reeva.utils.Result

object ModuleParserStage : Stage<String, ModuleNode, PipelineError> {
    override fun process(agent: Agent, input: String): Result<PipelineError, ModuleNode> = try {
        val ast = Parser(input).parseModule()
        if (agent.printAST) {
            ast.debugPrint()
            println("\n")
        }
        Result.success(ast)
    } catch (e: Parser.ParsingException) {
        Result.error(PipelineError.Parse(e.message!!, e.start, e.end))
    } catch (e: Throwable) {
        Result.error(PipelineError.Internal(e))
    }
}
