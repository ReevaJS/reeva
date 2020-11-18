package me.mattco.reeva.core

import me.mattco.reeva.ast.ScriptNode
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.core.modules.resolver.ModuleResolver
import me.mattco.reeva.runtime.arrays.JSArrayCtor
import me.mattco.reeva.runtime.arrays.JSArrayProto
import me.mattco.reeva.runtime.builtins.*
import me.mattco.reeva.runtime.builtins.promises.JSPromiseCtor
import me.mattco.reeva.runtime.builtins.promises.JSPromiseProto
import me.mattco.reeva.runtime.errors.*
import me.mattco.reeva.runtime.functions.JSFunctionCtor
import me.mattco.reeva.runtime.functions.JSFunctionProto
import me.mattco.reeva.runtime.global.JSConsole
import me.mattco.reeva.runtime.global.JSConsoleProto
import me.mattco.reeva.runtime.iterators.*
import me.mattco.reeva.jvmcompat.JSClassProto
import me.mattco.reeva.jvmcompat.JSPackageObject
import me.mattco.reeva.jvmcompat.JSPackageProto
import me.mattco.reeva.runtime.objects.*
import me.mattco.reeva.runtime.objects.JSObject.Companion.initialize
import me.mattco.reeva.runtime.primitives.JSSymbol
import me.mattco.reeva.runtime.wrappers.*
import java.util.concurrent.ConcurrentHashMap

@Suppress("ObjectPropertyName")
class Realm(var moduleResolver: ModuleResolver? = null) {
    internal val isGloballyInitialized: Boolean
        get() = ::globalObject.isInitialized && ::globalEnv.isInitialized

    lateinit var globalObject: JSObject
        private set
    lateinit var globalEnv: GlobalEnvRecord
        private set

    lateinit var objectProto: JSObjectProto private set
    lateinit var numberProto: JSNumberProto private set
    lateinit var booleanProto: JSBooleanProto private set
    lateinit var stringProto: JSStringProto private set
    lateinit var symbolProto: JSSymbolProto private set
    lateinit var functionProto: JSFunctionProto private set
    lateinit var arrayProto: JSArrayProto private set
    lateinit var setProto: JSSetProto private set
    lateinit var mapProto: JSMapProto private set
    lateinit var promiseProto: JSPromiseProto private set
    lateinit var dateProto: JSDateProto private set
    lateinit var iteratorProto: JSIteratorProto private set
    lateinit var arrayIteratorProto: JSArrayIteratorProto private set
    lateinit var setIteratorProto: JSSetIteratorProto private set
    lateinit var mapIteratorProto: JSMapIteratorProto private set
    lateinit var objectPropertyIteratorProto: JSObjectPropertyIteratorProto private set
    lateinit var consoleProto: JSConsoleProto private set

    lateinit var errorProto: JSErrorProto private set
    lateinit var evalErrorProto: JSEvalErrorProto private set
    lateinit var typeErrorProto: JSTypeErrorProto private set
    lateinit var rangeErrorProto: JSRangeErrorProto private set
    lateinit var referenceErrorProto: JSReferenceErrorProto private set
    lateinit var syntaxErrorProto: JSSyntaxErrorProto private set
    lateinit var uriErrorProto: JSURIErrorProto private set

    lateinit var objectCtor: JSObjectCtor private set
    lateinit var numberCtor: JSNumberCtor private set
    lateinit var booleanCtor: JSBooleanCtor private set
    lateinit var stringCtor: JSStringCtor private set
    lateinit var symbolCtor: JSSymbolCtor private set
    lateinit var functionCtor: JSFunctionCtor private set
    lateinit var arrayCtor: JSArrayCtor private set
    lateinit var setCtor: JSSetCtor private set
    lateinit var mapCtor: JSMapCtor private set
    lateinit var proxyCtor: JSProxyCtor private set
    lateinit var promiseCtor: JSPromiseCtor private set
    lateinit var dateCtor: JSDateCtor private set

    lateinit var errorCtor: JSErrorCtor private set
    lateinit var evalErrorCtor: JSEvalErrorCtor private set
    lateinit var typeErrorCtor: JSTypeErrorCtor private set
    lateinit var rangeErrorCtor: JSRangeErrorCtor private set
    lateinit var referenceErrorCtor: JSReferenceErrorCtor private set
    lateinit var syntaxErrorCtor: JSSyntaxErrorCtor private set
    lateinit var uriErrorCtor: JSURIErrorCtor private set

    lateinit var mathObj: JSMathObject private set
    lateinit var reflectObj: JSReflectObject private set
    lateinit var jsonObj: JSONObject private set
    lateinit var consoleObj: JSConsole private set

    lateinit var packageProto: JSPackageProto private set
    lateinit var classProto: JSClassProto private set
    lateinit var packageObj: JSPackageObject private set

    val emptyShape = Shape(this)
    val newObjectShape = Shape(this)

    fun setGlobalObject(obj: JSObject) {
        globalObject = obj
        globalEnv = GlobalEnvRecord.create(globalObject)
    }

    fun initObjects() {
        objectProto = JSObjectProto.create(this)
        functionProto = JSFunctionProto.create(this)
        objectCtor = JSObjectCtor.create(this)
        functionProto.initialize()
        objectProto.initialize()
        objectCtor.initialize()

        numberCtor = JSNumberCtor.create(this)
        booleanCtor = JSBooleanCtor.create(this)
        stringCtor = JSStringCtor.create(this)
        functionCtor = JSFunctionCtor.create(this)
        arrayCtor = JSArrayCtor.create(this)
        setCtor = JSSetCtor.create(this)
        mapCtor = JSMapCtor.create(this)
        proxyCtor = JSProxyCtor.create(this)
        promiseCtor = JSPromiseCtor.create(this)
        dateCtor = JSDateCtor.create(this)

        errorCtor = JSErrorCtor.create(this)
        evalErrorCtor = JSEvalErrorCtor.create(this)
        typeErrorCtor = JSTypeErrorCtor.create(this)
        rangeErrorCtor = JSRangeErrorCtor.create(this)
        referenceErrorCtor = JSReferenceErrorCtor.create(this)
        syntaxErrorCtor = JSSyntaxErrorCtor.create(this)
        uriErrorCtor = JSURIErrorCtor.create(this)

        numberProto = JSNumberProto.create(this)
        booleanProto = JSBooleanProto.create(this)
        stringProto = JSStringProto.create(this)
        arrayProto = JSArrayProto.create(this)
        setProto = JSSetProto.create(this)
        mapProto = JSMapProto.create(this)
        promiseProto = JSPromiseProto.create(this)
        dateProto = JSDateProto.create(this)
        iteratorProto = JSIteratorProto.create(this)
        arrayIteratorProto = JSArrayIteratorProto.create(this)
        setIteratorProto = JSSetIteratorProto.create(this)
        mapIteratorProto = JSMapIteratorProto.create(this)
        objectPropertyIteratorProto = JSObjectPropertyIteratorProto.create(this)
        consoleProto = JSConsoleProto.create(this)
        mathObj = JSMathObject.create(this)
        reflectObj = JSReflectObject.create(this)
        jsonObj = JSONObject.create(this)
        consoleObj = JSConsole.create(this)

        errorProto = JSErrorProto.create(this)
        evalErrorProto = JSEvalErrorProto.create(this)
        typeErrorProto = JSTypeErrorProto.create(this)
        rangeErrorProto = JSRangeErrorProto.create(this)
        referenceErrorProto = JSReferenceErrorProto.create(this)
        syntaxErrorProto = JSSyntaxErrorProto.create(this)
        uriErrorProto = JSURIErrorProto.create(this)

        packageProto = JSPackageProto.create(this)
        classProto = JSClassProto.create(this)
        packageObj = JSPackageObject.create(this)

        // Must be created after well-known symbols
        symbolCtor = JSSymbolCtor.create(this)
        symbolProto = JSSymbolProto.create(this)

        // These can't be in the init method of the objects due to circularity
        objectCtor.defineOwnProperty("prototype", objectProto, 0)
        functionCtor.defineOwnProperty("prototype", functionProto, 0)
        numberCtor.defineOwnProperty("prototype", numberProto, 0)
        stringCtor.defineOwnProperty("prototype", stringProto, 0)
        booleanCtor.defineOwnProperty("prototype", booleanProto, 0)
        symbolCtor.defineOwnProperty("prototype", symbolProto, 0)
        arrayCtor.defineOwnProperty("prototype", arrayProto, 0)
        setCtor.defineOwnProperty("prototype", setProto, 0)
        mapCtor.defineOwnProperty("prototype", mapProto, 0)
        dateCtor.defineOwnProperty("prototype", dateProto, 0)
        errorCtor.defineOwnProperty("prototype", errorProto, 0)
        evalErrorCtor.defineOwnProperty("prototype", evalErrorProto, 0)
        typeErrorCtor.defineOwnProperty("prototype", typeErrorProto, 0)
        rangeErrorCtor.defineOwnProperty("prototype", rangeErrorProto, 0)
        referenceErrorCtor.defineOwnProperty("prototype", referenceErrorProto, 0)
        syntaxErrorCtor.defineOwnProperty("prototype", syntaxErrorProto, 0)
        uriErrorCtor.defineOwnProperty("prototype", uriErrorProto, 0)
        functionProto.defineOwnProperty("constructor", functionCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)

        newObjectShape.setPrototypeWithoutTransition(objectProto)
    }

    data class ScriptRecord(
        val realm: Realm,
        var env: EnvRecord?,
        val scriptOrModule: ScriptNode,
        val errors: List<Parser.SyntaxError> = emptyList()
    )

    internal companion object {
        val EMPTY_REALM = Realm()

        val globalSymbolRegistry = ConcurrentHashMap<String, JSSymbol>()

        // To get access to the symbol via their name without reflection
        val wellknownSymbols = mutableMapOf<String, JSSymbol>()

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

        fun setupSymbols() {
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
        }
    }
}
