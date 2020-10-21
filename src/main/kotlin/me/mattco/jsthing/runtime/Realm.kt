package me.mattco.jsthing.runtime

import me.mattco.jsthing.ast.ScriptNode
import me.mattco.jsthing.parser.Parser
import me.mattco.jsthing.runtime.contexts.ExecutionContext
import me.mattco.jsthing.runtime.environment.EnvRecord
import me.mattco.jsthing.runtime.environment.FunctionEnvRecord
import me.mattco.jsthing.runtime.environment.GlobalEnvRecord
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.arrays.JSArrayProto
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSFunction
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSFunctionProto
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObjectProto
import me.mattco.jsthing.runtime.values.primitives.JSUndefined
import me.mattco.jsthing.utils.ecmaAssert

class Realm {
    lateinit var globalObject: JSObject
    var globalEnv: GlobalEnvRecord? = null

    lateinit var objectProto: JSObjectProto
        private set
    lateinit var functionProto: JSFunctionProto
        private set
    lateinit var arrayProto: JSArrayProto
        private set

    fun init(objProto: JSObjectProto, funcProto: JSFunctionProto) {
        if (!::globalObject.isInitialized)
            throw Error("Cannot initialize Realm without globalObject")
        objectProto = objProto
        functionProto = funcProto
        arrayProto = JSArrayProto.create(this)
    }

    fun parseScript(script: String): ScriptRecord {
        return ScriptRecord(this, null, Parser(script).parseScript())
    }

    data class ScriptRecord(
        val realm: Realm,
        var env: EnvRecord?,
        val scriptOrModule: ScriptNode,
        val errors: List<Nothing> = emptyList() // TODO
    )
}
