package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSSymbol
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue

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
