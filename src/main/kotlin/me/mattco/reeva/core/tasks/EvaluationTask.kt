package me.mattco.reeva.core.tasks

import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.ScriptOrModuleNode
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.JSGlobalObject
import me.mattco.reeva.runtime.errors.JSSyntaxErrorObject

class EvaluationTask(
    private val script: String,
    private val realm: Realm,
    private val isModule: Boolean,
) : Task<Reeva.Result>() {
    override fun makeContext(): ExecutionContext {
        val context = ExecutionContext(realm, null)

        if (!realm.isGloballyInitialized) {
            realm.initObjects()
            realm.setGlobalObject(JSGlobalObject.create(realm))
        }

        context.variableEnv = realm.globalEnv
        context.lexicalEnv = realm.globalEnv

        return context
    }

    override fun execute(): Reeva.Result {
        val parser = Parser(script)
        val scriptOrModule = ScriptOrModuleNode(if (isModule) parser.parseModule() else parser.parseScript())
        if (parser.syntaxErrors.isNotEmpty()) {
            val error = parser.syntaxErrors.first()
            return Reeva.Result(
                JSSyntaxErrorObject.create(realm, "(${error.lineNumber}, ${error.columnNumber}) ${error.message}"),
                true
            )
        }

        if (Reeva.PRINT_PARSE_NODES) {
            println("==== top level script ====")
            println(scriptOrModule.dump(1))
        }

        return try {
            if (Reeva.USE_COMPILER) {
                TODO()
            } else {
                Reeva.Result(Interpreter(realm).interpret(scriptOrModule), false)
            }
        } catch (e: ThrowException) {
            Reeva.Result(e.value, true)
        }
    }
}
