package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSSymbol
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.toValue

class JSSymbolProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    @JSNativePropertyGetter("description", attributes = 0)
    fun getDescription(thisValue: JSValue): JSValue {
        return thisSymbolValue(thisValue).description.toValue()
    }

    @JSMethod("toString", 0)
    fun toString_(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisSymbolValue(thisValue).descriptiveString().toValue()
    }

    @JSMethod("toValue", 0)
    fun toValue(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisSymbolValue(thisValue)
    }

    companion object {
        fun create(realm: Realm) = JSSymbolProto(realm).also { it.init() }

        @ECMAImpl("thisSymbolValue", "19.4.3")
        private fun thisSymbolValue(value: JSValue): JSSymbol {
            if (value.isSymbol)
                return value.asSymbol
            if (value is JSSymbolObject)
                return value.symbol
            shouldThrowError("TypeError")
        }
    }
}
