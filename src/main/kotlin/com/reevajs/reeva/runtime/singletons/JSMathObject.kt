package com.reevajs.reeva.runtime.singletons

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.toNumber
import com.reevajs.reeva.runtime.toUint32
import com.reevajs.reeva.utils.toValue
import kotlin.math.pow
import kotlin.random.Random

class JSMathObject private constructor(realm: Realm) : JSObject(realm.objectProto) {
    override fun init(realm: Realm) {
        super.init(realm)

        defineOwnProperty("E", JSNumber(2.718281828459045), 0)
        defineOwnProperty("LN10", JSNumber(2.302585092994046), 0)
        defineOwnProperty("LN2", JSNumber(0.6931471805599453), 0)
        defineOwnProperty("LOG10E", JSNumber(0.4342944819032518), 0)
        defineOwnProperty("LOG2E", JSNumber(1.4426950408889634), 0)
        defineOwnProperty("PI", JSNumber(3.141592653589793), 0)
        defineOwnProperty("SQRT1_2", JSNumber(0.7071067811865476), 0)
        defineOwnProperty("SQRT2", JSNumber(1.4142135623730951), 0)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Math".toValue(), 0)

        defineBuiltin(realm, "abs", 1, ::abs)
        defineBuiltin(realm, "acos", 1, ::acos)
        defineBuiltin(realm, "acosh", 1, ::acosh)
        defineBuiltin(realm, "asin", 1, ::asin)
        defineBuiltin(realm, "asinh", 1, ::asinh)
        defineBuiltin(realm, "atan", 1, ::atan)
        defineBuiltin(realm, "atanh", 1, ::atanh)
        defineBuiltin(realm, "atan2", 2, ::atan2)
        defineBuiltin(realm, "cbrt", 1, ::cbrt)
        defineBuiltin(realm, "ceil", 1, ::ceil)
        defineBuiltin(realm, "clz32", 1, ::clz32)
        defineBuiltin(realm, "cos", 1, ::cos)
        defineBuiltin(realm, "cosh", 1, ::cosh)
        defineBuiltin(realm, "exp", 1, ::exp)
        defineBuiltin(realm, "expm1", 1, ::expm1)
        defineBuiltin(realm, "floor", 1, ::floor)
        defineBuiltin(realm, "fround", 1, ::fround)
        defineBuiltin(realm, "hypot", 1, ::hypot)
        defineBuiltin(realm, "imul", 1, ::imul)
        defineBuiltin(realm, "log", 1, ::log)
        defineBuiltin(realm, "log1p", 1, ::log1p)
        defineBuiltin(realm, "log10", 1, ::log10)
        defineBuiltin(realm, "log2", 1, ::log2)
        defineBuiltin(realm, "max", 1, ::max)
        defineBuiltin(realm, "min", 1, ::min)
        defineBuiltin(realm, "pow", 1, ::pow)
        defineBuiltin(realm, "random", 1, ::random)
        defineBuiltin(realm, "round", 1, ::round)
        defineBuiltin(realm, "sign", 1, ::sign)
        defineBuiltin(realm, "sin", 1, ::sin)
        defineBuiltin(realm, "sinh", 1, ::sinh)
        defineBuiltin(realm, "sqrt", 1, ::sqrt)
        defineBuiltin(realm, "tan", 1, ::tan)
        defineBuiltin(realm, "tanh", 1, ::tanh)
        defineBuiltin(realm, "trunc", 1, ::trunc)
    }

    companion object {
        fun create(realm: Realm) = JSMathObject(realm).initialize(realm)

        @ECMAImpl("21.3.2.1")
        @JvmStatic
        fun abs(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.abs(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.2")
        @JvmStatic
        fun acos(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.acos(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.3")
        @JvmStatic
        fun acosh(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.acosh(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.4")
        @JvmStatic
        fun asin(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.asin(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.5")
        @JvmStatic
        fun asinh(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.asinh(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.6")
        @JvmStatic
        fun atan(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.atan(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.7")
        @JvmStatic
        fun atanh(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.atanh(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.8")
        @JvmStatic
        fun atan2(arguments: JSArguments): JSValue {
            val y = arguments.argument(0).toNumber()
            val x = arguments.argument(1).toNumber()
            return kotlin.math.atan2(y.asDouble, x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.9")
        @JvmStatic
        fun cbrt(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            val doubleVal = x.asDouble
            if (doubleVal.isNaN() || doubleVal == 0.0 || doubleVal.isInfinite())
                return x
            return doubleVal.pow(1.0 / 3.0).toValue()
        }

        @ECMAImpl("21.3.2.10")
        @JvmStatic
        fun ceil(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.ceil(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.11")
        @JvmStatic
        fun clz32(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toUint32()
            return x.asInt.toUInt().countLeadingZeroBits().toValue()
        }

        @ECMAImpl("21.3.2.12")
        @JvmStatic
        fun cos(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.cos(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.13")
        @JvmStatic
        fun cosh(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.cosh(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.14")
        @JvmStatic
        fun exp(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.exp(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.15")
        @JvmStatic
        fun expm1(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.expm1(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.16")
        @JvmStatic
        fun floor(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.floor(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.17")
        @JvmStatic
        fun fround(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return x.asDouble.toFloat().toDouble().toValue()
        }

        @ECMAImpl("21.3.2.18")
        @JvmStatic
        fun hypot(arguments: JSArguments): JSValue {
            val coerced = arguments.map { it.toNumber() }
            var onlyZero = true

            // The spec says the first parameter that is NaN or Infinity should be returned,
            // but for some reason test262 says differently. Apparently infinity should always
            // be prioritized
            if (coerced.any { it.isInfinite })
                return JSNumber.POSITIVE_INFINITY

            coerced.forEach { value ->
                if (value.isNaN)
                    return value
                if (!value.isZero)
                    onlyZero = false
            }
            if (onlyZero)
                return JSNumber.ZERO

            return kotlin.math.sqrt(coerced.sumOf { it.asDouble.pow(2.0) }).toValue()
        }

        @ECMAImpl("21.3.2.19")
        @JvmStatic
        fun imul(arguments: JSArguments): JSValue {
            val a = arguments.argument(0).toUint32()
            val b = arguments.argument(1).toUint32()
            val product = (a.asInt * b.asInt) % Operations.MAX_32BIT_INT
            if (product >= Operations.MAX_31BIT_INT)
                return (product - Operations.MAX_32BIT_INT).toValue()
            return product.toValue()
        }

        @ECMAImpl("21.3.2.20")
        @JvmStatic
        fun log(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.ln(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.21")
        @JvmStatic
        fun log1p(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.ln1p(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.22")
        @JvmStatic
        fun log10(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.log10(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.23")
        @JvmStatic
        fun log2(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.log2(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.24")
        @JvmStatic
        fun max(arguments: JSArguments): JSValue {
            if (arguments.isEmpty())
                return JSNumber.NEGATIVE_INFINITY
            val coerced = arguments.map { it.toNumber() }
            return coerced.maxOf { it.asDouble }.toValue()
        }

        @ECMAImpl("21.3.2.25")
        @JvmStatic
        fun min(arguments: JSArguments): JSValue {
            if (arguments.isEmpty())
                return JSNumber.POSITIVE_INFINITY
            val coerced = arguments.map { it.toNumber() }
            return coerced.minOf { it.asDouble }.toValue()
        }

        @ECMAImpl("21.3.2.26")
        @JvmStatic
        fun pow(arguments: JSArguments): JSValue {
            val base = arguments.argument(0).toNumber()
            val exp = arguments.argument(1).toNumber()
            return base.asDouble.pow(exp.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.27")
        @JvmStatic
        fun random(arguments: JSArguments): JSValue {
            return Random.nextDouble().toValue()
        }

        @ECMAImpl("21.3.2.28")
        @JvmStatic
        fun round(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            val doubleVal = x.asDouble
            if (doubleVal < 0.0 && doubleVal >= -0.5)
                return JSNumber.NEGATIVE_ZERO
            val frac = kotlin.math.abs(doubleVal - doubleVal.toInt())
            if (frac == 0.5)
                return (doubleVal + 0.5).toInt().toValue()
            return kotlin.math.round(doubleVal).toValue()
        }

        @ECMAImpl("21.3.2.29")
        @JvmStatic
        fun sign(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.sign(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.30")
        @JvmStatic
        fun sin(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.sin(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.31")
        @JvmStatic
        fun sinh(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.sinh(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.32")
        @JvmStatic
        fun sqrt(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.sqrt(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.33")
        @JvmStatic
        fun tan(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.tan(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.34")
        @JvmStatic
        fun tanh(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.tanh(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.35")
        @JvmStatic
        fun trunc(arguments: JSArguments): JSValue {
            val x = arguments.argument(0).toNumber()
            return kotlin.math.truncate(x.asDouble).toValue()
        }
    }
}
