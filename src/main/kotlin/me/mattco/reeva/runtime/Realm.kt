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
import me.mattco.reeva.runtime.values.primitives.JSSymbol
import me.mattco.reeva.runtime.values.wrappers.JSStringCtor
import me.mattco.reeva.runtime.values.wrappers.JSStringProto
import me.mattco.reeva.runtime.values.wrappers.JSSymbolCtor
import me.mattco.reeva.runtime.values.wrappers.JSSymbolProto
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class Realm {
    lateinit var globalObject: JSObject
    @JvmField
    var globalEnv: GlobalEnvRecord? = null

    lateinit var objectProto: JSObjectProto private set
    lateinit var stringProto: JSStringProto private set
    lateinit var symbolProto: JSSymbolProto private set
    lateinit var functionProto: JSFunctionProto private set
    lateinit var arrayProto: JSArrayProto private set
    lateinit var consoleProto: JSConsoleProto private set

    lateinit var objectCtor: JSObjectCtor private set
    lateinit var stringCtor: JSStringCtor private set
    lateinit var symbolCtor: JSSymbolCtor private set
    lateinit var functionCtor: JSFunctionCtor private set
    lateinit var arrayCtor: JSArrayCtor private set

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

    fun initObjects() {
        objectProto = JSObjectProto.create(this)
        functionProto = JSFunctionProto.create(this)
        objectProto.init()
        functionProto.init()

        stringProto = JSStringProto.create(this)
        symbolProto = JSSymbolProto.create(this)
        arrayProto = JSArrayProto.create(this)
        consoleProto = JSConsoleProto.create(this)

        stringCtor = JSStringCtor.create(this)
        functionCtor = JSFunctionCtor.create(this)
        objectCtor = JSObjectCtor.create(this)
        arrayCtor = JSArrayCtor.create(this)

        consoleObj = JSConsole.create(this)

        `@@asyncIterator` = JSSymbol("Symbol.asyncIterator")
        `@@hasInstance` = JSSymbol("Symbol.hasInstance")
        `@@isConcatSpreadable` = JSSymbol("Symbol.isConcatSpreadable")
        `@@iterator` = JSSymbol("Symbol.iterator")
        `@@match` = JSSymbol("Symbol.match")
        `@@matchAll` = JSSymbol("Symbol.matchAll")
        `@@replace` = JSSymbol("Symbol.replace")
        `@@search` = JSSymbol("Symbol.search")
        `@@species` = JSSymbol("Symbol.species")
        `@@split` = JSSymbol("Symbol.split")
        `@@toPrimitive` = JSSymbol("Symbol.toPrimitive")
        `@@toStringTag` = JSSymbol("Symbol.toStringTag")
        `@@unscopables` = JSSymbol("Symbol.unscopables")

        // Must be created after wellknown symbols
        symbolCtor = JSSymbolCtor.create(this)
    }

    fun populateGlobalObject() {
        globalObject.set("Object", objectCtor)
        globalObject.set("Function", functionCtor)
        globalObject.set("Array", arrayCtor)
        globalObject.set("String", stringCtor)
        globalObject.set("Symbol", symbolCtor)

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
