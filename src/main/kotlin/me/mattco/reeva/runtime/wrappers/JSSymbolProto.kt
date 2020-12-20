package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSSymbol
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSSymbolProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()
        defineOwnProperty("constructor", realm.symbolCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineNativeProperty("description", attrs { +conf -enum }, ::getDescription, null)
        defineNativeProperty(Realm.`@@toStringTag`.key(), attrs { +conf -enum -writ }, ::`get@@toStringTag`, null)
        defineNativeFunction("toString", 0, ::toString)
        defineNativeFunction("toValue", 0, ::toValue)
        defineNativeFunction(Realm.`@@toPrimitive`.key(), 0, function = ::`@@toPrimitive`)
    }

    fun getDescription(thisValue: JSValue): JSValue {
        return thisSymbolValue(thisValue, "description").description?.toValue() ?: JSUndefined
    }

    fun `get@@toStringTag`(thisValue: JSValue) = "Symbol".toValue()

    fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisSymbolValue(thisValue, "toString").descriptiveString().toValue()
    }

    fun toValue(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisSymbolValue(thisValue, "toValue")
    }

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
