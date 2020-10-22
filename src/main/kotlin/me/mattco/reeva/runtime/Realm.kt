package me.mattco.reeva.runtime

import me.mattco.reeva.ast.ScriptNode
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.environment.EnvRecord
import me.mattco.reeva.runtime.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.values.arrays.JSArrayCtor
import me.mattco.reeva.runtime.values.arrays.JSArrayProto
import me.mattco.reeva.runtime.values.functions.JSFunctionCtor
import me.mattco.reeva.runtime.values.functions.JSFunctionProto
import me.mattco.reeva.runtime.values.global.JSConsole
import me.mattco.reeva.runtime.values.global.JSConsoleProto
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.objects.JSObjectCtor
import me.mattco.reeva.runtime.values.objects.JSObjectProto
import me.mattco.reeva.runtime.values.wrappers.JSStringCtor
import me.mattco.reeva.runtime.values.wrappers.JSStringProto

class Realm {
    lateinit var globalObject: JSObject
    @JvmField
    var globalEnv: GlobalEnvRecord? = null

    lateinit var objectProto: JSObjectProto private set
    lateinit var stringProto: JSStringProto private set
    lateinit var functionProto: JSFunctionProto private set
    lateinit var arrayProto: JSArrayProto private set
    lateinit var consoleProto: JSConsoleProto private set

    lateinit var objectCtor: JSObjectCtor private set
    lateinit var stringCtor: JSStringCtor private set
    lateinit var functionCtor: JSFunctionCtor private set
    lateinit var arrayCtor: JSArrayCtor private set

    lateinit var consoleObj: JSConsole private set

    fun initObjects() {
        objectProto = JSObjectProto.create(this)
        functionProto = JSFunctionProto.create(this)
        objectProto.init()
        functionProto.init()

        stringProto = JSStringProto.create(this)
        arrayProto = JSArrayProto.create(this)
        consoleProto = JSConsoleProto.create(this)

        stringCtor = JSStringCtor.create(this)
        functionCtor = JSFunctionCtor.create(this)
        objectCtor = JSObjectCtor.create(this)
        arrayCtor = JSArrayCtor.create(this)

        consoleObj = JSConsole.create(this)
    }

    fun populateGlobalObject() {
        globalObject.set("Object", objectCtor)
        globalObject.set("Function", functionCtor)
        globalObject.set("Array", arrayCtor)
        globalObject.set("String", stringCtor)

        globalObject.set("console", consoleObj)
    }

    fun parseScript(script: String): ScriptRecord {
        val parser = Parser(script)
        val scriptNode = parser.parseScript()
        val errors = parser.syntaxErrors
        return ScriptRecord(this, null, scriptNode, errors)
    }

    data class ScriptRecord(
        val realm: Realm,
        var env: EnvRecord?,
        val scriptOrModule: ScriptNode,
        val errors: List<Parser.SyntaxError> = emptyList() // TODO
    )
}
