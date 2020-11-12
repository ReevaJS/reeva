package me.mattco.reeva.core.tasks

import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.ScriptOrModuleNode
import me.mattco.reeva.compiler.Compiler
import me.mattco.reeva.compiler.TopLevelScript
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.JSGlobalObject
import me.mattco.reeva.runtime.errors.JSSyntaxErrorObject
import java.io.File
import java.io.FileOutputStream

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
        val parser = Parser(script, realm)
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
                val (primary, dependencies) = if (scriptOrModule.isScript) {
                    Compiler().compileScript(scriptOrModule.asScript)
                } else {
                    Compiler().compileModule(scriptOrModule.asModule)
                }
                val ccl = Agent.activeAgent.compilerClassLoader

                ccl.addClass(primary.name, primary.bytes)
                if (Reeva.EMIT_CLASS_FILES) {
                    FileOutputStream(File(Reeva.CLASS_FILE_DIRECTORY, "${primary.name}.class")).use {
                        it.write(primary.bytes)
                    }
                }

                dependencies.forEach { dependency ->
                    ccl.addClass(dependency.name, dependency.bytes)
                    if (Reeva.EMIT_CLASS_FILES) {
                        FileOutputStream(File(Reeva.CLASS_FILE_DIRECTORY, "${dependency.name}.class")).use {
                            it.write(dependency.bytes)
                        }
                    }
                }

                val script = ccl.loadClass(primary.name).newInstance() as TopLevelScript
                Reeva.Result(script.run(), false)
            } else {
                Reeva.Result(Interpreter(realm, scriptOrModule).interpret(), false)
            }
        } catch (e: ThrowException) {
            Reeva.Result(e.value, true)
        }
    }
}
