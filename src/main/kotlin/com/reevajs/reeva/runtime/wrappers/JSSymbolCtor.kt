package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.primitives.JSSymbol
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSSymbolCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Symbol", 0) {
    override fun init() {
        super.init()

        defineOwnProperty("asyncIterator", Realm.`@@asyncIterator`, 0)
        defineOwnProperty("hasInstance", Realm.`@@hasInstance`, 0)
        defineOwnProperty("isConcatSpreadable", Realm.`@@isConcatSpreadable`, 0)
        defineOwnProperty("iterator", Realm.`@@iterator`, 0)
        defineOwnProperty("match", Realm.`@@match`, 0)
        defineOwnProperty("matchAll", Realm.`@@matchAll`, 0)
        defineOwnProperty("replace", Realm.`@@replace`, 0)
        defineOwnProperty("search", Realm.`@@search`, 0)
        defineOwnProperty("species", Realm.`@@species`, 0)
        defineOwnProperty("split", Realm.`@@split`, 0)
        defineOwnProperty("toPrimitive", Realm.`@@toPrimitive`, 0)
        defineOwnProperty("toStringTag", Realm.`@@toStringTag`, 0)
        defineOwnProperty("unscopables", Realm.`@@unscopables`, 0)

        defineBuiltin("for", 1, Builtin.SymbolCtorFor)
        defineBuiltin("keyFor", 1, Builtin.SymbolCtorKeyFor)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget != JSUndefined)
            Errors.NotACtor("Symbol").throwTypeError(realm)

        val description = arguments.argument(0).let {
            if (it == JSUndefined) null else Operations.toString(realm, it).string
        }
        return JSSymbol(description)
    }

    companion object {
        fun create(realm: Realm) = JSSymbolCtor(realm).initialize()

        @ECMAImpl("20.4.2.2")
        @JvmStatic
        fun `for`(realm: Realm, arguments: JSArguments): JSValue {
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
        fun keyFor(realm: Realm, arguments: JSArguments): JSValue {
            val sym = arguments.argument(0)
            if (!sym.isSymbol)
                Errors.Symbol.KeyForBadArg.throwTypeError(realm)
            for ((globalKey, globalSymbol) in Realm.globalSymbolRegistry) {
                if (sym == globalSymbol)
                    return globalKey.toValue()
            }
            return JSUndefined
        }
    }
}
