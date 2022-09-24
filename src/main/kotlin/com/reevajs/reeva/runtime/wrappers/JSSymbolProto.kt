package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSSymbol
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue

class JSSymbolProto private constructor(realm: Realm) : JSObject(realm.objectProto) {
    override fun init(realm: Realm) {
        super.init(realm)

        defineNativeProperty(
            Realm.WellKnownSymbols.toStringTag.key(),
            attrs { +conf; -enum; -writ },
            ::getSymbolToStringTag,
            null,
        )

        val realm = Agent.activeAgent.getActiveRealm()
        defineOwnProperty("constructor", realm.symbolCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineNativeProperty("description", attrs { +conf; -enum }, ::getDescription, null)
        defineBuiltin(realm, "toString", 0, ::toString)
        defineBuiltin(realm, "toValue", 0, ::toValue)
        defineBuiltin(realm, Realm.WellKnownSymbols.toPrimitive, 0, ::symbolToPrimitive)
    }

    fun getDescription(thisValue: JSValue): JSValue {
        return thisSymbolValue(thisValue, "description").description?.toValue() ?: JSUndefined
    }

    fun getSymbolToStringTag(thisValue: JSValue) = "Symbol".toValue()

    companion object {
        fun create(realm: Realm) = JSSymbolProto(realm).initialize(realm)

        @ECMAImpl("19.4.3")
        private fun thisSymbolValue(value: JSValue, methodName: String): JSSymbol {
            if (value.isSymbol)
                return value.asSymbol
            if (value is JSSymbolObject)
                return value.symbol
            Errors.IncompatibleMethodCall("Symbol.prototype.$methodName").throwTypeError()
        }

        @ECMAImpl("20.4.3.3")
        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            return thisSymbolValue(arguments.thisValue, "toString").descriptiveString().toValue()
        }

        @ECMAImpl("20.4.3.4")
        @JvmStatic
        fun toValue(arguments: JSArguments): JSValue {
            return thisSymbolValue(arguments.thisValue, "toValue")
        }

        @ECMAImpl("20.4.3.5")
        @JvmStatic
        fun symbolToPrimitive(arguments: JSArguments): JSValue {
            return thisSymbolValue(arguments.thisValue, "@@toPrimitive")
        }
    }
}
