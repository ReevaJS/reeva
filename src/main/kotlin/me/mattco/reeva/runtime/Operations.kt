@file:Suppress("unused")

package me.mattco.reeva.runtime

import me.mattco.reeva.ast.ASTNode
import me.mattco.reeva.ast.FormalParametersNode
import me.mattco.reeva.compiler.JSScriptFunction
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.FunctionEnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.core.tasks.Microtask
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.arrays.JSArrayObject
import me.mattco.reeva.runtime.builtins.JSMappedArgumentsObject
import me.mattco.reeva.runtime.builtins.JSProxyObject
import me.mattco.reeva.runtime.builtins.JSUnmappedArgumentsObject
import me.mattco.reeva.runtime.builtins.promises.JSCapabilitiesExecutor
import me.mattco.reeva.runtime.builtins.promises.JSPromiseObject
import me.mattco.reeva.runtime.builtins.promises.JSRejectFunction
import me.mattco.reeva.runtime.builtins.promises.JSResolveFunction
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.*
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
    const val MAX_SAFE_INTEGER: Long = (2L shl 52) - 1L
    const val MAX_32BIT_INT = 2L shl 31
    const val MAX_31BIT_INT = 2L shl 30

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
        if (reference.isUnresolvableReference)
            throwReferenceError("unknown reference '${reference.name}'")
        var base = reference.baseValue
        if (reference.isPropertyReference) {
            if (reference.hasPrimitiveBase) {
                expect(base != JSUndefined && base != JSNull)
                base = toObject(base as JSValue)
            }
            val value = (base as JSObject).get(reference.name, reference.getThisValue())
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
        if (reference !is JSReference)
            throwReferenceError("cannot assign value to ${toPrintableString(value)}")
        var base = reference.baseValue
        if (reference.isUnresolvableReference) {
            if (reference.isStrict)
                throwReferenceError("cannot resolve identifier ${reference.name}")
            Agent.runningContext.realm.globalObject.set(reference.name, value)
        } else if (reference.isPropertyReference) {
            if (reference.hasPrimitiveBase) {
                ecmaAssert(base != JSUndefined && base != JSNull)
                base = toObject(base as JSValue)
            }
            val succeeded = (base as JSObject).set(reference.name, value, reference.getThisValue())
            if (!succeeded && reference.isStrict)
                throwTypeError("TODO: Error message")
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
        if (exoticToPrim != JSUndefined) {
            val hint = when (type) {
                ToPrimitiveHint.AsDefault, null -> "default"
                ToPrimitiveHint.AsString -> "string"
                ToPrimitiveHint.AsNumber -> "number"
            }.toValue()
            val result = call(exoticToPrim, value, listOf(hint))
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
            if (isCallable(method)) {
                val result = call(method, value)
                if (result !is JSObject)
                    return result
            }
        }
        throwTypeError("cannot convert ${toPrintableString(value)} to primitive value")
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
            // TODO: spec-compliant string printing
            is JSString -> if ('.' in value.string) {
                try {
                    java.lang.Double.parseDouble(value.string).toValue()
                } catch (e: NumberFormatException) {
                    JSNumber.NaN
                }
            } else try {
                Integer.parseInt(value.string).toValue()
            } catch (e: NumberFormatException) {
                JSNumber.NaN
            }
            is JSSymbol, is JSBigInt -> throwTypeError("cannot convert ${value.type} to Number")
            is JSObject -> toPrimitive(value, ToPrimitiveHint.AsNumber)
            else -> unreachable()
        }
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.1.5")
    fun toIntegerOrInfinity(value: JSValue): JSValue {
        val number = toNumber(value)
        if (number.isNaN || number.isZero)
            return 0.toValue()
        if (number.isInfinite)
            return number
        return abs(number.asLong).let {
            if (number.asLong < 0) it * -1 else it
        }.toValue()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.1.6")
    fun toInt32(value: JSValue): JSValue {
        val number = toNumber(value)
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
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber.ZERO

        var int = floor(abs(number.asDouble)).toInt()
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
            is JSSymbol -> throwTypeError("cannot convert Symbol to string")
            is JSObject -> toString(toPrimitive(value, ToPrimitiveHint.AsString)).string
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
            is JSUndefined, JSNull -> throwTypeError("cannot convert ${value.type} to Object")
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
        val number = len.asLong
        if (number < 0)
            return 0.toValue()
        return min(number, MAX_SAFE_INTEGER).toValue()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.2.1")
    fun requireObjectCoercible(value: JSValue): JSValue {
        if (value is JSUndefined || value is JSNull)
            throwTypeError("cannot convert ${value.type} to Object")
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
            py = toPrimitive(rhs, ToPrimitiveHint.AsNumber)
        } else {
            py = toPrimitive(rhs, ToPrimitiveHint.AsNumber)
            px = toPrimitive(lhs, ToPrimitiveHint.AsNumber)
        }

        if (px is JSString && py is JSString)
            TODO()

        if (px is JSBigInt && py is JSString)
            TODO()
        if (px is JSString && py is JSBigInt)
            TODO()

        val nx = toNumeric(px)
        val ny = toNumeric(py)

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
        return obj.get(toPropertyKey(property))
    }

    @JvmStatic @ECMAImpl("7.3.4")
    fun set(obj: JSObject, property: PropertyKey, value: JSValue, throws: Boolean): Boolean {
        val success = obj.set(property, value)
        if (!success && throws)
            throwTypeError("TODO: message")
        return success
    }

    @JSThrows
    fun createDataProperty(target: JSValue, property: JSValue, value: JSValue): Boolean {
        return createDataProperty(target, toPropertyKey(property), value)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.5")
    fun createDataProperty(target: JSValue, property: PropertyKey, value: JSValue): Boolean {
        ecmaAssert(target is JSObject)
        return target.defineOwnProperty(property, Descriptor(value, Descriptor.defaultAttributes))
    }

    @JSThrows
    fun createDataPropertyOrThrow(target: JSValue, property: JSValue, value: JSValue): Boolean {
        return createDataPropertyOrThrow(target, toPropertyKey(property), value)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.7")
    fun createDataPropertyOrThrow(target: JSValue, property: PropertyKey, value: JSValue): Boolean {
        if (!createDataProperty(target, property, value))
            throwTypeError("unable to create property \"$property\" on object ${toPrintableString(target)}")
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
        if (!target.defineOwnProperty(property, descriptor))
            throwTypeError("unable to define property \"$property\" on object ${toPrintableString(target)}")
        return true
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.9")
    fun deletePropertyOrThrow(target: JSValue, property: PropertyKey): Boolean {
        ecmaAssert(target is JSObject)
        if (!target.delete(property))
            throwTypeError("unable to delete property \"$property\" on object ${toPrintableString(target)}")
        return true
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.10")
    fun getMethod(value: JSValue, key: JSValue): JSValue {
        val func = getV(value, key)
        if (func is JSUndefined || func is JSNull)
            return JSUndefined
        if (!isCallable(func))
            throwTypeError("cannot call value ${toPrintableString(func)}")
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
        return (desc != JSUndefined).toValue()
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.13")
    fun call(function: JSValue, thisValue: JSValue, arguments: List<JSValue> = emptyList()): JSValue {
        if (!isCallable(function))
            throwTypeError("cannot call value ${toPrintableString(function)}")
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
        return toLength(target.get("length")).asLong.let {
            if (it > Int.MAX_VALUE)
                TODO("Better Long support")
            it.toInt()
        }
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

        if (obj !is JSObject)
            throwTypeError("TODO: message")

        val length = lengthOfArrayLike(obj)
        val list = mutableListOf<JSValue>()

        for (i in 0 until length) {
            val next = obj.get(i)
            if (next.type !in elementTypes)
                throwTypeError("TODO: message")
            list.add(next)
        }

        return list
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.3.20")
    fun invoke(value: JSValue, property: JSValue, arguments: JSArguments = emptyList()): JSValue {
        return call(getV(value, property), value, arguments)
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
        if (ctorProto !is JSObject)
            throwTypeError("TODO: message")

        var obj = target
        while (true) {
            obj = (obj as JSObject).getPrototype()
            if (obj == JSNull)
                return JSFalse
            if (ctorProto.sameValue(obj))
                return JSTrue
        }
    }

    @JvmStatic @ECMAImpl("7.3.22")
    fun speciesConstructor(obj: JSObject, defaultCtor: JSFunction): JSFunction {
        val ctor = obj.get("constructor")
        if (ctor == JSUndefined)
            return defaultCtor
        if (ctor !is JSObject)
            throwTypeError("TODO: message")

        val species = ctor.get(Realm.`@@species`)
        if (species.isNullish)
            return defaultCtor

        if (isConstructor(species))
            return species as JSFunction

        throwTypeError("TODO: message")
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
    fun getIterator(obj: JSValue, hint: IteratorHint? = IteratorHint.Sync, _method: JSFunction? = null): IteratorRecord {
        if (hint == IteratorHint.Async)
            TODO()
        val method = _method ?: getMethod(obj, Realm.`@@iterator`)
        if (method == JSUndefined)
            throwTypeError("${toPrintableString(obj)} is not iterable")
        val iterator = call(method, obj)
        if (iterator !is JSObject)
            throwTypeError("iterator must be an object")
        val nextMethod = getV(iterator, "next".toValue())
        return IteratorRecord(iterator, nextMethod, false)
    }

    data class IteratorRecord(val iterator: JSObject, val nextMethod: JSValue, var done: Boolean)

    @JSThrows
    @JvmStatic @ECMAImpl("7.4.2")
    fun iteratorNext(record: IteratorRecord, value: JSValue? = null): JSObject {
        val result = if (value == null) {
            call(record.nextMethod, record.iterator)
        } else {
            call(record.nextMethod, record.iterator, listOf(value))
        }
        if (result !is JSObject)
            throwTypeError("iterator result must be an object")
        return result
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.4.3")
    fun iteratorComplete(result: JSValue): JSBoolean {
        ecmaAssert(result is JSObject)
        return toBoolean(result.get("done"))
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
    fun iteratorClose(record: IteratorRecord, value: JSValue): JSValue {
        val method = record.iterator.get("return")
        if (method == JSUndefined)
            return value
        return call(method, record.iterator)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("7.4.8")
    fun createIterResultObject(value: JSValue, done: Boolean): JSValue {
        val obj = JSObject.create(Agent.runningContext.realm)
        createDataPropertyOrThrow(obj, "value".toValue(), value)
        createDataPropertyOrThrow(obj, "done".toValue(), done.toValue())
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

    @ECMAImpl("8.3.5")
    fun getNewTarget(): JSValue {
        val env = getThisEnvironment()
        ecmaAssert(env is FunctionEnvRecord)
        return env.newTarget
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
    fun validateAndApplyPropertyDescriptor(
        target: JSObject?,
        property: PropertyKey?,
        extensible: Boolean,
        newDesc: Descriptor,
        currentDesc: Descriptor?
    ): Boolean {
        if (currentDesc == null) {
            if (!extensible)
                return false
            if (newDesc.isDataDescriptor || newDesc.isGenericDescriptor) {
                if (!newDesc.hasConfigurable)
                    newDesc.setConfigurable(false)
                if (!newDesc.hasEnumerable)
                    newDesc.setEnumerable(false)
                if (!newDesc.hasWritable)
                    newDesc.setWritable(false)
                if (newDesc.getRawValue() == JSEmpty)
                    newDesc.setRawValue(JSUndefined)
            } else {
                if (!newDesc.hasEnumerable)
                    newDesc.setEnumerable(false)
                if (!newDesc.hasWritable)
                    newDesc.setWritable(false)
            }
            target?.internalSet(property!!, newDesc)
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
                target?.internalSet(
                    property!!, Descriptor(
                        JSUndefined,
                        currentDesc.attributes and (Descriptor.WRITABLE or Descriptor.HAS_WRITABLE).inv(),
                        newDesc.getter,
                        newDesc.setter,
                    )
                )
            } else {
                target?.internalSet(
                    property!!, Descriptor(
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
            if (newDesc.hasSetter && (!currentDesc.hasSetter || !newSetter.sameValue(currentSetter)))
                return false
            val currentGetter = currentDesc.setter
            val newGetter = newDesc.setter
            if (newDesc.hasGetter && (!currentDesc.hasGetter || !newGetter.sameValue(currentGetter)))
                return false
            return true
        }

        if (target != null) {
            if (newDesc.isDataDescriptor && newDesc.getRawValue() != JSEmpty)
                currentDesc.setActualValue(target, newDesc.getActualValue(target))

            if (newDesc.hasGetter)
                currentDesc.getter = newDesc.getter
            if (newDesc.hasSetter)
                currentDesc.setter = newDesc.setter

            if (newDesc.hasConfigurable)
                currentDesc.setConfigurable(newDesc.isConfigurable)
            if (newDesc.hasEnumerable)
                currentDesc.setEnumerable(newDesc.isEnumerable)
            if (newDesc.hasWritable)
                currentDesc.setWritable(newDesc.isWritable)

            target.internalSet(property!!, currentDesc)
        }

        return true
    }

    @JSThrows
    @JvmStatic @ECMAImpl("9.1.13")
    fun ordinaryCreateFromConstructor(constructor: JSValue, intrinsicDefaultProto: JSObject): JSObject {
        val proto = getPrototypeFromConstructor(constructor, intrinsicDefaultProto)
        return JSObject.create((constructor as JSObject).realm, proto)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("9.1.14")
    fun getPrototypeFromConstructor(constructor: JSValue, intrinsicDefaultProto: JSObject): JSObject {
        ecmaAssert(isCallable(constructor))
        val proto = (constructor as JSObject).get("prototype")
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
            function.realm.globalEnv.globalThis
        } else toObject(thisArgument)

        val localEnv = calleeContext.lexicalEnv
        ecmaAssert(localEnv is FunctionEnvRecord)
        return localEnv.bindThisValue(thisValue)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("9.2.5")
    fun makeConstructor(function: JSFunction, writablePrototype: Boolean = true, prototype: JSObject? = null) {
        ecmaAssert(hasOwnProperty(function, "prototype".key()) == JSFalse)
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
            proto
        }
        var attrs = Descriptor.HAS_BASIC
        if (writablePrototype)
            attrs = attrs or Descriptor.WRITABLE
        definePropertyOrThrow(function, "prototype".key(), Descriptor(realProto, attrs))
    }

    @JSThrows
    @JvmStatic @ECMAImpl("9.2.7")
    fun makeMethod(function: JSFunction, homeObject: JSObject): JSValue {
        function.homeObject = homeObject
        return JSUndefined
    }

    @ECMAImpl("9.2.8")
    fun setFunctionName(function: JSFunction, name: PropertyKey, prefix: String? = null): Boolean {
        ecmaAssert(function.isExtensible())
        val nameString = when {
            name.isSymbol -> name.asSymbol.description.let {
                if (it == null) "" else "[${name.asSymbol.description}]"
            }
            name.isInt -> name.asInt.toString()
            name.isDouble -> name.asDouble.toString()
            else -> name.asString
        }.let {
            if (prefix != null) {
                "$prefix $it"
            } else it
        }
        return Operations.definePropertyOrThrow(function, "name".toValue(), Descriptor(nameString.toValue(), Descriptor.CONFIGURABLE))
    }

    @JSThrows
    @JvmStatic @JvmOverloads @ECMAImpl("9.4.2.2")
    fun arrayCreate(length: Int, proto: JSObject? = Agent.runningContext.realm.arrayProto): JSObject {
        if (length >= MAX_32BIT_INT - 1)
            throwRangeError("array length $length is too large")
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
        if (!isConstructor(ctor))
            throwTypeError("TODO: message")
        return construct(ctor, listOf(length.toValue()))
    }

    @ECMAImpl("9.4.4.6")
    fun createUnmappedArgumentsObject(arguments: JSArguments): JSValue {
        var realm = Agent.runningContext.realm
        val obj = JSUnmappedArgumentsObject.create(realm)
        definePropertyOrThrow(obj, "length".key(), Descriptor(arguments.size.toValue(), Descriptor.CONFIGURABLE or Descriptor.WRITABLE))
        arguments.forEachIndexed { index, value ->
            createDataPropertyOrThrow(obj, index.key(), value)
        }
        definePropertyOrThrow(
            obj,
            Realm.`@@iterator`.key(),
            Descriptor(realm.arrayProto.get("values"), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        )

        val typeErrorThrow = object : JSNativeFunction(realm, "", 0) {
            override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
                throwTypeError("TODO: message")
            }

            override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
                throw IllegalStateException("Unexpected construction of %ThrowTypeError%")
            }
        }
        definePropertyOrThrow(
            obj,
            "callee".key(),
            Descriptor(JSEmpty, Descriptor.HAS_CONFIGURABLE or Descriptor.HAS_ENUMERABLE, getter = typeErrorThrow, setter = typeErrorThrow)
        )

        return obj
    }

    @ECMAImpl("9.4.4.7")
    fun createMappedArgumentsObject(
        function: JSFunction,
        formals: FormalParametersNode,
        arguments: JSArguments,
        env: EnvRecord
    ): JSMappedArgumentsObject {
        ecmaAssert(formals.restParameter == null)
        ecmaAssert(formals.functionParameters.parameters.all { it.bindingElement.binding.initializer == null })

        val realm = Agent.runningContext.realm
        val obj = JSMappedArgumentsObject.create(realm)
        val map = JSObject.create(realm)
        obj.parameterMap = map

        val parameterNames = formals.boundNames()
        arguments.forEachIndexed { index, arg ->
            createDataPropertyOrThrow(obj, index.key(), arg)
        }

        definePropertyOrThrow(obj, "length".key(), Descriptor(arguments.size.toValue(), Descriptor.CONFIGURABLE or Descriptor.WRITABLE))

        val mappedNames = mutableListOf<String>()
        parameterNames.reversed().forEachIndexed { index, name ->
            if (name !in mappedNames) {
                mappedNames.add(name)
                if (index < arguments.size) {
                    val getter = makeArgGetter(name, env)
                    val setter = makeArgSetter(name, env)
                    map.defineOwnProperty(index.key(), Descriptor(JSEmpty, Descriptor.HAS_ENUMERABLE or Descriptor.CONFIGURABLE, getter, setter))
                }
            }
        }

        definePropertyOrThrow(obj, Realm.`@@iterator`.key(), Descriptor(
            realm.arrayProto.get("values"),
            Descriptor.CONFIGURABLE or Descriptor.WRITABLE
        ))
        definePropertyOrThrow(obj, "callee".key(), Descriptor(function, Descriptor.CONFIGURABLE or Descriptor.WRITABLE))

        return obj
    }

    @ECMAImpl("9.4.4.7.1")
    fun makeArgGetter(name: String, env: EnvRecord): JSValue {
        return object : JSNativeFunction(Agent.runningContext.realm, name, 0) {
            override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
                val function = Agent.runningContext.function
                expect(function != null)
                return env.getBindingValue(name, false)
            }

            override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
                throw IllegalStateException("Unexpected construction of ArgGetter function")
            }
        }
    }

    @ECMAImpl("9.4.4.7.2")
    fun makeArgSetter(name: String, env: EnvRecord): JSValue {
        return object : JSNativeFunction(Agent.runningContext.realm, name, 0) {
            override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
                val function = Agent.runningContext.function
                expect(function != null)
                env.setMutableBinding(name, arguments.argument(0), false)
                return JSEmpty
            }

            override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
                throw IllegalStateException("Unexpected construction of ArgSetter function")
            }
        }
    }

    fun utf16SurrogatePairToCodePoint(leading: Int, trailing: Int): Int {
        return (leading - 0xd800) * 0x400 + (trailing - 0xdc00) + 0x10000
    }

    @JSThrows
    @JvmStatic @ECMAImpl("10.1.4")
    fun codePointAt(string: String, position: Int): CodepointRecord {
        val size = string.length
        ecmaAssert(position in 0 until size)
        val first = string[position]
        if (!first.isHighSurrogate() && !first.isLowSurrogate())
            return CodepointRecord(first.toInt(), 1, false)
        if (first.isLowSurrogate() || position + 1 == size)
            return CodepointRecord(first.toInt(), 1, true)
        val second = string[position + 1]
        if (!second.isLowSurrogate())
            return CodepointRecord(first.toInt(), 1, true)
        return CodepointRecord(utf16SurrogatePairToCodePoint(first.toInt(), second.toInt()), 2, false)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("10.1.5")
    fun stringToCodePoints(string: String): List<CodepointRecord> {
        val codepoints = mutableListOf<CodepointRecord>()
        var position = 0
        while (position < string.length) {
            val record = codePointAt(string, position)
            codepoints.add(record)
            position += record.codeUnitCount
        }
        return codepoints
    }

    data class CodepointRecord(val codepoint: Int, val codeUnitCount: Int, val isUnpairedSurrogate: Boolean)

    @JSThrows
    @JvmStatic @ECMAImpl("12.3.3")
    fun evaluatePropertyAccessWithExpressionKey(baseValue: JSValue, property: JSValue, isStrict: Boolean): JSValue {
        val propertyValue = getValue(property)
        val bv = requireObjectCoercible(baseValue)
        val propertyKey = toPropertyKey(propertyValue)
        return JSReference(bv, propertyKey, isStrict)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("12.3.4")
    fun evaluatePropertyAccessWithIdentifierKey(baseValue: JSValue, property: String, isStrict: Boolean): JSValue {
        val bv = requireObjectCoercible(baseValue)
        return JSReference(bv, PropertyKey(property), isStrict)
    }

    @JSThrows
    @JvmStatic @ECMAImpl("12.3.5.1.1")
    fun evaluateNew(target: JSValue, arguments: Array<JSValue>): JSValue {
        val constructor = getValue(target)
        if (!isConstructor(constructor))
            throwTypeError("cannot construct value ${toPrintableString(target)}")
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

        if (!isCallable(target))
            throwTypeError("object of type ${target.type} is not callable")
        if (tailPosition)
            TODO()
        return call(target, thisValue, arguments.toList())
    }

    @ECMAImpl("12.3.7.2")
    fun getSuperConstructor(): JSValue {
        val env = getThisEnvironment()
        ecmaAssert(env is FunctionEnvRecord)
        val activeFunction = env.function
        return activeFunction.getPrototype()
    }

    @ECMAImpl("12.3.7.3")
    fun makeSuperPropertyReference(thisValue: JSValue, key: PropertyKey, isStrict: Boolean): JSReference {
        val env = getThisEnvironment()
        ecmaAssert(env.hasSuperBinding())
        val baseValue = (env as FunctionEnvRecord).getSuperBase()
        requireObjectCoercible(baseValue)
        return JSSuperReference(baseValue, key, isStrict, thisValue)
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
            expect(value.name is PropertyKey)
            val baseObj = toObject(value.baseValue)
            val deleteStatus = baseObj.delete(value.name)
            if (!deleteStatus && value.isStrict)
                TODO()
            deleteStatus.toValue()
        } else {
            ecmaAssert(value.baseValue is EnvRecord)
            expect(value.name is PropertyKey)
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
        if (ctor !is JSObject)
            throwTypeError("right-hand side of 'instanceof' operator must be an object")

        val instOfHandler = getMethod(target, Realm.`@@hasInstance`)
        if (instOfHandler !is JSUndefined) {
            val temp = call(instOfHandler, ctor, listOf(target))
            return toBoolean(temp)
        }

        if (!isCallable(ctor))
            throwTypeError("right-hand side of 'instanceof' operator must be callable")

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
                val rstr = toString(rprim)
                return JSString(lstr.string + rstr.string)

            }
        }

        val lnum = toNumeric(lhs)
        val rnum = toNumeric(rhs)
        if (lnum.type != rnum.type)
            throwTypeError("cannot apply operator $op to type ${lnum.type} and ${rnum.type}")

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

    @ECMAImpl("14.1.12")
    fun isAnonymousFunctionDefinition(node: ASTNode): Boolean {
        if (!node.isFunctionDefinition())
            return false
        return !node.hasName()
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
                iteratorClose(record, JSEmpty)
                throwTypeError("TODO: message")
            }
            val key = try {
                nextItem.get(0)
            } catch (e: Throwable) {
                iteratorClose(record, JSEmpty)
                throw e
            }
            val value = try {
                nextItem.get(1)
            } catch (e: Throwable) {
                iteratorClose(record, JSEmpty)
                throw e
            }
            try {
                call(adder, target, listOf(key, value))
            } catch (e: Throwable) {
                iteratorClose(record, JSEmpty)
                throw e
            }
        }
    }

    @ECMAImpl("26.6.1.3")
    fun createResolvingFunctions(promise: JSPromiseObject): Pair<JSFunction, JSFunction> {
        val resolvedStatus = Wrapper(false)
        val resolve = JSResolveFunction.create(promise, resolvedStatus, promise.realm)
        val reject = JSRejectFunction.create(promise, resolvedStatus, promise.realm)
        return resolve to reject
    }

    @ECMAImpl("26.6.1.4")
    fun fulfillPromise(promise: JSPromiseObject, reason: JSValue): JSValue {
        ecmaAssert(promise.state == PromiseState.Pending)
        val reactions = promise.fulfillReactions.toList()
        promise.result = reason
        promise.fulfillReactions.clear()
        promise.rejectReactions.clear()
        promise.state = PromiseState.Fulfilled

        return triggerPromiseReactions(reactions, reason)
    }

    @ECMAImpl("26.6.1.5")
    fun newPromiseCapability(ctor: JSValue): PromiseCapability {
        if (!isConstructor(ctor))
            throwTypeError("TODO: message")
        val capability = PromiseCapability(JSEmpty, null, null)
        val executor = JSCapabilitiesExecutor.create((ctor as JSObject).realm, capability)
        val promise = construct(ctor, listOf(executor))
        capability.promise = promise
        return capability
    }

    @ECMAImpl("26.6.1.6")
    fun isPromise(value: JSValue): Boolean {
        if (value is JSPromiseObject)
            return true
        if (value is JSProxyObject)
            return isPromise(value.target)
        return false
    }

    @ECMAImpl("26.6.1.7")
    internal fun rejectPromise(promise: JSPromiseObject, reason: JSValue): JSValue {
        ecmaAssert(promise.state == PromiseState.Pending)
        val reactions = promise.rejectReactions.toList()
        promise.result = reason
        promise.fulfillReactions.clear()
        promise.rejectReactions.clear()
        promise.state = PromiseState.Rejected
        if (!promise.isHandled) {
            hostPromiseRejectionTracker(promise, "reject")
        }

        return triggerPromiseReactions(reactions, reason)
    }

    @ECMAImpl("26.6.1.8")
    fun triggerPromiseReactions(reactions: List<PromiseReaction>, argument: JSValue): JSValue {
        reactions.forEach { reaction ->
            val job = newPromiseReactionJob(reaction, argument)
            hostEnqueuePromiseJob(job.job, job.realm)
        }
        return JSUndefined
    }

    data class PromiseReaction(
        val capability: PromiseCapability?,
        val type: Type,
        val handler: JSFunction?,
    ) {
        enum class Type {
            Fulfill,
            Reject,
        }
    }

    data class Wrapper<T>(var value: T)

    @ECMAImpl("26.6.1.9")
    fun hostPromiseRejectionTracker(promise: JSPromiseObject, operation: String) {
        if (operation == "reject") {
            val unhandledRejectionTask = object : Microtask() {
                override fun execute(): JSValue {
                    // If promise does not have any handlers by the time this microtask is ran, it
                    // will not have any handlers, and we can print a warning
                    if (!promise.isHandled)
                        println("\u001b[31mUnhandled promise rejection: ${toString(promise.result)}\u001B[0m")
                    return JSEmpty
                }
            }
            Agent.activeAgent.submitMicrotask(unhandledRejectionTask)
        }
    }

    @ECMAImpl("26.6.2.1")
    fun newPromiseReactionJob(reaction: PromiseReaction, argument: JSValue): PromiseReactionJob {
        val task = object : Microtask() {
            override fun execute(): JSValue {
                val handlerResult: Any = if (reaction.handler == null) {
                    if (reaction.type == PromiseReaction.Type.Fulfill) {
                        argument
                    } else {
                        ThrowException(argument)
                    }
                } else try {
                    call(reaction.handler, JSUndefined, listOf(argument))
                } catch (e: ThrowException) {
                    e
                }

                if (reaction.capability == null) {
                    ecmaAssert(handlerResult !is ThrowException)
                    return JSEmpty
                }

                return if (handlerResult is ThrowException) {
                    call(reaction.capability.reject!!, JSUndefined, listOf(handlerResult.value))
                } else {
                    call(reaction.capability.resolve!!, JSUndefined, listOf(handlerResult as JSValue))
                }
            }
        }

        val handlerRealm = if (reaction.handler != null) reaction.handler.realm else null
        return PromiseReactionJob(task, handlerRealm)
    }

    data class PromiseReactionJob(val job: Microtask, val realm: Realm?)

    @ECMAImpl("26.6.2.2")
    fun newPromiseResolveThenableJob(promise: JSPromiseObject, thenable: JSValue, then: JSValue): PromiseReactionJob {
        val job = object : Microtask() {
            override fun execute(): JSValue {
                val (resolveFunction, rejectFunction) = createResolvingFunctions(promise)
                return try {
                    call(then, thenable, listOf(resolveFunction, rejectFunction))
                } catch (e: ThrowException) {
                    call(rejectFunction, JSUndefined, listOf(e.value))
                }
            }
        }

        // TODO: then is always an object?
        val thenRealm = if (then is JSObject) then.realm else Agent.runningContext.realm
        return PromiseReactionJob(job, thenRealm)
    }

    @ECMAImpl("26.6.4.1.1")
    fun getPromiseResolve(constructor: JSValue): JSValue {
        ecmaAssert(isConstructor(constructor))
        val resolve = (constructor as JSObject).get("resolve")
        if (!isCallable(resolve))
            throwTypeError("TODO: message")
        return resolve
    }

    @ECMAImpl("26.6.4.7")
    fun promiseResolve(constructor: JSObject, value: JSValue): JSValue {
        if (isPromise(value)) {
            val valueCtor = (value as JSObject).get("constructor")
            if (valueCtor.sameValue(constructor))
                return value
        }

        val capability = newPromiseCapability(constructor)
        call(capability.resolve!!, JSUndefined, listOf(value))
        return capability.promise
    }

    @ECMAImpl("26.6.5.4.1")
    fun performPromiseThen(promise: JSPromiseObject, onFulfilled: JSValue, onRejected: JSValue, resultCapability: PromiseCapability?): JSValue {
        val onFulfilledCallback = if (isCallable(onFulfilled)) {
            onFulfilled as JSFunction
        } else null
        val onRejectedCallback = if (isCallable(onRejected)) {
            onRejected as JSFunction
        } else null

        val fulfillReaction = PromiseReaction(resultCapability, PromiseReaction.Type.Fulfill, onFulfilledCallback)
        val rejectReaction = PromiseReaction(resultCapability, PromiseReaction.Type.Reject, onRejectedCallback)

        when (promise.state) {
            PromiseState.Pending -> {
                promise.fulfillReactions.add(fulfillReaction)
                promise.rejectReactions.add(rejectReaction)
            }
            PromiseState.Fulfilled -> {
                val fulfillJob = newPromiseReactionJob(fulfillReaction, promise.result)
                hostEnqueuePromiseJob(fulfillJob.job, fulfillJob.realm)
            }
            else -> {
                if (!promise.isHandled)
                    hostPromiseRejectionTracker(promise, "handle")
                val rejectJob = newPromiseReactionJob(rejectReaction, promise.result)
                hostEnqueuePromiseJob(rejectJob.job, rejectJob.realm)
            }
        }

        promise.isHandled = true

        return resultCapability?.promise ?: JSUndefined
    }

    @ECMAImpl("8.4.4")
    fun hostEnqueuePromiseJob(job: Microtask, realm: Realm?) {
        // TODO: Use realm?
        Agent.activeAgent.submitMicrotask(job)
    }

    data class PromiseCapability(
        var promise: JSValue,
        var resolve: JSFunction?,
        var reject: JSFunction?,
    )

    enum class PromiseState {
        Pending,
        Fulfilled,
        Rejected,
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
