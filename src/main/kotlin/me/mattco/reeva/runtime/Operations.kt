@file:Suppress("unused")

package me.mattco.reeva.runtime

import me.mattco.reeva.interpreter.Completion
import me.mattco.reeva.compiler.JSScriptFunction
import me.mattco.reeva.core.Agent
import me.mattco.reeva.interpreter.Record
import me.mattco.reeva.core.Agent.Companion.ifError
import me.mattco.reeva.core.Agent.Companion.throwError
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.FunctionEnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.arrays.JSArrayObject
import me.mattco.reeva.runtime.builtins.JSProxyObject
import me.mattco.reeva.runtime.errors.JSRangeErrorObject
import me.mattco.reeva.runtime.errors.JSReferenceErrorObject
import me.mattco.reeva.runtime.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.wrappers.JSBooleanObject
import me.mattco.reeva.runtime.wrappers.JSNumberObject
import me.mattco.reeva.runtime.wrappers.JSStringObject
import me.mattco.reeva.runtime.wrappers.JSSymbolObject
import me.mattco.reeva.utils.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

object Operations {
    val MAX_SAFE_INTEGER = 2.0.pow(53) - 1
    val MAX_32BIT_INT = 2.0.pow(32)
    val MAX_31BIT_INT = 2.0.pow(31)

    // Note this common gotcha: In Kotlin this really does accept any
    // value, however it gets translated to Object in Java, which can't
    // accept primitives.
    @JvmStatic
    fun wrapInValue(value: Any?): JSValue = when (value) {
        null -> throw Error("Ambiguous use of null in Operations.wrapInValue")
        is Double -> JSNumber(value)
        is Number -> JSNumber(value.toDouble())
        is String -> JSString(value)
        is Boolean -> if (value) JSTrue else JSFalse
        else -> throw Error("Cannot wrap type ${value::class.java.simpleName}")
    }

    @JvmStatic
    fun checkNotBigInt(value: JSValue) {
        expect(value !is JSBigInt)
    }

    @JvmStatic
    fun isNullish(value: JSValue): Boolean {
        return value == JSUndefined || value == JSNull
    }

    @JvmStatic @ECMAImpl("6.1.6.1.1")
    fun numericUnaryMinus(value: JSValue): JSValue {
        expect(value is JSNumber)
        if (value.isNaN)
            return value
        if (value.isPositiveInfinity)
            return JSNumber.NEGATIVE_INFINITY
        if (value.isNegativeInfinity)
            return JSNumber.POSITIVE_INFINITY
        // TODO: -0 -> +0? +0 -> -0?
        return JSNumber(-value.number)
    }

    @JvmStatic @ECMAImpl("6.1.6.1.2")
    fun numericBitwiseNOT(value: JSValue): JSValue {
        expect(value is JSNumber)
        val oldValue = toInt32(value)
        return JSNumber(oldValue.asDouble.toInt().inv())
    }

    @JvmStatic @ECMAImpl("6.1.6.1.3")
    fun numericExponentiate(base: JSValue, exponent: JSValue): JSValue {
        expect(base is JSNumber)
        expect(exponent is JSNumber)
        if (exponent.isNaN)
            return exponent
        if (exponent.isZero)
            return JSNumber(1)
        if (base.isNaN && !exponent.isZero)
            return base

        val baseMag = abs(base.asDouble)
        when {
            baseMag > 1 && exponent.isPositiveInfinity -> return exponent
            baseMag > 1 && exponent.isNegativeInfinity -> return JSNumber.ZERO
            baseMag == 1.0 && exponent.isInfinite -> return JSNumber.NaN
            baseMag < 1 && exponent.isPositiveInfinity -> return JSNumber.ZERO
            baseMag < 1 && exponent.isNegativeInfinity -> return JSNumber.POSITIVE_INFINITY
        }

        // TODO: Other requirements here
        return JSNumber(base.asDouble.pow(exponent.asDouble))
    }

    @JvmStatic @ECMAImpl("6.1.6.1.4")
    fun numericMultiply(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        if ((lhs.isZero || rhs.isZero) && (lhs.isInfinite || rhs.isInfinite))
            return JSNumber.NaN
        // TODO: Other requirements
        return JSNumber(lhs.asDouble * rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("6.1.6.1.5")
    fun numericDivide(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        // TODO: Other requirements
        return JSNumber(lhs.asDouble / rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("6.1.6.1.6")
    fun numericRemainder(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        // TODO: Other requirements
        return JSNumber(lhs.asDouble.rem(rhs.asDouble))
    }

    @JvmStatic @ECMAImpl("6.1.6.1.7")
    fun numericAdd(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        if (lhs.isInfinite && rhs.isInfinite) {
            if (lhs.isPositiveInfinity != rhs.isPositiveInfinity)
                return JSNumber.NaN
            return lhs
        }
        if (lhs.isInfinite)
            return lhs
        if (rhs.isInfinite)
            return rhs
        if (lhs.isNegativeZero && rhs.isNegativeZero)
            return lhs
        if (lhs.isZero && rhs.isZero)
            return JSNumber.ZERO
        // TODO: Overflow
        return JSNumber(lhs.number + rhs.number)
    }

    @JvmStatic @ECMAImpl("6.1.6.1.8")
    fun numericSubtract(lhs: JSValue, rhs: JSValue): JSValue {
        return numericAdd(lhs, numericUnaryMinus(rhs))
    }

    @JvmStatic @ECMAImpl("6.1.6.1.9")
    fun numericLeftShift(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        return JSNumber(toInt32(lhs).asInt shl (toUint32(rhs).asInt % 32))
    }

    @JvmStatic @ECMAImpl("6.1.6.1.10")
    fun numericSignedRightShift(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        return JSNumber(toInt32(lhs).asInt shr (toUint32(rhs).asInt % 32))
    }

    @JvmStatic @ECMAImpl("6.1.6.1.11")
    fun numericUnsignedRightShift(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        return JSNumber(toInt32(lhs).asInt ushr (toUint32(rhs).asInt % 32))
    }

    @JvmStatic @ECMAImpl("6.1.6.1.12")
    fun numericLessThan(lhs: JSValue, rhs: JSValue): JSValue {
        if (lhs.isNaN || rhs.isNaN)
            return JSUndefined
        // TODO: Other requirements
        return wrapInValue(lhs.asDouble < rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("6.1.6.1.13")
    fun numericEqual(lhs: JSValue, rhs: JSValue): JSBoolean {
        if (lhs.isNaN || rhs.isNaN)
            return JSFalse
        // TODO: Other requirements
        return (lhs.asDouble == rhs.asDouble).toValue()
    }

    @JvmStatic @ECMAImpl("6.1.6.1.14")
    fun numericSameValue(lhs: JSValue, rhs: JSValue): JSBoolean {
        if (lhs.isNaN && rhs.isNaN)
            return JSTrue
        if (lhs.isPositiveZero && rhs.isNegativeZero)
            return false.toValue()
        if (lhs.isNegativeZero && rhs.isPositiveZero)
            return false.toValue()
        return (lhs.asDouble == rhs.asDouble).toValue()
    }

    @JvmStatic @ECMAImpl("6.1.6.1.15")
    fun numericSameValueZero(lhs: JSValue, rhs: JSValue): JSBoolean {
        if (lhs.isNaN && rhs.isNaN)
            return JSTrue
        if (lhs.isZero && rhs.isZero)
            return true.toValue()
        return (lhs.asDouble == rhs.asDouble).toValue()
    }

    @JvmStatic @ECMAImpl("6.1.6.1.17")
    fun numericBitwiseAND(lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(toInt32(lhs).asInt and toInt32(rhs).asInt)
    }

    @JvmStatic @ECMAImpl("6.1.6.1.18")
    fun numericBitwiseXOR(lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(toInt32(lhs).asInt xor toInt32(rhs).asInt)
    }

    @JvmStatic @ECMAImpl("6.1.6.1.19")
    fun numericBitwiseOR(lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(toInt32(lhs).asInt or toInt32(rhs).asInt)
    }

    @JvmStatic @ECMAImpl("6.1.6.1.20")
    fun numericToString(value: JSValue): JSValue {
        expect(value is JSNumber)
        if (value.isNaN)
            return "NaN".toValue()
        if (value.isZero)
            return "0".toValue()
        if (value.number < 0)
            return JSString("-" + numericToString(JSNumber(-value.number)))
        if (value.isPositiveInfinity)
            return "Infinity".toValue()

        // TODO: Better conversion, preferably V8's algorithm
        // (mfbt/double-conversion/double-conversion.{h,cc}
        if (value.isInt)
            return value.asInt.toString().toValue()
        return value.asDouble.toString().toValue()
    }

    /**************
     * REFERENCES
     **************/

    @JSThrows
    @JvmStatic @ECMAImpl("6.2.4.8")
    fun getValue(reference: JSValue): JSValue {
        if (reference !is JSReference)
            return reference
        if (reference.isUnresolvableReference) {
            throwError<JSReferenceErrorObject>("unknown reference '${reference.name}'")
            return JSValue.INVALID_VALUE
        }
        var base = reference.baseValue
        if (reference.isPropertyReference) {
            if (reference.hasPrimitiveBase) {
                expect(base != JSUndefined && base != JSNull)
                base = toObject(base as JSValue)
                ifError { return JSValue.INVALID_VALUE }
            }
            val value = (base as JSObject).get(reference.name, reference.getThisValue())
            ifError { return JSValue.INVALID_VALUE }
            if (value is JSNativeProperty)
                return value.get(base)
            if (value is JSAccessor)
                return value.callGetter(base)
            return value
        }

        expect(base is EnvRecord)
        expect(reference.name.isString)
        return base.getBindingValue(reference.name.asString, reference.isStrict)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("6.2.4.9")
    fun putValue(reference: JSValue, value: JSValue) {
        if (reference !is JSReference) {
            throwError<JSReferenceErrorObject>("cannot assign value to ${toPrintableString(value)}")
            return
        }
        var base = reference.baseValue
        if (reference.isUnresolvableReference) {
            if (reference.isStrict) {
                throwError<JSReferenceErrorObject>("cannot resolve identifier ${reference.name}")
                return
            }
            Agent.runningContext.realm.globalObject.set(reference.name, value)
            ifError { return }
        } else if (reference.isPropertyReference) {
            if (reference.hasPrimitiveBase) {
                ecmaAssert(base != JSUndefined && base != JSNull)
                base = toObject(base as JSValue)
                ifError { return }
            }
            val succeeded = (base as JSObject).set(reference.name, value, reference.getThisValue())
            if (!succeeded && reference.isStrict) {
                throwError<JSTypeErrorObject>("TODO: Error message")
            }
        } else {
            ecmaAssert(base is EnvRecord)
            expect(reference.name.isString)
            base.setMutableBinding(reference.name.asString, value, reference.isStrict)
        }
    }

    @JSThrows
    @JvmStatic @ECMAImpl("6.2.4.11")
    fun initializeReferencedBinding(reference: JSReference, value: JSValue) {
        ecmaAssert(!reference.isUnresolvableReference, "Unknown reference with identifier ${reference.name}")
        val base = reference.baseValue
        ecmaAssert(base is EnvRecord)
        expect(reference.name.isString)
        base.initializeBinding(reference.name.asString, value)
    }

    enum class ToPrimitiveHint(private val _text: String) {
        AsDefault("default"),
        AsString("string"),
        AsNumber("number");

        val value by lazy { JSString(_text) }
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.1.1")
    fun toPrimitive(value: JSValue, type: ToPrimitiveHint? = null): JSValue {
        if (value !is JSObject)
            return value

        val exoticToPrim = getMethod(value, Realm.`@@toPrimitive`)
        ifError { return JSValue.INVALID_VALUE }
        if (exoticToPrim != JSUndefined) {
            val hint = when (type) {
                ToPrimitiveHint.AsDefault, null -> "default"
                ToPrimitiveHint.AsString -> "string"
                ToPrimitiveHint.AsNumber -> "number"
            }.toValue()
            val result = call(exoticToPrim, value, listOf(hint))
            ifError { return JSValue.INVALID_VALUE }
            if (result !is JSObject)
                return result
        }

        // TODO Get @@toPrimitive method
        return ordinaryToPrimitive(value, type ?: ToPrimitiveHint.AsNumber)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.1.1.1")
    fun ordinaryToPrimitive(value: JSValue, hint: ToPrimitiveHint): JSValue {
        ecmaAssert(value is JSObject)
        ecmaAssert(hint != ToPrimitiveHint.AsDefault)
        val methodNames = when (hint) {
            ToPrimitiveHint.AsString -> listOf("toString", "valueOf")
            else -> listOf("valueOf", "toString")
        }
        methodNames.forEach { methodName ->
            val method = value.get(methodName)
            ifError { return JSValue.INVALID_VALUE }
            if (isCallable(method)) {
                val result = call(method, value)
                ifError { return JSValue.INVALID_VALUE }
                if (result !is JSObject)
                    return result
            }
        }
        throwError<JSTypeErrorObject>("cannot convert ${toPrintableString(value)} to primitive value")
        return JSValue.INVALID_VALUE
    }

    @JvmStatic @ECMAImpl("7.1.2")
    fun toBoolean(value: JSValue): JSBoolean = when (value.type) {
        JSValue.Type.Empty -> unreachable()
        JSValue.Type.Undefined -> JSFalse
        JSValue.Type.Null -> JSFalse
        JSValue.Type.Boolean -> value as JSBoolean
        JSValue.Type.String -> value.asString.isNotEmpty().toValue()
        JSValue.Type.Number -> (!value.isZero && !value.isNaN).toValue()
        JSValue.Type.BigInt -> TODO()
        JSValue.Type.Symbol -> JSTrue
        JSValue.Type.Object -> JSTrue
        else -> unreachable()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.1.3")
    fun toNumeric(value: JSValue): JSValue {
        val primValue = toPrimitive(value, ToPrimitiveHint.AsNumber)
        ifError { return JSValue.INVALID_VALUE }
        if (primValue is JSBigInt)
            return primValue
        return toNumber(primValue)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.1.4")
    fun toNumber(value: JSValue): JSValue {
        return when (value) {
            JSUndefined -> JSNumber.NaN
            JSNull, JSFalse -> JSNumber.ZERO
            JSTrue -> JSNumber(1)
            is JSNumber -> return value
            is JSString -> TODO()
            is JSSymbol, is JSBigInt -> {
                throwError<JSTypeErrorObject>("cannot convert ${value.type} to Number")
                return JSValue.INVALID_VALUE
            }
            is JSObject -> {
                val prim = toPrimitive(value, ToPrimitiveHint.AsNumber)
                ifError { return JSValue.INVALID_VALUE }
                prim
            }
            else -> unreachable()
        }
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.1.5")
    fun toIntegerOrInfinity(value: JSValue): JSValue {
        val number = toNumber(value)
        ifError { return JSValue.INVALID_VALUE }
        if (number.isNaN || number.isZero)
            return 0.toValue()
        if (number.isInfinite)
            return number
        return floor(abs(number.asDouble)).let {
            if (number.asDouble < 0) it * -1 else it
        }.toValue()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.1.6")
    fun toInt32(value: JSValue): JSValue {
        val number = toNumber(value)
        ifError { return JSValue.INVALID_VALUE }
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber.ZERO

        var int = floor(abs(number.asDouble)).toInt()
        if (number.asDouble < 0)
            int *= -1

        val int32bit = int % MAX_32BIT_INT.toInt()
        if (int32bit >= MAX_31BIT_INT.toInt())
            return JSNumber(int32bit - MAX_32BIT_INT)
        return JSNumber(int32bit)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.1.7")
    fun toUint32(value: JSValue): JSValue {
        val number = toNumber(value)
        ifError { return JSValue.INVALID_VALUE }
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber.ZERO

        var int = floor(abs(number.asDouble)).toInt()
        if (number.asDouble < 0)
            int *= -1
        return JSNumber(int % MAX_32BIT_INT.toInt())
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.1.17")
    fun toString(value: JSValue): JSString {
        return when (value) {
            is JSString -> return value
            JSUndefined -> "undefined"
            JSNull -> "null"
            JSTrue -> "true"
            JSFalse -> "false"
            // TODO: Make sure to follow all of JS's number conversion rules here
            is JSNumber -> if (value.isInt) {
                value.number.toInt().toString()
            } else value.number.toString()
            is JSSymbol -> {
                throwError<JSTypeErrorObject>("cannot convert Symbol to string")
                return "".toValue()
            }
            is JSObject -> {
                val prim = toPrimitive(value, ToPrimitiveHint.AsString)
                ifError { return "".toValue() }
                return toString(prim)
            }
            else -> unreachable()
        }.let(::JSString)
    }

    fun toPrintableString(value: JSValue): String {
        return when (value) {
            is JSUndefined -> "undefined"
            is JSNull -> "null"
            is JSTrue -> "true"
            is JSFalse -> "false"
            is JSNumber -> when {
                value.isNaN -> "NaN"
                value.isPositiveInfinity -> "Infinity"
                value.isNegativeInfinity -> "-Infinity"
                value.isInt -> value.asInt.toString()
                else -> value.asDouble.toString()
            }
            is JSString -> value.string
            is JSSymbol -> value.descriptiveString()
            is JSObject -> "[object <${value::class.java.simpleName}>]"
            is JSAccessor -> "<accessor>"
            is JSNativeProperty -> "<native-property>"
            else -> toString(value).string
        }
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.1.18")
    fun toObject(value: JSValue): JSObject {
        return when (value) {
            is JSObject -> value
            is JSUndefined, JSNull -> {
                throwError<JSTypeErrorObject>("cannot convert ${value.type} to Object")
                return JSObject.INVALID_OBJECT
            }
            is JSBoolean -> JSBooleanObject.create(Agent.runningContext.realm, value)
            is JSNumber -> JSNumberObject.create(Agent.runningContext.realm, value)
            is JSString -> JSStringObject.create(Agent.runningContext.realm, value)
            is JSSymbol -> JSSymbolObject.create(Agent.runningContext.realm, value)
            is JSBigInt -> TODO()
            else -> TODO()
        }
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.1.19")
    fun toPropertyKey(value: JSValue): PropertyKey {
        val key = toPrimitive(value, ToPrimitiveHint.AsString)
        ifError { return PropertyKey.INVALID_KEY }
        if (key is JSNumber && key.number.let { floor(it) == it })
            return PropertyKey(key.number.toInt())
        if (key is JSSymbol)
            return PropertyKey(key)
        return PropertyKey(toString(key).string)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.1.20")
    fun toLength(value: JSValue): JSValue {
        val len = toIntegerOrInfinity(value)
        ifError { return JSValue.INVALID_VALUE }
        val number = len.asDouble
        if (number < 0)
            return 0.toValue()
        return min(number, MAX_SAFE_INTEGER).toValue()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.2.1")
    fun requireObjectCoercible(value: JSValue): JSValue {
        if (value is JSUndefined || value is JSNull) {
            throwError<JSTypeErrorObject>("cannot convert ${value.type} to Object")
            return JSValue.INVALID_VALUE
        }
        return value
    }

    @JvmStatic @ECMAImpl("7.2.2")
    fun isArray(value: JSValue): Boolean {
        if (!value.isObject)
            return false
        if (value is JSArrayObject)
            return true
        // TODO: Proxies
        return false
    }

    @JvmStatic @ECMAImpl("7.2.3")
    fun isCallable(value: JSValue): Boolean {
        if (value is JSProxyObject)
            return value.isCallable
        if (value !is JSFunction)
            return false
        return value.isCallable
    }

    @JvmStatic @ECMAImpl("7.2.4")
    fun isConstructor(value: JSValue): Boolean {
        if (value is JSProxyObject)
            return value.isConstructor
        if (value !is JSFunction)
            return false
        return value.isConstructable
    }

    @JvmStatic @ECMAImpl("7.2.6")
    fun isIntegralNumber(value: JSValue): Boolean {
        if (!value.isNumber)
            return false
        if (value.isNaN || value.isInfinite)
            return false
        val mag = abs(value.asDouble)
        if (mag != floor(mag))
            return false
        return true
    }

    @JvmStatic @ECMAImpl("7.2.7")
    fun isPropertyKey(value: JSValue) = value is JSString || value is JSSymbol

    @JSThrows
    @JvmStatic @ECMAImpl("7.2.13")
    fun abstractRelationalComparison(lhs: JSValue, rhs: JSValue, leftFirst: Boolean): JSValue {
        val px: JSValue
        val py: JSValue

        if (leftFirst) {
            px = toPrimitive(lhs, ToPrimitiveHint.AsNumber)
            ifError { return JSValue.INVALID_VALUE }
            py = toPrimitive(rhs, ToPrimitiveHint.AsNumber)
            ifError { return JSValue.INVALID_VALUE }
        } else {
            py = toPrimitive(rhs, ToPrimitiveHint.AsNumber)
            ifError { return JSValue.INVALID_VALUE }
            px = toPrimitive(lhs, ToPrimitiveHint.AsNumber)
            ifError { return JSValue.INVALID_VALUE }
        }

        if (px is JSString && py is JSString)
            TODO()

        if (px is JSBigInt && py is JSString)
            TODO()
        if (px is JSString && py is JSBigInt)
            TODO()

        val nx = toNumeric(px)
        ifError { return JSValue.INVALID_VALUE }
        val ny = toNumeric(py)
        ifError { return JSValue.INVALID_VALUE }

        if (nx is JSNumber && ny is JSNumber)
            return numericLessThan(nx, ny)

        TODO()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.2.14")
    fun abstractEqualityComparison(lhs: JSValue, rhs: JSValue): JSValue {
        if (lhs.type == rhs.type)
            return strictEqualityComparison(lhs, rhs)

        if (lhs == JSNull && rhs == JSUndefined)
            return JSTrue
        if (lhs == JSUndefined && rhs == JSNull)
            return JSTrue

        if (lhs is JSNumber && rhs is JSString)
            return abstractEqualityComparison(lhs, toNumber(rhs))
        if (lhs is JSString && rhs is JSNumber)
            return abstractEqualityComparison(toNumber(lhs), rhs)

        if (lhs is JSBigInt && rhs is JSString)
            TODO()
        if (lhs is JSString && rhs is JSBigInt)
            TODO()

        if (lhs is JSBoolean)
            return abstractEqualityComparison(toNumber(lhs), rhs)
        if (rhs is JSBoolean)
            return abstractEqualityComparison(lhs, toNumber(rhs))

        if ((lhs is JSString || lhs is JSNumber || lhs is JSBigInt || lhs is JSSymbol) && rhs is JSObject)
            return abstractEqualityComparison(lhs, toPrimitive(rhs))
        if ((rhs is JSString || rhs is JSNumber || rhs is JSBigInt || rhs is JSSymbol) && lhs is JSObject)
            return abstractEqualityComparison(toPrimitive(lhs), rhs)

        if ((lhs is JSBigInt && rhs is JSNumber) || (lhs is JSNumber && rhs is JSBigInt))
            TODO()

        return JSFalse
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.2.15")
    fun strictEqualityComparison(lhs: JSValue, rhs: JSValue): JSValue {
        if (lhs.type != rhs.type)
            return JSFalse
        if (lhs is JSNumber)
            return numericEqual(lhs, rhs)
        if (lhs is JSBigInt)
            TODO()
        return lhs.sameValueNonNumeric(rhs).toValue()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.3")
    fun getV(target: JSValue, property: JSValue): JSValue {
        ecmaAssert(isPropertyKey(property))
        val obj = toObject(target)
        ifError { return JSValue.INVALID_VALUE }
        return obj.get(toPropertyKey(property).also {
            ifError { return JSValue.INVALID_VALUE }
        })
    }

    @JSThrows
    fun createDataProperty(target: JSValue, property: JSValue, value: JSValue): Boolean {
        return createDataProperty(target, toPropertyKey(property).also {
            ifError { return false }
        }, value)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.5")
    fun createDataProperty(target: JSValue, property: PropertyKey, value: JSValue): Boolean {
        ecmaAssert(target is JSObject)
        return target.defineOwnProperty(property, Descriptor(value, Descriptor.defaultAttributes))
    }

    @JSThrows
    fun createDataPropertyOrThrow(target: JSValue, property: JSValue, value: JSValue): Boolean {
        return createDataPropertyOrThrow(target, toPropertyKey(property).also {
            ifError { return false }
        }, value)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.7")
    fun createDataPropertyOrThrow(target: JSValue, property: PropertyKey, value: JSValue): Boolean {
        if (!createDataProperty(target, property, value)) {
            throwError<JSTypeErrorObject>("unable to create property \"$property\" on object ${toPrintableString(target)}")
            return false
        }
        return true
    }

    @JSThrows
    fun definePropertyOrThrow(target: JSValue, property: JSValue, descriptor: Descriptor): Boolean {
        return definePropertyOrThrow(target, toPropertyKey(property), descriptor)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.8")
    fun definePropertyOrThrow(target: JSValue, property: PropertyKey, descriptor: Descriptor): Boolean {
        ecmaAssert(target is JSObject)
        if (!target.defineOwnProperty(property, descriptor)) {
            throwError<JSTypeErrorObject>("unable to define property \"$property\" on object ${toPrintableString(target)}")
            return false
        }
        return true
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.9")
    fun deletePropertyOrThrow(target: JSValue, property: PropertyKey): Boolean {
        ecmaAssert(target is JSObject)
        if (!target.delete(property)) {
            throwError<JSTypeErrorObject>("unable to delete property \"$property\" on object ${toPrintableString(target)}")
            return false
        }
        return true
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.10")
    fun getMethod(value: JSValue, key: JSValue): JSValue {
        val func = getV(value, key)
        ifError { return JSValue.INVALID_VALUE }
        if (func is JSUndefined || func is JSNull)
            return JSUndefined
        if (!isCallable(func)) {
            throwError<JSTypeErrorObject>("cannot call value ${toPrintableString(func)}")
            return JSValue.INVALID_VALUE
        }
        return func
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.11")
    fun hasProperty(value: JSValue, property: PropertyKey): JSValue {
        ecmaAssert(value is JSObject)
        return value.hasProperty(property).toValue()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.12")
    fun hasOwnProperty(value: JSValue, property: PropertyKey): JSValue {
        ecmaAssert(value is JSObject)
        val desc = value.getOwnProperty(property)
        ifError { return JSValue.INVALID_VALUE }
        return (desc != JSUndefined).toValue()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.13")
    fun call(function: JSValue, thisValue: JSValue, arguments: List<JSValue> = emptyList()): JSValue {
        if (!isCallable(function)) {
            throwError<JSTypeErrorObject>("cannot call value ${toPrintableString(function)}")
            return JSValue.INVALID_VALUE
        }
        if (function is JSProxyObject)
            return function.call(thisValue, arguments)
        return (function as JSFunction).call(thisValue, arguments)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.14")
    fun construct(constructor: JSValue, arguments: List<JSValue>, newTarget: JSValue = constructor): JSValue {
        ecmaAssert(isConstructor(constructor))
        ecmaAssert(isConstructor(newTarget))
        if (constructor is JSProxyObject)
            return constructor.construct(arguments, newTarget)
        return (constructor as JSFunction).construct(arguments, newTarget)
    }

    enum class IntegrityLevel {
        Sealed,
        Frozen,
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.15")
    fun setIntegrityLevel(obj: JSObject, level: IntegrityLevel): Boolean {
        if (!obj.preventExtensions())
            return false
        val keys = obj.ownPropertyKeys()
        if (level == IntegrityLevel.Sealed) {
            keys.forEach { key ->
                definePropertyOrThrow(obj, key, Descriptor(JSEmpty, Descriptor.HAS_CONFIGURABLE))
                ifError { return false }
            }
            obj.isSealed = true
        } else {
            keys.forEach { key ->
                val currentDesc = obj.getOwnPropertyDescriptor(key) ?: return@forEach
                val desc = if (currentDesc.isAccessorDescriptor) {
                    Descriptor(JSEmpty, Descriptor.HAS_CONFIGURABLE)
                } else {
                    Descriptor(JSEmpty, Descriptor.HAS_CONFIGURABLE or Descriptor.HAS_WRITABLE)
                }
                definePropertyOrThrow(obj, key, desc)
                ifError { return false }
            }
            obj.isFrozen = true
        }
        return true
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.17")
    fun createArrayFromList(elements: List<JSValue>): JSValue {
        val array = arrayCreate(elements.size)
        elements.forEachIndexed { index, value ->
            createDataPropertyOrThrow(array, index.toValue(), value)
        }
        return array
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.18")
    fun lengthOfArrayLike(target: JSValue): Int {
        ecmaAssert(target is JSObject)
        return toLength(target.get("length").also {
            ifError { return 0 }
        }).asInt
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.19")
    fun createListFromArrayLike(obj: JSValue, types: List<JSValue.Type>? = null): List<JSValue> {
        val elementTypes = types ?: listOf(
            JSValue.Type.Undefined,
            JSValue.Type.Null,
            JSValue.Type.Boolean,
            JSValue.Type.String,
            JSValue.Type.Symbol,
            JSValue.Type.Number,
            JSValue.Type.BigInt,
            JSValue.Type.Object,
        )

        if (obj !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return emptyList()
        }

        val length = lengthOfArrayLike(obj)
        val list = mutableListOf<JSValue>()

        for (i in 0 until length) {
            val next = obj.get(i)
            if (next.type !in elementTypes) {
                throwError<JSTypeErrorObject>("TODO: message")
                return emptyList()
            }
            list.add(next)
        }

        return list
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.20")
    fun invoke(value: JSValue, property: JSValue, arguments: JSArguments = emptyList()): JSValue {
        val func = getV(value, property)
        ifError { return JSValue.INVALID_VALUE }
        return call(func, value, arguments)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.21")
    fun ordinaryHasInstance(ctor: JSFunction, target: JSValue): JSValue {
        if (!isCallable(ctor))
            return JSFalse

        // TODO: [[BoundTargetFunction]] slot check
        if (target !is JSObject)
            return JSFalse

        val ctorProto = ctor.get("prototype")
        if (ctorProto !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return JSFalse
        }

        var obj = target
        while (true) {
            obj = (obj as JSObject).getPrototype()
            if (obj == JSNull)
                return JSFalse
            if (ctorProto.sameValue(obj))
                return JSTrue
        }
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.23")
    fun enumerableOwnPropertyNames(target: JSValue, kind: JSObject.PropertyKind): List<JSValue> {
        ecmaAssert(target is JSObject)
        val properties = mutableListOf<JSValue>()
        target.ownPropertyKeys().forEach { property ->
            if (property.isSymbol)
                return@forEach
            val desc = target.getOwnPropertyDescriptor(property) ?: return@forEach
            if (!desc.isEnumerable)
                return@forEach
            if (kind == JSObject.PropertyKind.Key) {
                properties.add(property.asValue)
            } else {
                val value = target.get(property)
                ifError { return emptyList() }
                if (kind == JSObject.PropertyKind.Value) {
                    properties.add(value)
                } else {
                    TODO("Create an entry array")
                }
            }
        }
        return properties
    }

    enum class IteratorHint {
        Sync,
        Async
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.4.1")
    fun getIterator(obj: JSValue, hint: IteratorHint? = IteratorHint.Sync, _method: JSFunction? = null): JSValue {
        if (hint == IteratorHint.Async)
            TODO()
        val method = _method ?: getMethod(obj, Realm.`@@iterator`)
        if (method == JSUndefined) {
            throwError<JSTypeErrorObject>("${toPrintableString(obj)} is not iterable")
            return JSValue.INVALID_VALUE
        }
        val iterator = call(method, obj)
        if (iterator !is JSObject) {
            throwError<JSTypeErrorObject>("iterator must be an object")
            return JSValue.INVALID_VALUE
        }
        val nextMethod = getV(iterator, "next".toValue())
        return IteratorRecord(iterator, nextMethod, false)
    }

    data class IteratorRecord(val iterator: JSValue, val nextMethod: JSValue, var done: Boolean) : Record()

    @JSThrows
    @JvmStatic @ECMAImpl("7.4.2")
    fun iteratorNext(record: IteratorRecord, value: JSValue? = null): JSObject {
        val result = if (value == null) {
            call(record.nextMethod, record.iterator)
        } else {
            call(record.nextMethod, record.iterator, listOf(value))
        }
        if (result !is JSObject) {
            throwError<JSTypeErrorObject>("iterator result must be an object")
            return JSObject.INVALID_OBJECT
        }
        return result
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.4.3")
    fun iteratorComplete(result: JSValue): JSBoolean {
        ecmaAssert(result is JSObject)
        val done = result.get("done")
        ifError { return JSFalse }
        return toBoolean(done)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.4.4")
    fun iteratorValue(result: JSValue): JSValue {
        ecmaAssert(result is JSObject)
        return result.get("value")
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.4.5")
    fun iteratorStep(record: IteratorRecord): JSValue {
        val result = iteratorNext(record)
        val done = iteratorComplete(result)
        if (done == JSTrue)
            return JSFalse
        return result
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.4.6")
    fun iteratorClose(record: IteratorRecord, completion: Completion): Completion {
        var innerResult = getMethod(record.iterator, "return".toValue()).let {
            if (Agent.hasError())
                Completion(Completion.Type.Throw, JSEmpty)
            else Completion(Completion.Type.Normal, it)
        }
        if (innerResult.isNormal) {
            val return_ = innerResult.value
            if (return_ is JSUndefined)
                return completion
            innerResult = call(return_, record.iterator).let {
                if (Agent.hasError())
                    Completion(Completion.Type.Throw, JSEmpty)
                else Completion(Completion.Type.Normal, it)
            }
        }

        if (completion.isThrow)
            return completion
        if (innerResult.isThrow)
            return innerResult
        if (innerResult.value !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return Completion(Completion.Type.Throw, JSEmpty)
        }
        return completion
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.4.8")
    fun createIterResultObject(value: JSValue, done: Boolean): JSValue {
        val obj = JSObject.create(Agent.runningContext.realm)
        createDataPropertyOrThrow(obj, "value".toValue(), value)
        ifError { return JSValue.INVALID_VALUE }
        createDataPropertyOrThrow(obj, "done".toValue(), done.toValue())
        ifError { return JSValue.INVALID_VALUE }
        return obj
    }

    @JvmStatic @ECMAImpl("8.1.2.1")
    fun getIdentifierReference(env: EnvRecord?, name: String, isStrict: Boolean): JSReference {
        return when {
            env == null -> JSReference(JSUndefined, PropertyKey(name), isStrict)
            env.hasBinding(name) -> JSReference(env, PropertyKey(name), isStrict)
            else -> getIdentifierReference(env.outerEnv, name, isStrict)
        }
    }

    @JvmStatic @ECMAImpl("8.3.1")
    fun getActiveScriptOrModule() {
        TODO()
    }

    @JvmStatic @ECMAImpl("8.3.2")
    fun resolveBinding(name: String, env: EnvRecord? = null): JSReference {
        val actualEnv = env ?: Agent.runningContext.lexicalEnv!!
        // TODO: Strict mode checking
        return getIdentifierReference(actualEnv, name, false)
    }

    @JvmStatic @ECMAImpl("8.3.3")
    fun getThisEnvironment(): EnvRecord {
        // As the spec states, this is guaranteed to resolve without
        // any NPEs as there is always at least a global environment
        // with a this-binding
        var env = Agent.runningContext.lexicalEnv!!
        while (!env.hasThisBinding())
            env = env.outerEnv!!
        return env
    }

    @JSThrows
    @JvmStatic @ECMAImpl("8.3.4")
    fun resolveThisBinding(): JSValue {
        return when (val env = getThisEnvironment()) {
            is FunctionEnvRecord -> env.getThisBinding()
            is GlobalEnvRecord -> env.getThisBinding()
            // is ModuleEnvRecord -> env.getThisBinding()
            else -> unreachable()
        }
    }

    @JvmStatic @ECMAImpl("8.3.6")
    fun getGlobalObject(): JSObject {
        return Agent.runningContext.realm.globalObject
    }

    @JvmStatic @ECMAImpl("9.1.6.2")
    fun isCompatiblePropertyDescriptor(extensible: Boolean, desc: Descriptor, current: Descriptor?): Boolean {
        return validateAndApplyPropertyDescriptor(null, null, extensible, desc, current)
    }

    @JSThrows
    fun validateAndApplyPropertyDescriptor(target: JSObject?, property: PropertyKey?, extensible: Boolean, newDesc: Descriptor, currentDesc: Descriptor?): Boolean {
        if (currentDesc == null) {
            if (!extensible)
                return false
            target?.internalSet(property!!, newDesc.copy())
            return true
        }

        if (currentDesc.run { hasConfigurable && !isConfigurable }) {
            if (newDesc.isConfigurable)
                return false
            if (newDesc.hasEnumerable && currentDesc.isEnumerable != newDesc.isEnumerable)
                return false
        }

        if (currentDesc.isDataDescriptor != newDesc.isDataDescriptor) {
            if (currentDesc.run { hasConfigurable && !isConfigurable })
                return false
            if (currentDesc.isDataDescriptor) {
                target?.internalSet(property!!, Descriptor(
                    JSUndefined,
                    currentDesc.attributes and (Descriptor.WRITABLE or Descriptor.HAS_WRITABLE).inv(),
                    newDesc.getter,
                    newDesc.setter,
                )
                )
            } else {
                target?.internalSet(property!!, Descriptor(
                    newDesc.getActualValue(target),
                    currentDesc.attributes and (Descriptor.WRITABLE or Descriptor.HAS_WRITABLE).inv(),
                )
                )
            }
        } else if (currentDesc.isDataDescriptor && newDesc.isDataDescriptor) {
            if (currentDesc.run { hasConfigurable && hasWritable && !isConfigurable && !isWritable }) {
                if (newDesc.isWritable)
                    return false
                if (!newDesc.getActualValue(target).sameValue(currentDesc.getActualValue(target)))
                    return false
            }
        } else if (currentDesc.run { hasConfigurable && !isConfigurable }) {
            val currentSetter = currentDesc.setter
            val newSetter = newDesc.setter
            if (newSetter != null && (currentSetter == null || !newSetter.sameValue(currentSetter)))
                return false
            val currentGetter = currentDesc.setter
            val newGetter = newDesc.setter
            if (newGetter != null && (currentGetter == null || !newGetter.sameValue(currentGetter)))
                return false
            return true
        }

        if (newDesc.isDataDescriptor) {
            // To distinguish undefined from a non-specified property
            currentDesc.setActualValue(target, newDesc.getActualValue(target))
        }

        currentDesc.getter = newDesc.getter
        currentDesc.setter = newDesc.setter

        if (newDesc.hasConfigurable)
            currentDesc.setConfigurable(newDesc.isConfigurable)
        if (newDesc.hasEnumerable)
            currentDesc.setEnumerable(newDesc.isEnumerable)
        if (newDesc.hasWritable)
            currentDesc.setWritable(newDesc.isWritable)

        target?.internalSet(property!!, currentDesc)

        return true
    }

    @JSThrows
    @JvmStatic @ECMAImpl("9.1.13")
    fun ordinaryCreateFromConstructor(constructor: JSValue, intrinsicDefaultProto: JSObject): JSObject {
        val proto = getPrototypeFromConstructor(constructor, intrinsicDefaultProto)
        ifError { return JSObject.INVALID_OBJECT }
        return JSObject.create((constructor as JSObject).realm, proto)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("9.1.14")
    fun getPrototypeFromConstructor(constructor: JSValue, intrinsicDefaultProto: JSObject): JSObject {
        ecmaAssert(isCallable(constructor))
        val proto = (constructor as JSObject).get("prototype")
        ifError { return JSObject.INVALID_OBJECT }
        if (proto is JSObject)
            return proto
        return intrinsicDefaultProto
    }

    @JvmStatic @ECMAImpl("9.2.1.1")
    fun prepareForOrdinaryCall(function: JSFunction, newTarget: JSValue): ExecutionContext {
        ecmaAssert(newTarget is JSUndefined || newTarget is JSObject)
        val calleeContext = ExecutionContext(function.realm, function)
        val localEnv = FunctionEnvRecord.create(function, newTarget)
        calleeContext.lexicalEnv = localEnv
        calleeContext.variableEnv = localEnv
        Agent.pushContext(calleeContext)
        return calleeContext
    }

    // TODO: Do we really need the calleeContext here?
    // prepareForOrdinaryCall will have just set it as the running
    // execution context
    @JSThrows
    @JvmStatic @ECMAImpl("9.2.1.2")
    fun ordinaryCallBindThis(function: JSFunction, calleeContext: ExecutionContext, thisArgument: JSValue): JSValue {
        if (function.thisMode == JSFunction.ThisMode.Lexical)
            return JSUndefined
        val thisValue = if (function.thisMode == JSFunction.ThisMode.Strict) {
            thisArgument
        } else if (thisArgument == JSUndefined || thisArgument == JSNull) {
            function.realm.globalEnv!!.globalThis
        } else {
            toObject(thisArgument).also {
                ifError { return JSValue.INVALID_VALUE }
            }
        }

        val localEnv = calleeContext.lexicalEnv
        ecmaAssert(localEnv is FunctionEnvRecord)
        return localEnv.bindThisValue(thisValue)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("9.2.5")
    fun makeConstructor(function: JSFunction, writablePrototype: Boolean = true, prototype: JSObject? = null) {
        ecmaAssert(!function.hasProperty("prototype"))
        ecmaAssert(!function.isConstructable)
        ecmaAssert(function.isExtensible())

        function.constructorKind = JSFunction.ConstructorKind.Base
        function.isConstructable = true

        val realProto = prototype ?: run {
            val proto = JSObject.create(function.realm)
            var attrs = Descriptor.HAS_BASIC or Descriptor.CONFIGURABLE
            if (writablePrototype)
                attrs = attrs or Descriptor.WRITABLE
            definePropertyOrThrow(proto, "constructor".key(), Descriptor(function, attrs))
            ifError { return }
            proto
        }
        var attrs = Descriptor.HAS_BASIC
        if (writablePrototype)
            attrs = attrs or Descriptor.WRITABLE
        definePropertyOrThrow(function, "prototype".key(), Descriptor(realProto, attrs))
    }

    @JSThrows
    @JvmStatic @JvmOverloads @ECMAImpl("9.4.2.2")
    fun arrayCreate(length: Int, proto: JSObject? = Agent.runningContext.realm.arrayProto): JSObject {
        if (length >= MAX_32BIT_INT - 1) {
            throwError<JSRangeErrorObject>("array length $length is too large")
            return JSObject.INVALID_OBJECT
        }
        val array = JSArrayObject.create(Agent.runningContext.realm, proto)
        array.indexedProperties.setArrayLikeSize(length)
        return array
    }

    @JSThrows
    @JvmStatic @ECMAImpl("9.4.2.3")
    fun arraySpeciesCreate(originalArray: JSObject, length: Int): JSValue {
        if (!isArray(originalArray))
            return arrayCreate(length)
        var ctor = originalArray.get("constructor")
        if (isConstructor(ctor)) {
            val ctorRealm = (ctor as JSObject).realm
            if (Agent.runningContext.realm != ctorRealm && ctor.sameValue(ctorRealm.arrayCtor)) {
                ctor = JSUndefined
            }
        }
        if (ctor is JSObject) {
            ctor = ctor.get(Realm.`@@species`)
            if (ctor == JSNull)
                ctor = JSUndefined
        }
        if (ctor == JSUndefined)
            return arrayCreate(length)
        if (!isConstructor(ctor)) {
            throwError<JSTypeErrorObject>("TODO: message")
            return JSValue.INVALID_VALUE
        }
        return construct(ctor, listOf(length.toValue()))
    }

    @JSThrows
    @JvmStatic @ECMAImpl("12.3.3")
    fun evaluatePropertyAccessWithExpressionKey(baseValue: JSValue, property: JSValue, isStrict: Boolean): JSValue {
        val propertyValue = getValue(property)
        ifError { return JSValue.INVALID_VALUE }
        val bv = requireObjectCoercible(baseValue)
        ifError { return JSValue.INVALID_VALUE }
        val propertyKey = toPropertyKey(propertyValue)
        ifError { return JSValue.INVALID_VALUE }
        return JSReference(bv, propertyKey, isStrict)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("12.3.4")
    fun evaluatePropertyAccessWithIdentifierKey(baseValue: JSValue, property: String, isStrict: Boolean): JSValue {
        val bv = requireObjectCoercible(baseValue)
        ifError { return JSValue.INVALID_VALUE }
        return JSReference(bv, PropertyKey(property), isStrict)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("12.3.5.1.1")
    fun evaluateNew(target: JSValue, arguments: Array<JSValue>): JSValue {
        val constructor = getValue(target)
        if (!isConstructor(constructor)) {
            throwError<JSTypeErrorObject>("cannot construct value ${toPrintableString(target)}")
            return JSValue.INVALID_VALUE
        }
        return construct(constructor, arguments.toList())
    }

    @JSThrows
    @JvmStatic @ECMAImpl("12.3.6.2")
    fun evaluateCall(target: JSValue, reference: JSValue, arguments: List<JSValue>, tailPosition: Boolean): JSValue {
        val thisValue = if (reference is JSReference) {
            if (reference.isPropertyReference) {
                reference.getThisValue()
            } else {
                ecmaAssert(reference.baseValue is EnvRecord)
                reference.baseValue.withBaseObject()
            }
        } else JSUndefined

        if (!isCallable(target)) {
            throwError<JSTypeErrorObject>("object of type ${target.type} is not callable")
            return JSValue.INVALID_VALUE
        }
        if (tailPosition)
            TODO()
        return call(target as JSFunction, thisValue, arguments.toList())
    }

    @JSThrows
    @JvmStatic @ECMAImpl("12.5.3")
    fun deleteOperator(value: JSValue): JSValue {
        if (value !is JSReference)
            return JSTrue
        if (value.isUnresolvableReference) {
            ecmaAssert(!value.isStrict)
            return JSTrue
        }
        return if (value.isPropertyReference) {
            if (value.isSuperReference)
                TODO()
            expect(value.baseValue is JSValue)
            val baseObj = toObject(value.baseValue)
            val deleteStatus = baseObj.delete(value.name)
            if (!deleteStatus && value.isStrict)
                TODO()
            deleteStatus.toValue()
        } else {
            ecmaAssert(value.baseValue is EnvRecord)
            expect(value.name.isString)
            value.baseValue.deleteBinding(value.name.asString).toValue()
        }
    }

    @JSThrows
    @JvmStatic @ECMAImpl("12.5.5")
    fun typeofOperator(value: JSValue): JSValue {
        if (value is JSReference) {
            if (value.isUnresolvableReference)
                return "undefined".toValue()
        }
        val v = getValue(value)
        ifError { return JSValue.INVALID_VALUE }
        return when (v) {
            JSUndefined -> "undefined"
            JSNull -> "object"
            is JSBoolean -> "boolean"
            is JSNumber -> "number"
            is JSString -> "string"
            is JSSymbol -> "symbol"
            is JSBigInt -> "bigint"
            is JSFunction -> "function"
            is JSProxyObject -> return typeofOperator(v.target)
            is JSObject -> "object"
            else -> unreachable()
        }.toValue()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("12.10.4")
    fun instanceofOperator(target: JSValue, ctor: JSValue): JSValue {
        if (ctor !is JSObject) {
            throwError<JSTypeErrorObject>("right-hand side of 'instanceof' operator must be an object")
            return JSValue.INVALID_VALUE
        }

        val instOfHandler = getMethod(target, Realm.`@@hasInstance`)
        if (instOfHandler !is JSUndefined) {
            val temp = call(instOfHandler, ctor, listOf(target))
            ifError { return JSValue.INVALID_VALUE }
            return toBoolean(temp)
        }

        if (!isCallable(ctor)) {
            throwError<JSTypeErrorObject>("right-hand side of 'instanceof' operator must be callable")
            return JSValue.INVALID_VALUE
        }

        return ordinaryHasInstance(ctor as JSFunction, target)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("12.15.5")
    fun applyStringOrNumericBinaryOperator(lhs: JSValue, rhs: JSValue, op: String): JSValue {
        if (op == "+") {
            val lprim = toPrimitive(lhs)
            val rprim = toPrimitive(rhs)
            if (lprim.isString || rprim.isString) {
                val lstr = toString(lprim)
                ifError { return JSValue.INVALID_VALUE }
                val rstr = toString(rprim)
                ifError { return JSValue.INVALID_VALUE }
                return JSString(lstr.string + rstr.string)

            }
        }
        ifError { return JSValue.INVALID_VALUE }

        val lnum = toNumeric(lhs)
        ifError { return JSValue.INVALID_VALUE }
        val rnum = toNumeric(rhs)
        ifError { return JSValue.INVALID_VALUE }
        if (lnum.type != rnum.type) {
            throwError<JSTypeErrorObject>("cannot apply operator $op to type ${lnum.type} and ${rnum.type}")
            return JSUndefined
        }

        if (lnum.type == JSValue.Type.BigInt)
            TODO()

        return when (op) {
            "**" -> numericExponentiate(lnum, rnum)
            "*" -> numericMultiply(lnum, rnum)
            "/" -> numericDivide(lnum, rnum)
            "%" -> numericRemainder(lnum, rnum)
            "+" -> numericAdd(lnum, rnum)
            "-" -> numericSubtract(lnum, rnum)
            "<<" -> numericLeftShift(lnum, rnum)
            ">>" -> numericSignedRightShift(lnum, rnum)
            ">>>" -> numericUnsignedRightShift(lnum, rnum)
            "&" -> numericBitwiseAND(lnum, rnum)
            "^" -> numericBitwiseXOR(lnum, rnum)
            "|" -> numericBitwiseOR(lnum, rnum)
            else -> unreachable()
        }
    }

    @JSThrows
    @JvmStatic @ECMAImpl("23.1.1.2")
    fun addEntriesFromIterable(target: JSObject, iterable: JSValue, adder: JSFunction): JSObject {
        // TODO: This whole method is super scuffed
        ecmaAssert(iterable != JSUndefined && iterable != JSNull)
        val record = getIterator(iterable) as? IteratorRecord ?: return JSObject.INVALID_OBJECT
        while (true) {
            val next = iteratorStep(record)
            if (next == JSFalse)
                return target
            val nextItem = iteratorValue(next)
            if (nextItem !is JSObject) {
                val error = Completion(Completion.Type.Throw, JSTypeErrorObject.create(target.realm, "TODO: message"))
                return iteratorClose(record, error).let {
                    if (it.isAbrupt)
                        JSObject.INVALID_OBJECT
                    else it.value as? JSObject ?: JSObject.INVALID_OBJECT
                }
            }
            val key = nextItem.get(0)
            ifError {
                return iteratorClose(record, Completion(Completion.Type.Throw, Agent.runningContext.error!!)).let {
                    if (it.isAbrupt)
                        JSObject.INVALID_OBJECT
                    else it.value as? JSObject ?: JSObject.INVALID_OBJECT
                }
            }
            val value = nextItem.get(1)
            ifError {
                return iteratorClose(record, Completion(Completion.Type.Throw, Agent.runningContext.error!!)).let {
                    if (it.isAbrupt)
                        JSObject.INVALID_OBJECT
                    else it.value as? JSObject ?: JSObject.INVALID_OBJECT
                }
            }
            call(adder, target, listOf(key, value))
            ifError {
                return iteratorClose(record, Completion(Completion.Type.Throw, Agent.runningContext.error!!)).let {
                    if (it.isAbrupt)
                        JSObject.INVALID_OBJECT
                    else it.value as? JSObject ?: JSObject.INVALID_OBJECT
                }
            }
        }
    }

    @JSThrows
    @JvmStatic
    fun applyFunctionArguments(function: JSScriptFunction, arguments: List<JSValue>, env: EnvRecord?) {
        function.getParameterNames().forEachIndexed { index, name ->
            val lhs = resolveBinding(name, env)
            var value = if (index > arguments.lastIndex) {
                JSUndefined
            } else arguments[index]

            if (value == JSUndefined && function.getParamHasDefaultValue(index))
                value = function.getDefaultParameterValue(index)

            if (env == null) {
                putValue(lhs, value)
            } else {
                initializeReferencedBinding(lhs, value)
            }
        }
    }
}
