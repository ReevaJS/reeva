package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.primitives.JSSymbol
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toJSString
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSSymbolCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Symbol", 0) {
    override fun init(realm: Realm) {
        super.init(realm)

        defineOwnProperty("asyncIterator", Realm.WellKnownSymbols.asyncIterator, 0)
        defineOwnProperty("hasInstance", Realm.WellKnownSymbols.hasInstance, 0)
        defineOwnProperty("isConcatSpreadable", Realm.WellKnownSymbols.isConcatSpreadable, 0)
        defineOwnProperty("iterator", Realm.WellKnownSymbols.iterator, 0)
        defineOwnProperty("match", Realm.WellKnownSymbols.match, 0)
        defineOwnProperty("matchAll", Realm.WellKnownSymbols.matchAll, 0)
        defineOwnProperty("replace", Realm.WellKnownSymbols.replace, 0)
        defineOwnProperty("search", Realm.WellKnownSymbols.search, 0)
        defineOwnProperty("species", Realm.WellKnownSymbols.species, 0)
        defineOwnProperty("split", Realm.WellKnownSymbols.split, 0)
        defineOwnProperty("toPrimitive", Realm.WellKnownSymbols.toPrimitive, 0)
        defineOwnProperty("toStringTag", Realm.WellKnownSymbols.toStringTag, 0)
        defineOwnProperty("unscopables", Realm.WellKnownSymbols.unscopables, 0)

        defineBuiltin(realm, "for", 1, ::for_)
        defineBuiltin(realm, "keyFor", 1, ::keyFor)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget != JSUndefined)
            Errors.NotACtor("Symbol").throwTypeError()

        val description = arguments.argument(0).let {
            if (it == JSUndefined) null else it.toJSString().string
        }
        return JSSymbol(description)
    }

    companion object {
        fun create(realm: Realm) = JSSymbolCtor(realm).initialize()

        @ECMAImpl("20.4.2.2")
        @JvmStatic
        fun for_(arguments: JSArguments): JSValue {
            val key = arguments.argument(0).asString
            for ((globalKey, globalSymbol) in Realm.globalSymbolRegistry) {
                if (globalKey == key)
                    return globalSymbol
            }
            val newSymbol = JSSymbol(key)
            Realm.globalSymbolRegistry[key] = newSymbol
            return newSymbol
        }

        @ECMAImpl("20.4.2.6")
        @JvmStatic
        fun keyFor(arguments: JSArguments): JSValue {
            val sym = arguments.argument(0)
            if (!sym.isSymbol)
                Errors.Symbol.KeyForBadArg.throwTypeError()
            for ((globalKey, globalSymbol) in Realm.globalSymbolRegistry) {
                if (sym == globalSymbol)
                    return globalKey.toValue()
            }
            return JSUndefined
        }
    }
}
