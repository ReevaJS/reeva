package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
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
        defineNativeProperty("description", attrs { +conf; -enum }, ::getDescription, null)
        defineNativeProperty(Realm.`@@toStringTag`.key(), attrs { +conf; -enum; -writ }, ::`get@@toStringTag`, null)
        defineBuiltin("toString", 0, ReevaBuiltin.SymbolProtoToString)
        defineBuiltin("toValue", 0, ReevaBuiltin.SymbolProtoToValue)
        defineBuiltin(Realm.`@@toPrimitive`, 0, ReevaBuiltin.SymbolProtoSymbolToPrimitive)
    }

    fun getDescription(realm: Realm, thisValue: JSValue): JSValue {
        return thisSymbolValue(realm, thisValue, "description").description?.toValue() ?: JSUndefined
    }

    fun `get@@toStringTag`(realm: Realm, thisValue: JSValue) = "Symbol".toValue()

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

        @ECMAImpl("20.4.3.3")
        @JvmStatic
        fun toString(realm: Realm, arguments: JSArguments): JSValue {
            return thisSymbolValue(realm, arguments.thisValue, "toString").descriptiveString().toValue()
        }

        @ECMAImpl("20.4.3.4")
        @JvmStatic
        fun toValue(realm: Realm, arguments: JSArguments): JSValue {
            return thisSymbolValue(realm, arguments.thisValue, "toValue")
        }

        @ECMAImpl("20.4.3.5")
        @JvmStatic
        fun `@@toPrimitive`(realm: Realm, arguments: JSArguments): JSValue {
            return thisSymbolValue(realm, arguments.thisValue, "@@toPrimitive")
        }
    }
}
