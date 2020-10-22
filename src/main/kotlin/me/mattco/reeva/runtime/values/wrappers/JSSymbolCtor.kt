package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.functions.JSNativeFunction
import me.mattco.reeva.runtime.values.objects.Attributes
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.primitives.JSSymbol
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.toValue

class JSSymbolCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Symbol") {
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

    @JSMethod("for", 1)
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

    @JSMethod("keyFor", 1)
    fun keyFor(thisValue: JSValue, arguments: JSArguments): JSValue {
        val sym = arguments.argument(0)
        if (!sym.isSymbol)
            shouldThrowError("TypeError")
        for ((globalKey, globalSymbol) in Realm.globalSymbolRegistry) {
            if (sym == globalSymbol)
                return globalKey.toValue()
        }
        return JSUndefined
    }

    override fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        val description = Operations.toString(arguments.getOrElse(0) { JSUndefined }).string
        return JSSymbol(description)
    }

    override fun construct(arguments: List<JSValue>, newTarget: JSValue): JSValue {
        shouldThrowError("TypeError")
    }

    companion object {
        fun create(realm: Realm) = JSSymbolCtor(realm).also { it.init() }
    }
}
