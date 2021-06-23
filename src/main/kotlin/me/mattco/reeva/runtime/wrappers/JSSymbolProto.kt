package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSSymbol
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.attrs
import me.mattco.reeva.utils.key
import me.mattco.reeva.utils.toValue

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

    fun getDescription(realm: Realm, thisValue: JSValue): JSValue {
        return thisSymbolValue(realm, thisValue, "description").description?.toValue() ?: JSUndefined
    }

    fun `get@@toStringTag`(realm: Realm, thisValue: JSValue) = "Symbol".toValue()

    fun toString(realm: Realm, arguments: JSArguments): JSValue {
        return thisSymbolValue(realm, arguments.thisValue, "toString").descriptiveString().toValue()
    }

    fun toValue(realm: Realm, arguments: JSArguments): JSValue {
        return thisSymbolValue(realm, arguments.thisValue, "toValue")
    }

    fun `@@toPrimitive`(realm: Realm, arguments: JSArguments): JSValue {
        return thisSymbolValue(realm, arguments.thisValue, "@@toPrimitive")
    }

    companion object {
        fun create(realm: Realm) = JSSymbolProto(realm).initialize()

        @ECMAImpl("19.4.3")
        private fun thisSymbolValue(realm: Realm, value: JSValue, methodName: String): JSSymbol {
            if (value.isSymbol)
                return value.asSymbol
            if (value is JSSymbolObject)
                return value.symbol
            Errors.IncompatibleMethodCall("Symbol.prototype.$methodName").throwTypeError(realm)
        }
    }
}
