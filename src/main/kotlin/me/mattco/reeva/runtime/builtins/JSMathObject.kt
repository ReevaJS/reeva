package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.utils.*
import kotlin.math.pow
import kotlin.random.Random

@OptIn(ExperimentalUnsignedTypes::class)
@Suppress("UNUSED_PARAMETER")
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

        defineNativeFunction("abs", 1, ::abs)
        defineNativeFunction("acos", 1, ::acos)
        defineNativeFunction("acosh", 1, ::acosh)
        defineNativeFunction("asin", 1, ::asin)
        defineNativeFunction("asinh", 1, ::asinh)
        defineNativeFunction("atan", 1, ::atan)
        defineNativeFunction("atanh", 1, ::atanh)
        defineNativeFunction("atan2", 2, ::atan2)
        defineNativeFunction("cbrt", 1, ::cbrt)
        defineNativeFunction("ceil", 1, ::ceil)
        defineNativeFunction("clz32", 1, ::clz32)
        defineNativeFunction("cos", 1, ::cos)
        defineNativeFunction("cosh", 1, ::cosh)
        defineNativeFunction("exp", 1, ::exp)
        defineNativeFunction("expm1", 1, ::expm1)
        defineNativeFunction("floor", 1, ::floor)
        defineNativeFunction("fround", 1, ::fround)
        defineNativeFunction("hypot", 1, ::hypot)
        defineNativeFunction("imul", 1, ::imul)
        defineNativeFunction("log", 1, ::log)
        defineNativeFunction("log1p", 1, ::log1p)
        defineNativeFunction("log10", 1, ::log10)
        defineNativeFunction("log2", 1, ::log2)
        defineNativeFunction("max", 1, ::max)
        defineNativeFunction("min", 1, ::min)
        defineNativeFunction("pow", 1, ::pow)
        defineNativeFunction("random", 1, ::random)
        defineNativeFunction("round", 1, ::round)
        defineNativeFunction("sign", 1, ::sign)
        defineNativeFunction("sin", 1, ::sin)
        defineNativeFunction("sinh", 1, ::sinh)
        defineNativeFunction("sqrt", 1, ::sqrt)
        defineNativeFunction("tan", 1, ::tan)
        defineNativeFunction("tanh", 1, ::tanh)
        defineNativeFunction("trunc", 1, ::trunc)
    }

    fun abs(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.abs(x.asDouble).toValue()
    }

    fun acos(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.acos(x.asDouble).toValue()
    }

    fun acosh(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.acosh(x.asDouble).toValue()
    }

    fun asin(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.asin(x.asDouble).toValue()
    }

    fun asinh(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.asinh(x.asDouble).toValue()
    }

    fun atan(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.atan(x.asDouble).toValue()
    }

    fun atanh(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.atanh(x.asDouble).toValue()
    }

    fun atan2(thisValue: JSValue, arguments: JSArguments): JSValue {
        val y = Operations.toNumber(arguments.argument(0))
        val x = Operations.toNumber(arguments.argument(1))
        return kotlin.math.atan2(y.asDouble, x.asDouble).toValue()
    }

    fun cbrt(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        val doubleVal = x.asDouble
        if (doubleVal.isNaN() || doubleVal == 0.0 || doubleVal.isInfinite())
            return x
        return doubleVal.pow(1.0 / 3.0).toValue()
    }

    fun ceil(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.ceil(x.asDouble).toValue()
    }

    fun clz32(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toUint32(arguments.argument(0))
        return x.asInt.toUInt().countLeadingZeroBits().toValue()
    }

    fun cos(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.cos(x.asDouble).toValue()
    }

    fun cosh(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.cosh(x.asDouble).toValue()
    }

    fun exp(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.exp(x.asDouble).toValue()
    }

    fun expm1(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.expm1(x.asDouble).toValue()
    }

    fun floor(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.floor(x.asDouble).toValue()
    }

    fun fround(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return x.asDouble.toFloat().toDouble().toValue()
    }

    fun hypot(thisValue: JSValue, arguments: JSArguments): JSValue {
        val coerced = arguments.map(Operations::toNumber)
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

        return kotlin.math.sqrt(coerced.sumByDouble { it.asDouble.pow(2.0) }).toValue()
    }

    fun imul(thisValue: JSValue, arguments: JSArguments): JSValue {
        val a = Operations.toUint32(arguments.argument(0))
        val b = Operations.toUint32(arguments.argument(1))
        val product = (a.asInt * b.asInt) % Operations.MAX_32BIT_INT
        if (product >= Operations.MAX_31BIT_INT)
            return (product - Operations.MAX_32BIT_INT).toValue()
        return product.toValue()
    }

    fun log(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.ln(x.asDouble).toValue()
    }

    fun log1p(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.ln1p(x.asDouble).toValue()
    }

    fun log10(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.log10(x.asDouble).toValue()
    }

    fun log2(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.log2(x.asDouble).toValue()
    }

    fun max(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (arguments.isEmpty())
            return JSNumber.NEGATIVE_INFINITY
        val coerced = arguments.map(Operations::toNumber)
        return coerced.map { it.asDouble }.maxOrNull()!!.toValue()
    }

    fun min(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (arguments.isEmpty())
            return JSNumber.POSITIVE_INFINITY
        val coerced = arguments.map(Operations::toNumber)
        return coerced.map { it.asDouble }.minOrNull()!!.toValue()
    }

    fun pow(thisValue: JSValue, arguments: JSArguments): JSValue {
        val base = Operations.toNumber(arguments.argument(0))
        val exp = Operations.toNumber(arguments.argument(1))
        return base.asDouble.pow(exp.asDouble).toValue()
    }

    fun random(thisValue: JSValue, arguments: JSArguments): JSValue {
        return Random.nextDouble().toValue()
    }

    fun round(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        val doubleVal = x.asDouble
        if (doubleVal < 0.0 && doubleVal >= -0.5)
            return JSNumber.NEGATIVE_ZERO
        val frac = kotlin.math.abs(doubleVal - doubleVal.toInt())
        if (frac == 0.5)
            return (doubleVal + 0.5).toInt().toValue()
        return kotlin.math.round(doubleVal).toValue()
    }

    fun sign(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.sign(x.asDouble).toValue()
    }

    fun sin(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.sin(x.asDouble).toValue()
    }

    fun sinh(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.sinh(x.asDouble).toValue()
    }

    fun sqrt(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.sqrt(x.asDouble).toValue()
    }

    fun tan(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.tan(x.asDouble).toValue()
    }

    fun tanh(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.tanh(x.asDouble).toValue()
    }

    fun trunc(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.truncate(x.asDouble).toValue()
    }

    companion object {
        fun create(realm: Realm) = JSMathObject(realm).also { it.init() }
    }
}
