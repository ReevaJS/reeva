@file:Suppress("unused")

package me.mattco.reeva.runtime

import me.mattco.reeva.compiler.JSScriptFunction
import me.mattco.reeva.runtime.Agent.Companion.checkError
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.contexts.ExecutionContext
import me.mattco.reeva.runtime.environment.EnvRecord
import me.mattco.reeva.runtime.environment.FunctionEnvRecord
import me.mattco.reeva.runtime.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.JSReference
import me.mattco.reeva.runtime.values.arrays.JSArray
import me.mattco.reeva.runtime.values.errors.JSRangeErrorObject
import me.mattco.reeva.runtime.values.errors.JSReferenceErrorObject
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.objects.PropertyKey
import me.mattco.reeva.runtime.values.primitives.*
import me.mattco.reeva.runtime.values.wrappers.JSStringObject
import me.mattco.reeva.runtime.values.wrappers.JSSymbolObject
import me.mattco.reeva.utils.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

object Operations {
    val MAX_SAFE_INTEGER = 2.0.pow(53) - 1
    val MAX_32BIT_INT = 2.0.pow(32)
    val MAX_31BIT_INT = 2.0.pow(31)

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

    @JvmStatic @ECMAImpl("Number::unaryMinus(x)", "6.1.6.1.1")
    fun numericUnaryMinus(value: JSValue): JSValue {
        expect(value is JSNumber)
        if (value.isNaN)
            return value
        if (value.isPositiveInfinity)
            return JSNumber(Double.NEGATIVE_INFINITY)
        if (value.isNegativeInfinity)
            return JSNumber(Double.POSITIVE_INFINITY)
        // TODO: -0 -> +0? +0 -> -0?
        return JSNumber(-value.number)
    }

    @JvmStatic @ECMAImpl("Number::bitwiseNOT", "6.1.6.1.2")
    fun numericBitwiseNOT(value: JSValue): JSValue {
        expect(value is JSNumber)
        val oldValue = toInt32(value)
        return JSNumber(oldValue.asDouble.toInt().inv())
    }

    @JvmStatic @ECMAImpl("Number::exponentiate", "6.1.6.1.3")
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
            baseMag > 1 && exponent.isNegativeInfinity -> return JSNumber(0)
            baseMag == 1.0 && exponent.isInfinite -> return JSNumber(Double.NaN)
            baseMag < 1 && exponent.isPositiveInfinity -> return JSNumber(0)
            baseMag < 1 && exponent.isNegativeInfinity -> return JSNumber(Double.POSITIVE_INFINITY)
        }

        // TODO: Other requirements here
        return JSNumber(base.asDouble.pow(exponent.asDouble))
    }

    @JvmStatic @ECMAImpl("Number::multiply", "6.1.6.1.4")
    fun numericMultiply(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        if ((lhs.isZero || rhs.isZero) && (lhs.isInfinite || rhs.isInfinite))
            return JSNumber(Double.NaN)
        // TODO: Other requirements
        return JSNumber(lhs.asDouble * rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("Number::divide", "6.1.6.1.5")
    fun numericDivide(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        // TODO: Other requirements
        return JSNumber(lhs.asDouble / rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("Number::remainder", "6.1.6.1.6")
    fun numericRemainder(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        // TODO: Other requirements
        return JSNumber(lhs.asDouble.rem(rhs.asDouble))
    }

    @JvmStatic @ECMAImpl("Number::add", "6.1.6.1.7")
    fun numericAdd(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        if (lhs.isInfinite && rhs.isInfinite) {
            if (lhs.isPositiveInfinity != rhs.isPositiveInfinity)
                return JSNumber(Double.NaN)
            return lhs
        }
        if (lhs.isInfinite)
            return lhs
        if (rhs.isInfinite)
            return rhs
        if (lhs.isNegativeZero && rhs.isNegativeZero)
            return lhs
        if (lhs.isZero && rhs.isZero)
            return JSNumber(0)
        // TODO: Overflow
        return JSNumber(lhs.number + rhs.number)
    }

    @JvmStatic @ECMAImpl("Number::subtract", "6.1.6.1.8")
    fun numericSubtract(lhs: JSValue, rhs: JSValue): JSValue {
        return numericAdd(lhs, numericUnaryMinus(rhs))
    }

    @JvmStatic @ECMAImpl("Number::leftShift", "6.1.6.1.9")
    fun numericLeftShift(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        return JSNumber(toInt32(lhs).asInt shl (toUint32(rhs).asInt % 32))
    }

    @JvmStatic @ECMAImpl("Number::signedRightShift", "6.1.6.1.10")
    fun numericSignedRightShift(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        return JSNumber(toInt32(lhs).asInt shr (toUint32(rhs).asInt % 32))
    }

    @JvmStatic @ECMAImpl("Number::unsignedRightShift", "6.1.6.1.11")
    fun numericUnsignedRightShift(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber(Double.NaN)
        return JSNumber(toInt32(lhs).asInt ushr (toUint32(rhs).asInt % 32))
    }

    @JvmStatic @ECMAImpl("Number::lessThan", "6.1.6.1.12")
    fun numericLessThan(lhs: JSValue, rhs: JSValue): JSValue {
        if (lhs.isNaN || rhs.isNaN)
            return JSUndefined
        // TODO: Other requirements
        return wrapInValue(lhs.asDouble < rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("Number::equal", "6.1.6.1.13")
    fun numericEqual(lhs: JSValue, rhs: JSValue): JSValue {
        if (lhs.isNaN || rhs.isNaN)
            return JSUndefined
        // TODO: Other requirements
        return wrapInValue(lhs.asDouble == rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("Number::sameValue", "6.1.6.1.14")
    fun numericSameValue(lhs: JSValue, rhs: JSValue): JSValue {
        if (lhs.isNaN || rhs.isNaN)
            return JSUndefined
        // TODO: Other requirements
        return wrapInValue(lhs.asDouble == rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("Number::sameValueZero", "6.1.6.1.15")
    fun numericSameValueZero(lhs: JSValue, rhs: JSValue): JSValue {
        if (lhs.isNaN || rhs.isNaN)
            return JSUndefined
        // TODO: Other requirements
        return wrapInValue(lhs.asDouble == rhs.asDouble)
    }

    @JvmStatic @ECMAImpl("Number::bitwiseAND", "6.1.6.1.17")
    fun numericBitwiseAND(lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(toInt32(lhs).asInt and toInt32(rhs).asInt)
    }

    @JvmStatic @ECMAImpl("Number::bitwiseXOR", "6.1.6.1.18")
    fun numericBitwiseXOR(lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(toInt32(lhs).asInt xor toInt32(rhs).asInt)
    }

    @JvmStatic @ECMAImpl("Number::bitwiseOR", "6.1.6.1.19")
    fun numericBitwiseOR(lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(toInt32(lhs).asInt or toInt32(rhs).asInt)
    }

    @JvmStatic @ECMAImpl("Number::toString", "6.1.6.1.20")
    fun numericToString(lhs: JSValue): JSValue {
        TODO()
    }

    /**************
     * REFERENCES
     **************/

    @JSThrows
    @JvmStatic @ECMAImpl("GetValue", "6.2.4.8")
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
                checkError() ?: return JSValue.INVALID_VALUE
            }
            val value = (base as JSObject).get(reference.name, reference.getThisValue())
            checkError() ?: return JSValue.INVALID_VALUE
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
    @JvmStatic @ECMAImpl("PutValue", "6.2.4.9")
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
            checkError() ?: return
        } else if (reference.isPropertyReference) {
            if (reference.hasPrimitiveBase) {
                ecmaAssert(base != JSUndefined && base != JSNull)
                base = toObject(base as JSValue)
                checkError() ?: return
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
    @JvmStatic @ECMAImpl("InitializeReferencedBinding", "6.2.4.11")
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
    @JvmStatic @ECMAImpl("ToPrimitive", "7.1.1")
    fun toPrimitive(value: JSValue, type: ToPrimitiveHint? = null): JSValue {
        if (value !is JSObject)
            return value

        val exoticToPrim = getMethod(value, value.realm.`@@toPrimitive`)
        checkError() ?: return JSValue.INVALID_VALUE
        if (exoticToPrim != JSUndefined) {
            val hint = when (type) {
                ToPrimitiveHint.AsDefault, null -> "default"
                ToPrimitiveHint.AsString -> "string"
                ToPrimitiveHint.AsNumber -> "number"
            }.toValue()
            val result = call(exoticToPrim, value, listOf(hint))
            checkError() ?: return JSValue.INVALID_VALUE
            if (result !is JSObject)
                return result
        }

        // TODO Get @@toPrimitive method
        return ordinaryToPrimitive(value, type ?: ToPrimitiveHint.AsNumber)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("OrdinaryToPrimitive", "7.1.1.1")
    fun ordinaryToPrimitive(value: JSValue, hint: ToPrimitiveHint): JSValue {
        ecmaAssert(value is JSObject)
        ecmaAssert(hint != ToPrimitiveHint.AsDefault)
        val methodNames = when (hint) {
            ToPrimitiveHint.AsString -> listOf("toString", "valueOf")
            else -> listOf("valueOf", "toString")
        }
        methodNames.forEach { methodName ->
            val method = value.get(methodName)
            checkError() ?: return JSValue.INVALID_VALUE
            if (isCallable(method)) {
                val result = call(method, value)
                checkError() ?: return JSValue.INVALID_VALUE
                if (result !is JSObject)
                    return result
            }
        }
        throwError<JSTypeErrorObject>("cannot convert ${toPrintableString(value)} to primitive value")
        return JSValue.INVALID_VALUE
    }

    @JvmStatic @ECMAImpl("ToBoolean", "7.1.2")
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
    @JvmStatic @ECMAImpl("ToNumeric", "7.1.3")
    fun toNumeric(value: JSValue): JSValue {
        val primValue = toPrimitive(value, ToPrimitiveHint.AsNumber)
        checkError() ?: return JSValue.INVALID_VALUE
        if (primValue is JSBigInt)
            return primValue
        return toNumber(primValue)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("ToNumber", "7.1.4")
    fun toNumber(value: JSValue): JSValue {
        return when (value) {
            JSUndefined -> JSNumber(Double.NaN)
            JSNull, JSFalse -> JSNumber(0)
            JSTrue -> JSNumber(1)
            is JSNumber -> return value
            is JSString -> TODO()
            is JSSymbol, is JSBigInt -> {
                throwError<JSTypeErrorObject>("cannot convert ${value.type} to Number")
                return JSValue.INVALID_VALUE
            }
            is JSObject -> {
                val prim = toPrimitive(value, ToPrimitiveHint.AsNumber)
                checkError() ?: return JSValue.INVALID_VALUE
                prim
            }
            else -> unreachable()
        }
    }

    @JSThrows
    @JvmStatic @ECMAImpl("ToIntegerOrInfinity", "7.1.5")
    fun toIntegerOrInfinity(value: JSValue): JSValue {
        val number = toNumber(value)
        checkError() ?: return JSValue.INVALID_VALUE
        if (number.isNaN || number.isZero)
            return 0.toValue()
        if (number.isInfinite)
            return number
        return floor(abs(number.asDouble)).let {
            if (number.asDouble < 0) it * -1 else it
        }.toValue()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("ToInt32", "7.1.6")
    fun toInt32(value: JSValue): JSValue {
        val number = toNumber(value)
        checkError() ?: return JSValue.INVALID_VALUE
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber(0)

        var int = floor(abs(number.asDouble)).toInt()
        if (number.asDouble < 0)
            int *= -1

        val int32bit = int % MAX_32BIT_INT.toInt()
        if (int32bit >= MAX_31BIT_INT.toInt())
            return JSNumber(int32bit - MAX_32BIT_INT)
        return JSNumber(int32bit)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("ToUint32", "7.1.7")
    fun toUint32(value: JSValue): JSValue {
        val number = toNumber(value)
        checkError() ?: return JSValue.INVALID_VALUE
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber(0)

        var int = floor(abs(number.asDouble)).toInt()
        if (number.asDouble < 0)
            int *= -1
        return JSNumber(int % MAX_32BIT_INT.toInt())
    }

    @JSThrows
    @JvmStatic @ECMAImpl("ToString", "7.1.17")
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
                checkError() ?: return "".toValue()
                return toString(prim)
            }
            else -> unreachable()
        }.let(::JSString)
    }

    fun toPrintableString(value: JSValue, withQuotes: Boolean = true): String {
        return when (value) {
            is JSSymbol -> value.descriptiveString()
            is JSString -> {
                val str = toString(value).string
                if (withQuotes) "\"$str\"" else str
            }
            else -> toString(value).string
        }
    }

    @JSThrows
    @JvmStatic @ECMAImpl("ToObject", "7.1.18")
    fun toObject(value: JSValue): JSObject {
        return when (value) {
            is JSObject -> value
            is JSUndefined, JSNull -> {
                throwError<JSTypeErrorObject>("cannot convert ${value.type} to Object")
                return JSObject.INVALID_OBJECT
            }
            is JSBoolean -> TODO()
            is JSNumber -> TODO()
            is JSString -> JSStringObject.create(Agent.runningContext.realm, value)
            is JSSymbol -> JSSymbolObject.create(Agent.runningContext.realm, value)
            is JSBigInt -> TODO()
            else -> TODO()
        }
    }

    @JSThrows
    @JvmStatic @ECMAImpl("ToPropertyKey", "7.1.19")
    fun toPropertyKey(value: JSValue): PropertyKey {
        val key = toPrimitive(value, ToPrimitiveHint.AsString)
        checkError() ?: return PropertyKey.INVALID_KEY
        if (key is JSSymbol)
            return PropertyKey(key)
        return PropertyKey(toString(key).string)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("ToLength", "7.1.20")
    fun toLength(value: JSValue): JSValue {
        val len = toIntegerOrInfinity(value)
        checkError() ?: return JSValue.INVALID_VALUE
        val number = len.asDouble
        if (number < 0)
            return 0.toValue()
        return min(number, MAX_SAFE_INTEGER).toValue()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("RequireObjectCoercible", "7.2.1")
    fun requireObjectCoercible(value: JSValue): JSValue {
        if (value is JSUndefined || value is JSNull) {
            throwError<JSTypeErrorObject>("cannot convert ${value.type} to Object")
            return JSValue.INVALID_VALUE
        }
        return value
    }

    @JvmStatic @ECMAImpl("IsArray", "7.2.2")
    fun isArray(value: JSValue): Boolean {
        if (!value.isObject)
            return false
        if (value is JSArray)
            return true
        // TODO: Proxies
        return false
    }

    @JvmStatic @ECMAImpl("IsCallable", "7.2.3")
    fun isCallable(value: JSValue): Boolean {
        if (value !is JSFunction)
            return false
        return value.isCallable
    }

    @JvmStatic @ECMAImpl("IsConstructor", "7.2.4")
    fun isConstructor(value: JSValue): Boolean {
        if (value !is JSFunction)
            return false
        return value.isConstructable
    }

    @JvmStatic @ECMAImpl("IsIntegralNumber", "7.2.6")
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

    @JvmStatic @ECMAImpl("IsPropertyKey", "7.2.7")
    fun isPropertyKey(value: JSValue) = value is JSString || value is JSSymbol

    @JSThrows
    @JvmStatic @ECMAImpl("Abstract Relational Comparison", "7.2.13")
    fun abstractRelationalComparison(lhs: JSValue, rhs: JSValue, leftFirst: Boolean): JSValue {
        val px: JSValue
        val py: JSValue

        if (leftFirst) {
            px = toPrimitive(lhs, ToPrimitiveHint.AsNumber)
            checkError() ?: return JSValue.INVALID_VALUE
            py = toPrimitive(rhs, ToPrimitiveHint.AsNumber)
            checkError() ?: return JSValue.INVALID_VALUE
        } else {
            py = toPrimitive(rhs, ToPrimitiveHint.AsNumber)
            checkError() ?: return JSValue.INVALID_VALUE
            px = toPrimitive(lhs, ToPrimitiveHint.AsNumber)
            checkError() ?: return JSValue.INVALID_VALUE
        }

        if (px is JSString && py is JSString)
            TODO()

        if (px is JSBigInt && py is JSString)
            TODO()
        if (px is JSString && py is JSBigInt)
            TODO()

        val nx = toNumeric(px)
        checkError() ?: return JSValue.INVALID_VALUE
        val ny = toNumeric(py)
        checkError() ?: return JSValue.INVALID_VALUE

        if (nx is JSNumber && ny is JSNumber)
            return numericLessThan(nx, ny)

        TODO()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("Abstract Equality Comparison", "7.2.14")
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
    @JvmStatic @ECMAImpl("Strict Equality Comparison", "7.2.15")
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
    @JvmStatic @ECMAImpl("GetV", "7.3.3")
    fun getV(target: JSValue, property: JSValue): JSValue {
        ecmaAssert(isPropertyKey(property))
        val obj = toObject(target)
        checkError() ?: return JSValue.INVALID_VALUE
        return obj.get(toPropertyKey(property).also {
            checkError() ?: return JSValue.INVALID_VALUE
        })
    }

    @JSThrows
    @JvmStatic @ECMAImpl("CreateDataProperty", "7.3.5")
    fun createDataProperty(target: JSValue, property: JSValue, value: JSValue): Boolean {
        ecmaAssert(target is JSObject)
        return target.defineOwnProperty(toPropertyKey(property).also {
            checkError() ?: return false
        }, value, Descriptor.defaultAttributes)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("CreateDataPropertyOrThrow", "7.3.7")
    fun createDataPropertyOrThrow(target: JSValue, property: JSValue, value: JSValue): Boolean {
        if (!createDataProperty(target, property, value)) {
            throwError<JSTypeErrorObject>("unable to create property \"${toPrintableString(property)}\" on object ${toPrintableString(target)}")
            return false
        }
        return true
    }

    @JSThrows
    @JvmStatic @ECMAImpl("DefinePropertyOrThrow", "7.3.8")
    fun definePropertyOrThrow(target: JSValue, property: JSValue, descriptor: Descriptor): Boolean {
        ecmaAssert(target is JSObject)
        if (!target.defineOwnProperty(toPropertyKey(property), descriptor)) {
            throwError<JSTypeErrorObject>("unable to define property \"${toPrintableString(property)}\" on object ${toPrintableString(target)}")
            return false
        }
        return true
    }

    @JSThrows
    @JvmStatic @ECMAImpl("GetMethod", "7.3.10")
    fun getMethod(value: JSValue, key: JSValue): JSValue {
        val func = getV(value, key)
        checkError() ?: return JSValue.INVALID_VALUE
        if (func is JSUndefined || func is JSNull)
            return JSUndefined
        if (!isCallable(func)) {
            throwError<JSTypeErrorObject>("cannot call value ${toPrintableString(func)}")
            return JSValue.INVALID_VALUE
        }
        return func
    }

    @JSThrows
    @JvmStatic @ECMAImpl("HasOwnProperty", "7.3.12")
    fun hasOwnProperty(value: JSValue, property: PropertyKey): JSValue {
        ecmaAssert(value is JSObject)
        val desc = value.getOwnProperty(property)
        checkError() ?: return JSValue.INVALID_VALUE
        return (desc != JSUndefined).toValue()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("Call", "7.3.13")
    fun call(function: JSValue, thisValue: JSValue, arguments: List<JSValue> = emptyList()): JSValue {
        if (!isCallable(function)) {
            throwError<JSTypeErrorObject>("cannot call value ${toPrintableString(function)}")
            return JSValue.INVALID_VALUE
        }
        return (function as JSFunction).call(thisValue, arguments)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("Construct", "7.3.14")
    fun construct(constructor: JSValue, arguments: List<JSValue>, newTarget: JSValue = constructor): JSValue {
        ecmaAssert(isConstructor(constructor))
        ecmaAssert(isConstructor(newTarget))
        return (constructor as JSFunction).construct(arguments, newTarget as JSFunction)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("LengthOfArrayLike", "7.3.18")
    fun lengthOfArrayLike(target: JSValue): Int {
        ecmaAssert(target is JSObject)
        return toLength(target.get("length").also {
            checkError() ?: return 0
        }).asInt
    }

    @JSThrows
    @JvmStatic @ECMAImpl("Invoke", "7.3.20")
    fun invoke(value: JSValue, property: JSValue, arguments: JSArguments = emptyList()): JSValue {
        val func = getV(value, property)
        checkError() ?: return JSValue.INVALID_VALUE
        return call(func, value, arguments)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("OrdinaryHasInstance", "7.3.21")
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
    @JvmStatic @ECMAImpl("EnumerableOwnPropertyNames", "7.3.23")
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
                checkError() ?: return emptyList()
                if (kind == JSObject.PropertyKind.Value) {
                    properties.add(value)
                } else {
                    TODO("Create an entry array")
                }
            }
        }
        return properties
    }

    @JvmStatic @ECMAImpl("GetIdentifierReference", "8.1.2.1")
    fun getIdentifierReference(env: EnvRecord?, name: String, isStrict: Boolean): JSReference {
        return when {
            env == null -> JSReference(JSUndefined, PropertyKey(name), isStrict)
            env.hasBinding(name) -> JSReference(env, PropertyKey(name), isStrict)
            else -> getIdentifierReference(env.outerEnv, name, isStrict)
        }
    }

    @JvmStatic @ECMAImpl("GetActiveScriptOrModule", "8.3.1")
    fun getActiveScriptOrModule() {
        TODO()
    }

    @JvmStatic @ECMAImpl("ResolveBinding", "8.3.2")
    fun resolveBinding(name: String, env: EnvRecord?): JSReference {
        val actualEnv = env ?: Agent.runningContext.lexicalEnv!!
        // TODO: Strict mode checking
        return getIdentifierReference(actualEnv, name, false)
    }

    @JvmStatic @ECMAImpl("GetThisEnvironment", "8.3.3")
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
    @JvmStatic @ECMAImpl("ResolveThisBinding", "8.3.4")
    fun resolveThisBinding(): JSValue {
        return when (val env = getThisEnvironment()) {
            is FunctionEnvRecord -> env.getThisBinding()
            is GlobalEnvRecord -> env.getThisBinding()
            // is ModuleEnvRecord -> env.getThisBinding()
            else -> unreachable()
        }
    }

    @JvmStatic @ECMAImpl("GetGlobalObject", "8.3.6")
    fun getGlobalObject(): JSObject {
        return Agent.runningContext.realm.globalObject
    }

    @JSThrows
    @JvmStatic @ECMAImpl("OrdinaryCreateFromConstructor", "9.1.13")
    fun ordinaryCreateFromConstructor(constructor: JSValue, intrinsicDefaultProto: JSObject): JSObject {
        val proto = getPrototypeFromConstructor(constructor, intrinsicDefaultProto)
        checkError() ?: return JSObject.INVALID_OBJECT
        return JSObject.create((constructor as JSObject).realm, proto)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("GetPrototypeFromConstructor", "9.1.14")
    fun getPrototypeFromConstructor(constructor: JSValue, intrinsicDefaultProto: JSObject): JSObject {
        ecmaAssert(isCallable(constructor))
        val proto = (constructor as JSObject).get("prototype")
        checkError() ?: return JSObject.INVALID_OBJECT
        if (proto is JSObject)
            return proto
        return intrinsicDefaultProto
    }

    @JvmStatic @ECMAImpl("PrepareForOrdinaryCall", "9.2.1.1")
    fun prepareForOrdinaryCall(function: JSScriptFunction, newTarget: JSValue): ExecutionContext {
        ecmaAssert(newTarget is JSUndefined || newTarget is JSObject)
        val callerContext = Agent.runningContext
        val calleeContext = ExecutionContext(
            callerContext.agent,
            function.realm,
            function,
        )
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
    @JvmStatic @ECMAImpl("OrdinaryCallBindThis", "9.2.1.2")
    fun ordinaryCallBindThis(function: JSScriptFunction, calleeContext: ExecutionContext, thisArgument: JSValue): JSValue {
        if (function.thisMode == JSFunction.ThisMode.Lexical)
            return JSUndefined
        val thisValue = if (function.thisMode == JSFunction.ThisMode.Strict) {
            thisArgument
        } else if (thisArgument == JSUndefined || thisArgument == JSNull) {
            function.realm.globalEnv!!.globalThis
        } else {
            toObject(thisArgument).also {
                checkError() ?: return JSValue.INVALID_VALUE
            }
        }

        val localEnv = calleeContext.lexicalEnv
        ecmaAssert(localEnv is FunctionEnvRecord)
        return localEnv.bindThisValue(thisValue)
    }

    @JSThrows
    @JvmStatic @JvmOverloads @ECMAImpl("ArrayCreate", "9.4.2.2")
    fun arrayCreate(length: Int, proto: JSObject? = Agent.runningContext.realm.arrayProto): JSValue {
        if (length >= MAX_32BIT_INT - 1) {
            throwError<JSRangeErrorObject>("array length $length is too large")
            return JSValue.INVALID_VALUE
        }
        val array = JSArray.create(Agent.runningContext.realm)
        array.defineOwnProperty("length", JSNumber(length), Descriptor.WRITABLE)
        return array
    }

    @JSThrows
    @JvmStatic @ECMAImpl("EvaluatePropertyAccessWithExpressionKey", "12.3.3")
    fun evaluatePropertyAccessWithExpressionKey(baseValue: JSValue, property: JSValue, isStrict: Boolean): JSValue {
        val propertyValue = getValue(property)
        checkError() ?: return JSValue.INVALID_VALUE
        val bv = requireObjectCoercible(baseValue)
        checkError() ?: return JSValue.INVALID_VALUE
        val propertyKey = toPropertyKey(propertyValue)
        checkError() ?: return JSValue.INVALID_VALUE
        return JSReference(bv, propertyKey, isStrict)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("EvaluatePropertyAccessWithIdentifierKey", "12.3.4")
    fun evaluatePropertyAccessWithIdentifierKey(baseValue: JSValue, property: String, isStrict: Boolean): JSValue {
        val bv = requireObjectCoercible(baseValue)
        checkError() ?: return JSValue.INVALID_VALUE
        return JSReference(bv, PropertyKey(property), isStrict)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("EvaluateNew", "12.3.5.1.1")
    fun evaluateNew(target: JSValue, arguments: Array<JSValue>): JSValue {
        val constructor = getValue(target)
        if (!isConstructor(constructor)) {
            throwError<JSTypeErrorObject>("cannot construct value ${toPrintableString(target)}")
            return JSValue.INVALID_VALUE
        }
        return construct(constructor, arguments.toList())
    }

    @JSThrows
    @JvmStatic @ECMAImpl("EvaluateCall", "12.3.6.2")
    fun evaluateCall(target: JSValue, reference: JSValue, arguments: Array<JSValue>, tailPosition: Boolean): JSValue {
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
        }
        if (tailPosition)
            TODO()
        return call(target as JSFunction, thisValue, arguments.toList())
    }

    @JSThrows
    @JvmStatic @ECMAImpl("The delete Operator", "12.5.3")
    fun deleteOperator(value: JSValue): JSValue {
        if (value !is JSReference)
            return JSTrue
        if (value.isUnresolvableReference) {
            ecmaAssert(!value.isStrict)
            return JSTrue
        }
        if (value.isPropertyReference) {
            if (value.isSuperReference)
                TODO()
            expect(value.baseValue is JSValue)
            val baseObj = toObject(value.baseValue)
            val deleteStatus = baseObj.delete(value.name)
            if (!deleteStatus && value.isStrict)
                TODO()
            return deleteStatus.toValue()
        } else {
            ecmaAssert(value.baseValue is EnvRecord)
            expect(value.name.isString)
            return value.baseValue.deleteBinding(value.name.asString).toValue()
        }
    }

    @JSThrows
    @JvmStatic @ECMAImpl("The typeof Operator", "12.5.5")
    fun typeofOperator(value: JSValue): JSValue {
        if (value is JSReference) {
            if (value.isUnresolvableReference)
                return "undefined".toValue()
        }
        val v = getValue(value)
        checkError() ?: return JSValue.INVALID_VALUE
        return when (v) {
            JSUndefined -> "undefined"
            JSNull -> "object"
            is JSBoolean -> "boolean"
            is JSNumber -> "number"
            is JSString -> "string"
            is JSSymbol -> "symbol"
            is JSBigInt -> "bigint"
            is JSFunction -> "function"
            is JSObject -> "object"
            else -> unreachable()
        }.toValue()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("InstanceofOperator", "12.10.4")
    fun instanceofOperator(target: JSValue, ctor: JSValue): JSValue {
        if (ctor !is JSObject) {
            throwError<JSTypeErrorObject>("right-hand side of 'instanceof' operator must be an object")
            return JSValue.INVALID_VALUE
        }

        val instOfHandler = getMethod(target, Agent.runningContext.realm.`@@hasInstance`)
        if (instOfHandler !is JSUndefined) {
            val temp = call(instOfHandler, ctor, listOf(target))
            checkError() ?: return JSValue.INVALID_VALUE
            return toBoolean(temp)
        }

        if (!isCallable(ctor)) {
            throwError<JSTypeErrorObject>("right-hand side of 'instanceof' operator must be callable")
            return JSValue.INVALID_VALUE
        }

        return ordinaryHasInstance(ctor as JSFunction, target)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("ApplyStringOrNumericBinaryOperator", "12.15.5")
    fun applyStringOrNumericBinaryOperator(lhs: JSValue, rhs: JSValue, op: String): JSValue {
        if (op == "+") {
            val lprim = toPrimitive(lhs)
            val rprim = toPrimitive(rhs)
            if (lprim.isString || rprim.isString) {
                val lstr = toString(lprim)
                checkError() ?: return JSValue.INVALID_VALUE
                val rstr = toString(rprim)
                checkError() ?: return JSValue.INVALID_VALUE
                return JSString(lstr.string + rstr.string)

            }
        }
        checkError() ?: return JSValue.INVALID_VALUE

        val lnum = toNumeric(lhs)
        checkError() ?: return JSValue.INVALID_VALUE
        val rnum = toNumeric(rhs)
        checkError() ?: return JSValue.INVALID_VALUE
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
