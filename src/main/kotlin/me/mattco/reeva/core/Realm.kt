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

    // Special objects that have to be handled manually
    lateinit var objectProto: JSObjectProto private set
    lateinit var functionProto: JSFunctionProto private set
    lateinit var symbolProto: JSSymbolProto private set
    lateinit var symbolCtor: JSSymbolCtor private set

    val numberProto by lazy { JSNumberProto.create(this) }
    val booleanProto by lazy { JSBooleanProto.create(this) }
    val stringProto by lazy { JSStringProto.create(this) }
    val arrayProto by lazy { JSArrayProto.create(this) }
    val setProto by lazy { JSSetProto.create(this) }
    val mapProto by lazy { JSMapProto.create(this) }
    val promiseProto by lazy { JSPromiseProto.create(this) }
    val dateProto by lazy { JSDateProto.create(this) }
    val iteratorProto by lazy { JSIteratorProto.create(this) }
    val arrayIteratorProto by lazy { JSArrayIteratorProto.create(this) }
    val setIteratorProto by lazy { JSSetIteratorProto.create(this) }
    val mapIteratorProto by lazy { JSMapIteratorProto.create(this) }
    val objectPropertyIteratorProto by lazy { JSObjectPropertyIteratorProto.create(this) }
    val consoleProto by lazy { JSConsoleProto.create(this) }

    val errorProto by lazy { JSErrorProto.create(this) }
    val evalErrorProto by lazy { JSEvalErrorProto.create(this) }
    val typeErrorProto by lazy { JSTypeErrorProto.create(this) }
    val rangeErrorProto by lazy { JSRangeErrorProto.create(this) }
    val referenceErrorProto by lazy { JSReferenceErrorProto.create(this) }
    val syntaxErrorProto by lazy { JSSyntaxErrorProto.create(this) }
    val uriErrorProto by lazy { JSURIErrorProto.create(this) }

    val objectCtor by lazy { JSObjectCtor.create(this) }
    val numberCtor by lazy { JSNumberCtor.create(this) }
    val booleanCtor by lazy { JSBooleanCtor.create(this) }
    val stringCtor by lazy { JSStringCtor.create(this) }
    val functionCtor by lazy { JSFunctionCtor.create(this) }
    val arrayCtor by lazy { JSArrayCtor.create(this) }
    val setCtor by lazy { JSSetCtor.create(this) }
    val mapCtor by lazy { JSMapCtor.create(this) }
    val proxyCtor by lazy { JSProxyCtor.create(this) }
    val promiseCtor by lazy { JSPromiseCtor.create(this) }
    val dateCtor by lazy { JSDateCtor.create(this) }

    val errorCtor by lazy { JSErrorCtor.create(this) }
    val evalErrorCtor by lazy { JSEvalErrorCtor.create(this) }
    val typeErrorCtor by lazy { JSTypeErrorCtor.create(this) }
    val rangeErrorCtor by lazy { JSRangeErrorCtor.create(this) }
    val referenceErrorCtor by lazy { JSReferenceErrorCtor.create(this) }
    val syntaxErrorCtor by lazy { JSSyntaxErrorCtor.create(this) }
    val uriErrorCtor by lazy { JSURIErrorCtor.create(this) }

    val mathObj by lazy { JSMathObject.create(this) }
    val reflectObj by lazy { JSReflectObject.create(this) }
    val jsonObj by lazy { JSONObject.create(this) }
    val consoleObj by lazy { JSConsole.create(this) }

    val packageProto by lazy { JSPackageProto.create(this) }
    val classProto by lazy { JSClassProto.create(this) }
    val packageObj by lazy { JSPackageObject.create(this) }

    val emptyShape = Shape(this)
    val newObjectShape = Shape(this)

    fun setGlobalObject(obj: JSObject) {
        globalObject = obj
        globalEnv = GlobalEnvRecord.create(globalObject)
    }

    fun initObjects() {
        objectProto = JSObjectProto.create(this)
        functionProto = JSFunctionProto.create(this)
        functionProto.initialize()
        objectProto.initialize()

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
