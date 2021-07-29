package com.reevajs.reeva.runtime.singletons

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.utils.toValue
import kotlin.math.pow
import kotlin.random.Random

@OptIn(ExperimentalUnsignedTypes::class)
class JSMathObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("E", JSNumber(2.7182818284590452354), 0)
        defineOwnProperty("LN10", JSNumber(2.302585092994046), 0)
        defineOwnProperty("LN2", JSNumber(0.6931471805599453), 0)
        defineOwnProperty("LOG10E", JSNumber(0.4342944819032518), 0)
        defineOwnProperty("LOG2E", JSNumber(1.4426950408889634), 0)
        defineOwnProperty("PI", JSNumber(3.1415926535897932), 0)
        defineOwnProperty("SQRT1_2", JSNumber(0.7071067811865476), 0)
        defineOwnProperty("SQRT2", JSNumber(1.4142135623730951), 0)
        defineOwnProperty(Realm.`@@toStringTag`, "Math".toValue(), 0)

        defineBuiltin("abs", 1, Builtin.MathAbs)
        defineBuiltin("acos", 1, Builtin.MathAcos)
        defineBuiltin("acosh", 1, Builtin.MathAcosh)
        defineBuiltin("asin", 1, Builtin.MathAsin)
        defineBuiltin("asinh", 1, Builtin.MathAsinh)
        defineBuiltin("atan", 1, Builtin.MathAtan)
        defineBuiltin("atanh", 1, Builtin.MathAtanh)
        defineBuiltin("atan2", 2, Builtin.MathAtan2)
        defineBuiltin("cbrt", 1, Builtin.MathCbrt)
        defineBuiltin("ceil", 1, Builtin.MathCeil)
        defineBuiltin("clz32", 1, Builtin.MathClz32)
        defineBuiltin("cos", 1, Builtin.MathCos)
        defineBuiltin("cosh", 1, Builtin.MathCosh)
        defineBuiltin("exp", 1, Builtin.MathExp)
        defineBuiltin("expm1", 1, Builtin.MathExpm1)
        defineBuiltin("floor", 1, Builtin.MathFloor)
        defineBuiltin("fround", 1, Builtin.MathFround)
        defineBuiltin("hypot", 1, Builtin.MathHypot)
        defineBuiltin("imul", 1, Builtin.MathImul)
        defineBuiltin("log", 1, Builtin.MathLog)
        defineBuiltin("log1p", 1, Builtin.MathLog1p)
        defineBuiltin("log10", 1, Builtin.MathLog10)
        defineBuiltin("log2", 1, Builtin.MathLog2)
        defineBuiltin("max", 1, Builtin.MathMax)
        defineBuiltin("min", 1, Builtin.MathMin)
        defineBuiltin("pow", 1, Builtin.MathPow)
        defineBuiltin("random", 1, Builtin.MathRandom)
        defineBuiltin("round", 1, Builtin.MathRound)
        defineBuiltin("sign", 1, Builtin.MathSign)
        defineBuiltin("sin", 1, Builtin.MathSin)
        defineBuiltin("sinh", 1, Builtin.MathSinh)
        defineBuiltin("sqrt", 1, Builtin.MathSqrt)
        defineBuiltin("tan", 1, Builtin.MathTan)
        defineBuiltin("tanh", 1, Builtin.MathTanh)
        defineBuiltin("trunc", 1, Builtin.MathTrunc)
    }

    companion object {
        fun create(realm: Realm) = JSMathObject(realm).also { it.init() }

        @ECMAImpl("21.3.2.1")
        @JvmStatic
        fun abs(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.abs(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.2")
        @JvmStatic
        fun acos(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.acos(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.3")
        @JvmStatic
        fun acosh(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.acosh(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.4")
        @JvmStatic
        fun asin(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.asin(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.5")
        @JvmStatic
        fun asinh(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.asinh(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.6")
        @JvmStatic
        fun atan(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.atan(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.7")
        @JvmStatic
        fun atanh(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.atanh(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.8")
        @JvmStatic
        fun atan2(realm: Realm, arguments: JSArguments): JSValue {
            val y = Operations.toNumber(realm, arguments.argument(0))
            val x = Operations.toNumber(realm, arguments.argument(1))
            return kotlin.math.atan2(y.asDouble, x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.9")
        @JvmStatic
        fun cbrt(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            val doubleVal = x.asDouble
            if (doubleVal.isNaN() || doubleVal == 0.0 || doubleVal.isInfinite())
                return x
            return doubleVal.pow(1.0 / 3.0).toValue()
        }

        @ECMAImpl("21.3.2.10")
        @JvmStatic
        fun ceil(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.ceil(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.11")
        @JvmStatic
        fun clz32(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toUint32(realm, arguments.argument(0))
            return x.asInt.toUInt().countLeadingZeroBits().toValue()
        }

        @ECMAImpl("21.3.2.12")
        @JvmStatic
        fun cos(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.cos(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.13")
        @JvmStatic
        fun cosh(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.cosh(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.14")
        @JvmStatic
        fun exp(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.exp(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.15")
        @JvmStatic
        fun expm1(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.expm1(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.16")
        @JvmStatic
        fun floor(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.floor(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.17")
        @JvmStatic
        fun fround(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return x.asDouble.toFloat().toDouble().toValue()
        }

        @ECMAImpl("21.3.2.18")
        @JvmStatic
        fun hypot(realm: Realm, arguments: JSArguments): JSValue {
            val coerced = arguments.map { Operations.toNumber(realm, it) }
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
        fun imul(realm: Realm, arguments: JSArguments): JSValue {
            val a = Operations.toUint32(realm, arguments.argument(0))
            val b = Operations.toUint32(realm, arguments.argument(1))
            val product = (a.asInt * b.asInt) % Operations.MAX_32BIT_INT
            if (product >= Operations.MAX_31BIT_INT)
                return (product - Operations.MAX_32BIT_INT).toValue()
            return product.toValue()
        }

        @ECMAImpl("21.3.2.20")
        @JvmStatic
        fun log(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.ln(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.21")
        @JvmStatic
        fun log1p(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.ln1p(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.22")
        @JvmStatic
        fun log10(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.log10(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.23")
        @JvmStatic
        fun log2(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.log2(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.24")
        @JvmStatic
        fun max(realm: Realm, arguments: JSArguments): JSValue {
            if (arguments.isEmpty())
                return JSNumber.NEGATIVE_INFINITY
            val coerced = arguments.map { Operations.toNumber(realm, it) }
            return coerced.maxOf { it.asDouble }.toValue()
        }

        @ECMAImpl("21.3.2.25")
        @JvmStatic
        fun min(realm: Realm, arguments: JSArguments): JSValue {
            if (arguments.isEmpty())
                return JSNumber.POSITIVE_INFINITY
            val coerced = arguments.map { Operations.toNumber(realm, it) }
            return coerced.minOf { it.asDouble }.toValue()
        }

        @ECMAImpl("21.3.2.26")
        @JvmStatic
        fun pow(realm: Realm, arguments: JSArguments): JSValue {
            val base = Operations.toNumber(realm, arguments.argument(0))
            val exp = Operations.toNumber(realm, arguments.argument(1))
            return base.asDouble.pow(exp.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.27")
        @JvmStatic
        fun random(realm: Realm, arguments: JSArguments): JSValue {
            return Random.nextDouble().toValue()
        }

        @ECMAImpl("21.3.2.28")
        @JvmStatic
        fun round(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
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
        fun sign(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.sign(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.30")
        @JvmStatic
        fun sin(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.sin(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.31")
        @JvmStatic
        fun sinh(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.sinh(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.32")
        @JvmStatic
        fun sqrt(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.sqrt(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.33")
        @JvmStatic
        fun tan(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.tan(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.34")
        @JvmStatic
        fun tanh(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.tanh(x.asDouble).toValue()
        }

        @ECMAImpl("21.3.2.35")
        @JvmStatic
        fun trunc(realm: Realm, arguments: JSArguments): JSValue {
            val x = Operations.toNumber(realm, arguments.argument(0))
            return kotlin.math.truncate(x.asDouble).toValue()
        }
    }
}
