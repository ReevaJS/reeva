package me.mattco.renva.runtime

import me.mattco.renva.ast.ScriptNode
import me.mattco.renva.parser.Parser
import me.mattco.renva.runtime.environment.EnvRecord
import me.mattco.renva.runtime.environment.GlobalEnvRecord
import me.mattco.renva.runtime.values.arrays.JSArrayCtor
import me.mattco.renva.runtime.values.arrays.JSArrayProto
import me.mattco.renva.runtime.values.functions.JSFunctionCtor
import me.mattco.renva.runtime.values.functions.JSFunctionProto
import me.mattco.renva.runtime.values.global.JSConsole
import me.mattco.renva.runtime.values.global.JSConsoleProto
import me.mattco.renva.runtime.values.objects.JSObject
import me.mattco.renva.runtime.values.objects.JSObjectCtor
import me.mattco.renva.runtime.values.objects.JSObjectProto

class Realm {
    lateinit var globalObject: JSObject
    var globalEnv: GlobalEnvRecord? = null

    lateinit var objectProto: JSObjectProto private set
    lateinit var functionProto: JSFunctionProto private set
    lateinit var arrayProto: JSArrayProto private set
    lateinit var consoleProto: JSConsoleProto private set

    lateinit var objectCtor: JSObjectCtor private set
    lateinit var functionCtor: JSFunctionCtor private set
    lateinit var arrayCtor: JSArrayCtor private set

    lateinit var consoleObj: JSConsole private set

    fun initObjects() {
        objectProto = JSObjectProto.create(this)
        functionProto = JSFunctionProto.create(this)
        functionCtor = JSFunctionCtor.create(this)
        objectCtor = JSObjectCtor.create(this)

        arrayProto = JSArrayProto.create(this)
        arrayCtor = JSArrayCtor.create(this)
        consoleProto = JSConsoleProto.create(this)

        consoleObj = JSConsole.create(this)
    }

    fun populateGlobalObject() {
        globalObject.set("Object", objectCtor)
        globalObject.set("Function", functionCtor)
        globalObject.set("Array", arrayCtor)

        globalObject.set("console", consoleObj)
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
