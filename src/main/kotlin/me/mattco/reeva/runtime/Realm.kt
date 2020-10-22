package me.mattco.reeva.runtime

import me.mattco.reeva.ast.ScriptNode
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.environment.EnvRecord
import me.mattco.reeva.runtime.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.values.arrays.JSArrayCtor
import me.mattco.reeva.runtime.values.arrays.JSArrayProto
import me.mattco.reeva.runtime.values.exotics.JSONObject
import me.mattco.reeva.runtime.values.functions.JSFunctionCtor
import me.mattco.reeva.runtime.values.functions.JSFunctionProto
import me.mattco.reeva.runtime.values.global.JSConsole
import me.mattco.reeva.runtime.values.global.JSConsoleProto
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.objects.JSObjectCtor
import me.mattco.reeva.runtime.values.objects.JSObjectProto
import me.mattco.reeva.runtime.values.primitives.JSSymbol
import me.mattco.reeva.runtime.values.wrappers.*
import java.util.concurrent.ConcurrentHashMap

class Realm {
    lateinit var globalObject: JSObject
    @JvmField
    var globalEnv: GlobalEnvRecord? = null

    lateinit var objectProto: JSObjectProto private set
    lateinit var numberProto: JSNumberProto private set
    lateinit var booleanProto: JSBooleanProto private set
    lateinit var stringProto: JSStringProto private set
    lateinit var symbolProto: JSSymbolProto private set
    lateinit var functionProto: JSFunctionProto private set
    lateinit var arrayProto: JSArrayProto private set
    lateinit var consoleProto: JSConsoleProto private set

    lateinit var objectCtor: JSObjectCtor private set
    lateinit var numberCtor: JSNumberCtor private set
    lateinit var booleanCtor: JSBooleanCtor private set
    lateinit var stringCtor: JSStringCtor private set
    lateinit var symbolCtor: JSSymbolCtor private set
    lateinit var functionCtor: JSFunctionCtor private set
    lateinit var arrayCtor: JSArrayCtor private set

    lateinit var jsonObj: JSONObject private set
    lateinit var consoleObj: JSConsole private set

    lateinit var `@@asyncIterator`: JSSymbol private set
    lateinit var `@@hasInstance`: JSSymbol private set
    lateinit var `@@isConcatSpreadable`: JSSymbol private set
    lateinit var `@@iterator`: JSSymbol private set
    lateinit var `@@match`: JSSymbol private set
    lateinit var `@@matchAll`: JSSymbol private set
    lateinit var `@@replace`: JSSymbol private set
    lateinit var `@@search`: JSSymbol private set
    lateinit var `@@species`: JSSymbol private set
    lateinit var `@@split`: JSSymbol private set
    lateinit var `@@toPrimitive`: JSSymbol private set
    lateinit var `@@toStringTag`: JSSymbol private set
    lateinit var `@@unscopables`: JSSymbol private set

    // To get access to the symbol via their name without reflection
    val wellknownSymbols = mutableMapOf<String, JSSymbol>()

    fun initObjects() {
        // Objects can declare symbol methods, so we initialize these first, as they are not objects
        // and do not depends on the object ctor or proto
        `@@asyncIterator` = JSSymbol("Symbol.asyncIterator").also { wellknownSymbols["@@asyncIterator"] = it }
        `@@hasInstance` = JSSymbol("Symbol.hasInstance").also { wellknownSymbols["@@hasInstance"] = it }
        `@@isConcatSpreadable` = JSSymbol("Symbol.isConcatSpreadable").also { wellknownSymbols["@@isConcatSpreadable"] = it }
        `@@iterator` = JSSymbol("Symbol.iterator").also { wellknownSymbols["@@iterator"] = it }
        `@@match` = JSSymbol("Symbol.match").also { wellknownSymbols["@@match"] = it }
        `@@matchAll` = JSSymbol("Symbol.matchAll").also { wellknownSymbols["@@matchAll"] = it }
        `@@replace` = JSSymbol("Symbol.replace").also { wellknownSymbols["@@replace"] = it }
        `@@search` = JSSymbol("Symbol.search").also { wellknownSymbols["@@search"] = it }
        `@@species` = JSSymbol("Symbol.species").also { wellknownSymbols["@@species"] = it }
        `@@split` = JSSymbol("Symbol.split").also { wellknownSymbols["@@split"] = it }
        `@@toPrimitive` = JSSymbol("Symbol.toPrimitive").also { wellknownSymbols["@@toPrimitive"] = it }
        `@@toStringTag` = JSSymbol("Symbol.toStringTag").also { wellknownSymbols["@@toStringTag"] = it }
        `@@unscopables` = JSSymbol("Symbol.unscopables").also { wellknownSymbols["@@unscopables"] = it }

        objectProto = JSObjectProto.create(this)
        functionProto = JSFunctionProto.create(this)
        objectCtor = JSObjectCtor.create(this)
        objectProto.init()
        objectProto.init()
        functionProto.init()

        numberCtor = JSNumberCtor.create(this)
        booleanCtor = JSBooleanCtor.create(this)
        stringCtor = JSStringCtor.create(this)
        functionCtor = JSFunctionCtor.create(this)
        arrayCtor = JSArrayCtor.create(this)

        numberProto = JSNumberProto.create(this)
        booleanProto = JSBooleanProto.create(this)
        stringProto = JSStringProto.create(this)
        arrayProto = JSArrayProto.create(this)
        consoleProto = JSConsoleProto.create(this)
        jsonObj = JSONObject.create(this)
        consoleObj = JSConsole.create(this)

        // Must be created after wellknown symbols
        symbolCtor = JSSymbolCtor.create(this)
        symbolProto = JSSymbolProto.create(this)
    }

    fun populateGlobalObject() {
        globalObject.set("Object", objectCtor)
        globalObject.set("Function", functionCtor)
        globalObject.set("Array", arrayCtor)
        globalObject.set("String", stringCtor)
        globalObject.set("Number", numberCtor)
        globalObject.set("Boolean", booleanCtor)
        globalObject.set("Symbol", symbolCtor)

        globalObject.set("JSON", jsonObj)
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

    companion object {
        internal val globalSymbolRegistry = ConcurrentHashMap<String, JSSymbol>()
    }
}
