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
import me.mattco.reeva.runtime.values.objects.Attributes
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.primitives.JSSymbol
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSSymbolCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Symbol", 0) {
    override fun init() {
        super.init()

        defineOwnProperty("asyncIterator", Descriptor(realm.`@@asyncIterator`, Attributes(0)))
        defineOwnProperty("hasInstance", Descriptor(realm.`@@hasInstance`, Attributes(0)))
        defineOwnProperty("isConcatSpreadable", Descriptor(realm.`@@isConcatSpreadable`, Attributes(0)))
        defineOwnProperty("iterator", Descriptor(realm.`@@iterator`, Attributes(0)))
        defineOwnProperty("match", Descriptor(realm.`@@match`, Attributes(0)))
        defineOwnProperty("matchAll", Descriptor(realm.`@@matchAll`, Attributes(0)))
        defineOwnProperty("replace", Descriptor(realm.`@@replace`, Attributes(0)))
        defineOwnProperty("search", Descriptor(realm.`@@search`, Attributes(0)))
        defineOwnProperty("species", Descriptor(realm.`@@species`, Attributes(0)))
        defineOwnProperty("split", Descriptor(realm.`@@split`, Attributes(0)))
        defineOwnProperty("toPrimitive", Descriptor(realm.`@@toPrimitive`, Attributes(0)))
        defineOwnProperty("toStringTag", Descriptor(realm.`@@toStringTag`, Attributes(0)))
        defineOwnProperty("unscopables", Descriptor(realm.`@@unscopables`, Attributes(0)))
    }

    @JSMethod("for", 1, Attributes.CONFIGURABLE and Attributes.WRITABLE)
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
    @JSMethod("keyFor", 1, Attributes.CONFIGURABLE and Attributes.WRITABLE)
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
