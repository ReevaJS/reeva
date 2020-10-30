package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Agent.Companion.throwError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSSymbol
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.toValue

class JSSymbolProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    @JSNativePropertyGetter("description", attributes = 0)
    fun getDescription(thisValue: JSValue): JSValue {
        return thisSymbolValue(thisValue).description.toValue()
    }

    @JSNativePropertyGetter("@@toStringTag", Descriptor.CONFIGURABLE)
    fun `get@@toStringTag`(thisValue: JSValue) = "Symbol".toValue()

    @JSThrows
    @JSMethod("toString", 0)
    fun toString_(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisSymbolValue(thisValue).descriptiveString().toValue()
    }

    @JSThrows
    @JSMethod("toValue", 0)
    fun toValue(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisSymbolValue(thisValue)
    }

    @JSThrows
    @JSMethod("@@toPrimitive", 1)
    fun `@@toPrimitive`(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisSymbolValue(thisValue)
    }

    companion object {
        fun create(realm: Realm) = JSSymbolProto(realm).also { it.init() }

        @JSThrows
        @ECMAImpl("19.4.3")
        private fun thisSymbolValue(value: JSValue): JSSymbol {
            if (value.isSymbol)
                return value.asSymbol
            if (value is JSSymbolObject)
                return value.symbol
            throwError<JSTypeErrorObject>("Symbol prototype method called on incompatible object ${Operations.toPrintableString(value)}")
            shouldThrowError("TypeError")
        }
    }
}
