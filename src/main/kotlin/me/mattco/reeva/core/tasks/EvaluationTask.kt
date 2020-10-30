package me.mattco.reeva.core.tasks

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.Realm
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.JSGlobalObject
import me.mattco.reeva.runtime.JSValue

class EvaluationTask(private val script: String, private val realm: Realm) : Task<Reeva.Result>() {
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
        val scriptNode = parser.parseScript()
        if (parser.syntaxErrors.isNotEmpty())
            TODO()

        val result = Interpreter(realm, scriptNode).interpret()
        return Reeva.Result(result.value, result.isAbrupt)
    }
}
