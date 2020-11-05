package me.mattco.reeva.core.tasks

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.DeclarativeEnvRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.JSGlobalObject
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.JSSyntaxErrorObject

class EvaluationTask(private val script: String, private val realm: Realm) : Task<Reeva.Result>() {
    override fun makeContext(): ExecutionContext {
        val context = ExecutionContext(realm, null)

        if (!realm.isGloballyInitialized) {
            realm.initObjects()
            realm.setGlobalObject(JSGlobalObject.create(realm))
        }

        context.variableEnv = realm.globalEnv
        context.lexicalEnv = realm.globalEnv
        context.privateEnv = DeclarativeEnvRecord.create(null)

        return context
    }

    override fun execute(): Reeva.Result {
        val parser = Parser(script)
        val scriptNode = parser.parseScript()
        if (parser.syntaxErrors.isNotEmpty()) {
            val error = parser.syntaxErrors.first()
            return Reeva.Result(
                JSSyntaxErrorObject.create(realm, "(${error.lineNumber}, ${error.columnNumber}) ${error.message}"),
                true
            )
        }

        return try {
            Reeva.Result(Interpreter(realm, scriptNode).interpret(), false)
        } catch (e: ThrowException) {
            Reeva.Result(e.value, true)
        }
    }
}
