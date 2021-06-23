package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.toValue

class JSNumberProto private constructor(realm: Realm) : JSNumberObject(realm, JSNumber.ZERO) {
    override fun init() {
        // No super call to avoid prototype complications
        setPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, Descriptor.HAS_BASIC)
        defineOwnProperty("constructor", realm.numberCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineNativeFunction("toString", 0, ::toString)
        defineNativeFunction("valueOf", 0, ::valueOf)
        defineNativeFunction("toExponential", 1, ::toExponential)
        defineNativeFunction("toFixed", 1, ::toFixed)
        defineNativeFunction("toLocaleString", 0, ::toLocaleString)
        defineNativeFunction("toPrecision", 1, ::toPrecision)
        defineNativeFunction("toString", 0, ::toString)
        defineNativeFunction("valueOf", 0, ::valueOf)
    }

    @ECMAImpl("20.1.3.2")
    fun toExponential(realm: Realm, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("20.1.3.3")
    fun toFixed(realm: Realm, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("20.1.3.3")
    fun toLocaleString(realm: Realm, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("20.1.3.3")
    fun toPrecision(realm: Realm, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("20.1.3.3")
    fun toString(realm: Realm, arguments: JSArguments): JSValue {
        // TODO: Spec-compliant conversion
        val x = thisNumberValue(realm, arguments.thisValue, "toString")
        val radix = if (arguments.isEmpty()) {
            10
        } else Operations.toIntegerOrInfinity(realm, arguments[0]).asInt

        if (radix < 2 || radix > 36)
            Errors.Number.InvalidRadix(radix).throwRangeError(realm)

        return if (x.isInt) {
            x.asInt.toString(radix).toValue()
        } else {
            if (radix != 10)
                TODO("Double -> String conversion with radix != 10")
            x.asDouble.toString().toValue()
        }
    }

    @ECMAImpl("20.1.3.3")
    fun valueOf(realm: Realm, arguments: JSArguments): JSValue {
        return thisNumberValue(realm, arguments.thisValue, "valueOf")
    }

    companion object {
        fun create(realm: Realm) = JSNumberProto(realm).initialize()

        @ECMAImpl("20.1.3")
        private fun thisNumberValue(realm: Realm, value: JSValue, methodName: String): JSNumber {
            if (value.isNumber)
                return value as JSNumber
            if (value !is JSObject)
                Errors.IncompatibleMethodCall("Number.prototype.$methodName").throwTypeError(realm)
            return value.getSlotAs(SlotName.NumberData) ?:
                Errors.IncompatibleMethodCall("Number.prototype.$methodName").throwTypeError(realm)
        }
    }
}
