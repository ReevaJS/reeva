package me.mattco.reeva.test262

import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.ScriptOrModuleNode
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.IRConsumer
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.tasks.Task
import me.mattco.reeva.ir.IRTransformer
import me.mattco.reeva.ir.ScopeResolver
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.JSGlobalObject
import me.mattco.reeva.runtime.errors.JSSyntaxErrorObject
import me.mattco.reeva.utils.expect

class Test262EvaluationTask(
    private val script: String,
    private val realm: Realm,
    private val isModule: Boolean,
    private val backend: IRConsumer,
) : Task<Reeva.Result>() {
    var phaseFailed: Negative.Phase? = null

    override fun makeContext(): ExecutionContext {
        realm.ensureGloballyInitialized()
        return ExecutionContext(realm)
    }

    override fun execute(): Reeva.Result {
        val parser = Parser(script)
        val scriptOrModule = ScriptOrModuleNode(if (isModule) parser.parseModule() else parser.parseScript())
        if (parser.syntaxErrors.isNotEmpty()) {
            val error = parser.syntaxErrors.first()
            phaseFailed = Negative.Phase.Parse
            return Reeva.Result(
                JSSyntaxErrorObject.create(realm, "(${error.lineNumber}, ${error.columnNumber}) ${error.message}"),
                true
            )
        }

        if (Reeva.PRINT_PARSE_NODES) {
            println("==== top level script ====")
            println(scriptOrModule.dump(1))
        }

        expect(scriptOrModule.isScript)
        val scriptNode = scriptOrModule.asScript

        return try {
            ScopeResolver().resolve(scriptNode)
            val functionInfo = IRTransformer().transform(scriptNode)
            val task = backend.consume(functionInfo, realm)
            Reeva.getAgent().runTask(task)
        } catch (e: ThrowException) {
            phaseFailed = Negative.Phase.Runtime
            Reeva.Result(e.value, true)
        }
    }
}
