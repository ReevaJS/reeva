package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.mfbt.Dtoa
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSNumberProto private constructor(realm: Realm) : JSNumberObject(realm, JSNumber.ZERO) {
    override fun init() {
        // No super call to avoid prototype complications
        setPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, Descriptor.HAS_BASIC)
        defineOwnProperty("constructor", realm.numberCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineNativeFunction("valueOf", 0, ::valueOf)
        defineNativeFunction("toExponential", 1, ::toExponential)
        defineNativeFunction("toFixed", 1, ::toFixed)
        defineNativeFunction("toLocaleString", 0, ::toLocaleString)
        defineNativeFunction("toPrecision", 1, ::toPrecision)
        defineNativeFunction("toString", 1, ::toString)
        defineNativeFunction("valueOf", 0, ::valueOf)
    }

    @ECMAImpl("20.1.3.2")
    fun toExponential(realm: Realm, arguments: JSArguments): JSValue {
        val x = thisNumberValue(realm, arguments.thisValue, "toExponential")
        val requestedDigits = arguments.argument(0).let {
            if (it == JSUndefined) {
                -1
            } else {
                val value = it.toIntegerOrInfinity(realm)
                if (x.isFinite && (value.isInfinite || value.number !in 0.0..100.0))
                    Errors.Number.PrecisionOutOfRange(value.toJSString(realm).string).throwRangeError(realm)
                value.asInt
            }
        }
        if (!x.isFinite)
            return Operations.numericToString(x).toValue()

        return Dtoa.toExponential(x.asDouble, requestedDigits)?.toValue() ?: TODO()
    }

    @ECMAImpl("20.1.3.3")
    fun toFixed(realm: Realm, arguments: JSArguments): JSValue {
        val x = thisNumberValue(realm, arguments.thisValue, "toFixed")
        val requestedDigits = arguments.argument(0).let {
            if (it == JSUndefined) {
                0
            } else {
                val value = it.toIntegerOrInfinity(realm)
                if (value.isInfinite || value.number !in 0.0..100.0)
                    Errors.Number.PrecisionOutOfRange(value.toJSString(realm).string).throwRangeError(realm)
                value.asInt
            }
        }
        if (!x.isFinite)
            return Operations.numericToString(x).toValue()

        val value = x.asDouble
        if (value >= 1e21)
            return toString(realm, JSArguments(emptyList(), thisValue = value.toValue()))

        return Dtoa.toFixed(x.asDouble, requestedDigits)?.toValue() ?: TODO()
    }

    @ECMAImpl("20.1.3.3")
    fun toLocaleString(realm: Realm, arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("20.1.3.3")
    fun toPrecision(realm: Realm, arguments: JSArguments): JSValue {
        if (arguments.argument(0) == JSUndefined)
            return toString(realm, arguments)

        val x = thisNumberValue(realm, arguments.thisValue, "toFixed")
        val requestedDigits = arguments.argument(0).toIntegerOrInfinity(realm)
        if (!x.isFinite)
            return Operations.numericToString(x).toValue()

        if (requestedDigits.isInfinite || requestedDigits.number !in 1.0..100.0)
            Errors.Number.PrecisionOutOfRange(requestedDigits.toJSString(realm).string).throwRangeError(realm)

        return Dtoa.toPrecision(x.asDouble, requestedDigits.asInt)?.toValue() ?: TODO()
    }

    @ECMAImpl("20.1.3.3")
    fun toString(realm: Realm, arguments: JSArguments): JSValue {
        val x = thisNumberValue(realm, arguments.thisValue, "toString")
        val r = arguments.argument(0).ifUndefined(10.toValue()).toIntegerOrInfinity(realm)

        if (r.isInfinite || r.number !in 2.0..36.0)
            Errors.Number.PrecisionOutOfRange(r.toJSString(realm).string).throwRangeError(realm)

        if (!x.isFinite)
            return Operations.numericToString(x).toValue()

        val num = x.asDouble
        val radix = r.asInt

        if (radix == 10)
            return Dtoa.toShortest(num)?.toValue() ?: TODO()
        return Dtoa.radixToString(num, radix)?.toValue() ?: TODO()
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
