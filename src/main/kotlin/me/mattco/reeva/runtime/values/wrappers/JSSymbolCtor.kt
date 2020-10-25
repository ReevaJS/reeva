package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Agent
import me.mattco.reeva.runtime.Agent.Companion.checkError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.functions.JSNativeFunction
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.primitives.JSSymbol
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSSymbolCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Symbol", 0) {
    override fun init() {
        super.init()

        defineOwnProperty("asyncIterator", realm.`@@asyncIterator`, 0)
        defineOwnProperty("hasInstance", realm.`@@hasInstance`, 0)
        defineOwnProperty("isConcatSpreadable", realm.`@@isConcatSpreadable`, 0)
        defineOwnProperty("iterator", realm.`@@iterator`, 0)
        defineOwnProperty("match", realm.`@@match`, 0)
        defineOwnProperty("matchAll", realm.`@@matchAll`, 0)
        defineOwnProperty("replace", realm.`@@replace`, 0)
        defineOwnProperty("search", realm.`@@search`, 0)
        defineOwnProperty("species", realm.`@@species`, 0)
        defineOwnProperty("split", realm.`@@split`, 0)
        defineOwnProperty("toPrimitive", realm.`@@toPrimitive`, 0)
        defineOwnProperty("toStringTag", realm.`@@toStringTag`, 0)
        defineOwnProperty("unscopables", realm.`@@unscopables`, 0)
    }

    @JSMethod("for", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun for_(thisValue: JSValue, arguments: JSArguments): JSValue {
        val key = arguments.argument(0).asString
        for ((globalKey, globalSymbol) in Realm.globalSymbolRegistry) {
            if (globalKey == key)
                return globalSymbol
        }
        val newSymbol = JSSymbol(key)
        Realm.globalSymbolRegistry[key] = newSymbol
        return newSymbol
    }

    @JSThrows
    @JSMethod("keyFor", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun keyFor(thisValue: JSValue, arguments: JSArguments): JSValue {
        val sym = arguments.argument(0)
        if (!sym.isSymbol) {
            throwError<JSTypeErrorObject>("Symbol.keyFor expects a symbol for it's first argument")
            return INVALID_VALUE
        }
        for ((globalKey, globalSymbol) in Realm.globalSymbolRegistry) {
            if (sym == globalSymbol)
                return globalKey.toValue()
        }
        return JSUndefined
    }

    @JSThrows
    override fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        val description = Operations.toString(arguments.getOrElse(0) { JSUndefined }).string
        checkError() ?: return INVALID_VALUE
        return JSSymbol(description)
    }

    @JSThrows
    override fun construct(arguments: List<JSValue>, newTarget: JSValue): JSValue {
        throwError<JSTypeErrorObject>("Symbol objects cannot be constructed")
        return INVALID_VALUE
    }

    companion object {
        fun create(realm: Realm) = JSSymbolCtor(realm).also { it.init() }
    }
}
