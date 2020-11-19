package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.toValue
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
    }

    @JSMethod("abs", 1)
    fun abs(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.abs(x.asDouble).toValue()
    }

    @JSMethod("acos", 1)
    fun acos(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.acos(x.asDouble).toValue()
    }

    @JSMethod("acosh", 1)
    fun acosh(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.acosh(x.asDouble).toValue()
    }

    @JSMethod("asin", 1)
    fun asin(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.asin(x.asDouble).toValue()
    }

    @JSMethod("asinh", 1)
    fun asinh(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.asinh(x.asDouble).toValue()
    }

    @JSMethod("atan", 1)
    fun atan(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.atan(x.asDouble).toValue()
    }

    @JSMethod("atanh", 1)
    fun atanh(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.atanh(x.asDouble).toValue()
    }

    @JSMethod("atan2", 2)
    fun atan2(thisValue: JSValue, arguments: JSArguments): JSValue {
        val y = Operations.toNumber(arguments.argument(0))
        val x = Operations.toNumber(arguments.argument(1))
        return kotlin.math.atan2(y.asDouble, x.asDouble).toValue()
    }

    @JSMethod("cbrt", 1)
    fun cbrt(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        val doubleVal = x.asDouble
        if (doubleVal.isNaN() || doubleVal == 0.0 || doubleVal.isInfinite())
            return x
        return doubleVal.pow(1.0 / 3.0).toValue()
    }

    @JSMethod("ceil", 1)
    fun ceil(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.ceil(x.asDouble).toValue()
    }

    @JSMethod("clz32", 1)
    fun clz32(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toUint32(arguments.argument(0))
        return x.asInt.toUInt().countLeadingZeroBits().toValue()
    }

    @JSMethod("cos", 1)
    fun cos(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.cos(x.asDouble).toValue()
    }

    @JSMethod("cosh", 1)
    fun cosh(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.cosh(x.asDouble).toValue()
    }

    @JSMethod("exp", 1)
    fun exp(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.exp(x.asDouble).toValue()
    }

    @JSMethod("expm1", 1)
    fun expm1(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.expm1(x.asDouble).toValue()
    }

    @JSMethod("floor", 1)
    fun floor(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.floor(x.asDouble).toValue()
    }

    @JSMethod("fround", 1)
    fun fround(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return x.asDouble.toFloat().toDouble().toValue()
    }

    @JSMethod("hypot", 2)
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

    @JSMethod("imul", 2)
    fun imul(thisValue: JSValue, arguments: JSArguments): JSValue {
        val a = Operations.toUint32(arguments.argument(0))
        val b = Operations.toUint32(arguments.argument(1))
        val product = (a.asInt * b.asInt) % Operations.MAX_32BIT_INT
        if (product >= Operations.MAX_31BIT_INT)
            return (product - Operations.MAX_32BIT_INT).toValue()
        return product.toValue()
    }

    @JSMethod("log", 1)
    fun log(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.ln(x.asDouble).toValue()
    }

    @JSMethod("log1p", 1)
    fun log1p(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.ln1p(x.asDouble).toValue()
    }

    @JSMethod("log10", 1)
    fun log10(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.log10(x.asDouble).toValue()
    }

    @JSMethod("log2", 1)
    fun log2(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.log2(x.asDouble).toValue()
    }

    @JSMethod("max", 2)
    fun max(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (arguments.isEmpty())
            return JSNumber.NEGATIVE_INFINITY
        val coerced = arguments.map(Operations::toNumber)
        return coerced.map { it.asDouble }.maxOrNull()!!.toValue()
    }

    @JSMethod("min", 2)
    fun min(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (arguments.isEmpty())
            return JSNumber.POSITIVE_INFINITY
        val coerced = arguments.map(Operations::toNumber)
        return coerced.map { it.asDouble }.minOrNull()!!.toValue()
    }

    @JSMethod("pow", 2)
    fun pow(thisValue: JSValue, arguments: JSArguments): JSValue {
        val base = Operations.toNumber(arguments.argument(0))
        val exp = Operations.toNumber(arguments.argument(1))
        return base.asDouble.pow(exp.asDouble).toValue()
    }

    @JSMethod("random", 0)
    fun random(thisValue: JSValue, arguments: JSArguments): JSValue {
        return Random.nextDouble().toValue()
    }

    @JSMethod("round", 1)
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

    @JSMethod("sign", 1)
    fun sign(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.sign(x.asDouble).toValue()
    }

    @JSMethod("sin", 1)
    fun sin(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.sin(x.asDouble).toValue()
    }

    @JSMethod("sinh", 1)
    fun sinh(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.sinh(x.asDouble).toValue()
    }

    @JSMethod("sqrt", 1)
    fun sqrt(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.sqrt(x.asDouble).toValue()
    }

    @JSMethod("tan", 1)
    fun tan(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.tan(x.asDouble).toValue()
    }

    @JSMethod("tanh", 1)
    fun tanh(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.tanh(x.asDouble).toValue()
    }

    @JSMethod("trunc", 1)
    fun trunc(thisValue: JSValue, arguments: JSArguments): JSValue {
        val x = Operations.toNumber(arguments.argument(0))
        return kotlin.math.truncate(x.asDouble).toValue()
    }

    companion object {
        fun create(realm: Realm) = JSMathObject(realm).initialize()
    }
}
