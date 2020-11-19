package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSSymbol
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.toValue

class JSSymbolProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()
        defineOwnProperty("constructor", realm.symbolCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    @JSNativePropertyGetter("description", "Ce")
    fun getDescription(thisValue: JSValue): JSValue {
        return thisSymbolValue(thisValue, "description").description?.toValue() ?: JSUndefined
    }

    @JSNativePropertyGetter("@@toStringTag", "Cew")
    fun `get@@toStringTag`(thisValue: JSValue) = "Symbol".toValue()

    @JSMethod("toString", 0)
    fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisSymbolValue(thisValue, "toString").descriptiveString().toValue()
    }

    @JSMethod("toValue", 0)
    fun toValue(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisSymbolValue(thisValue, "toValue")
    }

    @JSMethod("@@toPrimitive", 1)
    fun `@@toPrimitive`(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisSymbolValue(thisValue, "@@toPrimitive")
    }

    companion object {
        fun create(realm: Realm) = JSSymbolProto(realm).initialize()

        @ECMAImpl("19.4.3")
        private fun thisSymbolValue(value: JSValue, methodName: String): JSSymbol {
            if (value.isSymbol)
                return value.asSymbol
            if (value is JSSymbolObject)
                return value.symbol
            Errors.IncompatibleMethodCall("Symbol.prototype.$methodName").throwTypeError()
        }
    }
}
