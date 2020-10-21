package me.mattco.renva.runtime

import me.mattco.renva.ast.ScriptNode
import me.mattco.renva.parser.Parser
import me.mattco.renva.runtime.environment.EnvRecord
import me.mattco.renva.runtime.environment.GlobalEnvRecord
import me.mattco.renva.runtime.values.arrays.JSArrayProto
import me.mattco.renva.runtime.values.functions.JSFunctionProto
import me.mattco.renva.runtime.values.objects.JSObject
import me.mattco.renva.runtime.values.objects.JSObjectProto

class Realm {
    lateinit var globalObject: JSObject
    var globalEnv: GlobalEnvRecord? = null

    lateinit var objectProto: JSObjectProto
        private set
    lateinit var functionProto: JSFunctionProto
        private set
    lateinit var arrayProto: JSArrayProto
        private set

    fun init() {
        objectProto = JSObjectProto.create(this)
        functionProto = JSFunctionProto(this)
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
