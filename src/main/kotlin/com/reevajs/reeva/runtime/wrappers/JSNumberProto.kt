package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.mfbt.Dtoa
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toIntegerOrInfinity
import com.reevajs.reeva.runtime.toJSString
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSNumberProto private constructor(realm: Realm) : JSNumberObject(realm, JSNumber.ZERO) {
    override fun init(realm: Realm) {
        // No super call to avoid prototype complications
        setPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, Descriptor.HAS_BASIC)
        defineOwnProperty("constructor", realm.numberCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineBuiltin(realm, "valueOf", 0, ::valueOf)
        defineBuiltin(realm, "toExponential", 1, ::toExponential)
        defineBuiltin(realm, "toFixed", 1, ::toFixed)
        defineBuiltin(realm, "toLocaleString", 0, ::toLocaleString)
        defineBuiltin(realm, "toPrecision", 1, ::toPrecision)
        defineBuiltin(realm, "toString", 1, ::toString)
        defineBuiltin(realm, "valueOf", 0, ::valueOf)
    }

    companion object {
        fun create(realm: Realm) = JSNumberProto(realm).initialize(realm)

        @ECMAImpl("20.1.3")
        private fun thisNumberValue(value: JSValue, methodName: String): JSNumber {
            if (value.isNumber)
                return value as JSNumber
            if (value !is JSObject)
                Errors.IncompatibleMethodCall("Number.prototype.$methodName").throwTypeError()
            return value.getSlotOrNull(SlotName.NumberData)
                ?: Errors.IncompatibleMethodCall("Number.prototype.$methodName").throwTypeError()
        }

        @ECMAImpl("20.1.3.2")
        @JvmStatic
        fun toExponential(arguments: JSArguments): JSValue {
            val x = thisNumberValue(arguments.thisValue, "toExponential")
            val requestedDigits = arguments.argument(0).let {
                if (it == JSUndefined) {
                    -1
                } else {
                    val value = it.toIntegerOrInfinity()
                    if (x.isFinite && (value.isInfinite || value.number !in 0.0..100.0))
                        Errors.Number.PrecisionOutOfRange(value.toJSString().string).throwRangeError()
                    value.asInt
                }
            }
            if (!x.isFinite)
                return Operations.numericToString(x).toValue()

            return Dtoa.toExponential(x.asDouble, requestedDigits)?.toValue() ?: TODO()
        }

        @ECMAImpl("20.1.3.3")
        @JvmStatic
        fun toFixed(arguments: JSArguments): JSValue {
            val x = thisNumberValue(arguments.thisValue, "toFixed")
            val requestedDigits = arguments.argument(0).let {
                if (it == JSUndefined) {
                    0
                } else {
                    val value = it.toIntegerOrInfinity()
                    if (value.isInfinite || value.number !in 0.0..100.0)
                        Errors.Number.PrecisionOutOfRange(value.toJSString().string).throwRangeError()
                    value.asInt
                }
            }
            if (!x.isFinite)
                return Operations.numericToString(x).toValue()

            val value = x.asDouble
            if (value >= 1e21)
                return toString(JSArguments(emptyList(), thisValue = value.toValue()))

            return Dtoa.toFixed(x.asDouble, requestedDigits)?.toValue() ?: TODO()
        }

        @ECMAImpl("20.1.3.3")
        @JvmStatic
        fun toLocaleString(arguments: JSArguments): JSValue {
            TODO()
        }

        @ECMAImpl("20.1.3.3")
        @JvmStatic
        fun toPrecision(arguments: JSArguments): JSValue {
            if (arguments.argument(0) == JSUndefined)
                return toString(arguments)

            val x = thisNumberValue(arguments.thisValue, "toFixed")
            val requestedDigits = arguments.argument(0).toIntegerOrInfinity()
            if (!x.isFinite)
                return Operations.numericToString(x).toValue()

            if (requestedDigits.isInfinite || requestedDigits.number !in 1.0..100.0)
                Errors.Number.PrecisionOutOfRange(requestedDigits.toJSString().string).throwRangeError()

            return Dtoa.toPrecision(x.asDouble, requestedDigits.asInt)?.toValue() ?: TODO()
        }

        @ECMAImpl("20.1.3.3")
        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            val x = thisNumberValue(arguments.thisValue, "toString")
            val r = arguments.argument(0).ifUndefined(10.toValue()).toIntegerOrInfinity()

            if (r.isInfinite || r.number !in 2.0..36.0)
                Errors.Number.PrecisionOutOfRange(r.toJSString().string).throwRangeError()

            if (!x.isFinite)
                return Operations.numericToString(x).toValue()

            val num = x.asDouble
            val radix = r.asInt

            if (radix == 10)
                return Dtoa.toShortest(num)?.toValue() ?: TODO()
            return Dtoa.radixToString(num, radix)?.toValue() ?: TODO()
        }

        @ECMAImpl("20.1.3.3")
        @JvmStatic
        fun valueOf(arguments: JSArguments): JSValue {
            return thisNumberValue(arguments.thisValue, "valueOf")
        }
    }
}
