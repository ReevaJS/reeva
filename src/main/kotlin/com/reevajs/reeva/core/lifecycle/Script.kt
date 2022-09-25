package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ExecutionContext
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.interpreter.NormalInterpretedFunction
import com.reevajs.reeva.parsing.ParsedSource
import com.reevajs.reeva.parsing.Parser
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Result
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.expect

class Script(val realm: Realm, private val parsedSource: ParsedSource) : Executable {
    @ECMAImpl("16.1.6")
    override fun execute(): JSValue {
        expect(!parsedSource.sourceInfo.isModule)

        // 1. Let globalEnv be scriptRecord.[[Realm]].[[GlobalEnv]].
        val globalEnv = realm.globalEnv

        // 2. Let scriptContext be a new ECMAScript code execution context.
        // 3. Set the Function of scriptContext to null.
        // 4. Set the Realm of scriptContext to scriptRecord.[[Realm]].
        // 5. Set the ScriptOrModule of scriptContext to scriptRecord.
        // 6. Set the VariableEnvironment of scriptContext to globalEnv.
        // 7. Set the LexicalEnvironment of scriptContext to globalEnv.
        // 8. Set the PrivateEnvironment of scriptContext to null.
        val scriptContext = ExecutionContext(
            realm,
            envRecord = globalEnv,
            executable = this,
        )

        // 9. Suspend the currently running execution context.
        // 10. Push scriptContext onto the execution context stack; scriptContext is now the running execution context.
        val agent = Agent.activeAgent
        agent.pushExecutionContext(scriptContext)

        // 11. Let script be scriptRecord.[[ECMAScriptCode]].
        // 12. Let result be Completion(GlobalDeclarationInstantiation(script, globalEnv)).
        // 13. If result.[[Type]] is normal, then
        //     a. Set result to the result of evaluating script.
        // 14. If result.[[Type]] is normal and result.[[Value]] is empty, then
        //     a. Set result to NormalCompletion(undefined).
        val result = try {
            val transformedSource = Executable.transform(parsedSource)
            Interpreter(transformedSource, listOf(realm.globalObject, JSUndefined)).interpret()
        } finally {
            // 15. Suspend scriptContext and remove it from the execution context stack.
            agent.popExecutionContext()

            // 16. Assert: The execution context stack is not empty.
            ecmaAssert(agent.contextStack().isNotEmpty())

            // 17. Resume the context that is now on the top of the execution context stack as the running execution
            //     context.
        }

        // 18. Return ? result.
        return result
    }

    companion object {
        fun parseScript(realm: Realm, sourceInfo: SourceInfo): Result<ParsingError, Script> {
            return Parser(sourceInfo).parseScript().mapValue { result ->
                Script(realm, result)
            }
        }
    }
}
