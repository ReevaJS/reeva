package com.reevajs.reeva.runtime

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.toValue
import java.math.BigInteger
import kotlin.math.floor

abstract class JSValue {
    val type: Type
        get() = when (this) {
            is JSEmpty -> Type.Empty
            is JSUndefined -> Type.Undefined
            is JSNull -> Type.Null
            is JSBoolean -> Type.Boolean
            is JSString -> Type.String
            is JSSymbol -> Type.Symbol
            is JSNumber -> Type.Number
            is JSBigInt -> Type.BigInt
            is JSNativeProperty -> Type.NativeProperty
            is JSAccessor -> Type.Accessor
            is JSObject -> Type.Object
            else -> throw IllegalStateException("Unknown object type")
        }

    val isEmpty: Boolean get() = type == Type.Empty
    val isUndefined: Boolean get() = type == Type.Undefined
    val isNull: Boolean get() = type == Type.Null
    val isBoolean: Boolean get() = type == Type.Boolean
    val isNumber: Boolean get() = type == Type.Number
    val isBigInt: Boolean get() = type == Type.BigInt
    val isString: Boolean get() = type == Type.String
    val isSymbol: Boolean get() = type == Type.Symbol
    val isObject: Boolean get() = type == Type.Object
    val isAccessor: Boolean get() = type == Type.Accessor

    val isNullish: Boolean get() = this == JSNull || this == JSUndefined
    val isInt: Boolean get() = isNumber && !isInfinite && floor(asDouble) == asDouble &&
        asDouble in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()
    val isLong: Boolean get() = isNumber && !isInfinite && floor(asDouble) == asDouble &&
        asDouble in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()
    val isNaN: Boolean get() = isNumber && asDouble.isNaN()
    val isFinite: Boolean get() = isNumber && asDouble.isFinite()
    val isInfinite: Boolean get() = isNumber && asDouble.isInfinite()
    val isPositiveInfinity: Boolean get() = isNumber && asDouble == Double.POSITIVE_INFINITY
    val isNegativeInfinity: Boolean get() = isNumber && asDouble == Double.NEGATIVE_INFINITY
    val isZero: Boolean get() = isNumber && asDouble == 0.0
    val isPositiveZero: Boolean get() = isNumber && 1.0 / asDouble == Double.POSITIVE_INFINITY
    val isNegativeZero: Boolean get() = isNumber && 1.0 / asDouble == Double.NEGATIVE_INFINITY

    val asBoolean: Boolean
        get() {
            expect(isBoolean)
            return (this as JSBoolean).boolean
        }

    val asString: String
        get() {
            expect(isString)
            return (this as JSString).string
        }

    val asSymbol: JSSymbol
        get() {
            expect(isSymbol)
            return this as JSSymbol
        }

    val asInt: Int
        get() {
            expect(isNumber)
            return (this as JSNumber).number.toInt()
        }

    val asLong: Long
        get() {
            expect(isNumber)
            return (this as JSNumber).number.toLong()
        }

    val asDouble: Double
        get() {
            expect(isNumber)
            return (this as JSNumber).number
        }

    val asAccessor: JSNativeProperty
        get() {
            expect(isAccessor)
            return this as JSNativeProperty
        }

    @ECMAImpl("7.2.10")
    fun sameValue(other: JSValue): Boolean {
        if (type != other.type)
            return false
        if (type == Type.Number)
            return Operations.numericSameValue(this, other).boolean
        if (type == Type.BigInt)
            return Operations.bigintSameValue(this, other).boolean
        return sameValueNonNumeric(other)
    }

    @ECMAImpl("7.2.11")
    fun sameValueZero(other: JSValue): Boolean {
        if (type != other.type)
            return false
        if (type == Type.Number)
            return Operations.numericSameValueZero(this, other).boolean
        if (type == Type.BigInt)
            return Operations.bigintSameValueZero(this, other).boolean
        return sameValueNonNumeric(other)
    }

    @ECMAImpl("7.2.12")
    fun sameValueNonNumeric(other: JSValue): Boolean {
        ecmaAssert(type != Type.Number && type != Type.BigInt)
        ecmaAssert(type == other.type)
        expect(type != Type.Empty)

        return when (type) {
            Type.Undefined -> true
            Type.Null -> true
            Type.Boolean -> asBoolean == other.asBoolean
            Type.String -> asString == other.asString
            Type.Symbol, Type.Object -> this === other
            else -> TODO()
        }
    }

    fun ifEmpty(value: JSValue) = if (this == JSEmpty) value else this

    fun ifUndefined(value: JSValue) = if (this == JSUndefined) value else this

    fun ifUndefined(block: () -> JSValue) = if (this == JSUndefined) block() else this

    fun ifNull(value: JSValue) = if (this == JSUndefined) value else this

    fun ifNullish(value: JSValue) = if (this == JSUndefined || this == JSNull) value else this

    override fun toString(): String {
        return Operations.toPrintableString(this)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this)
            return true
        return other is JSValue && sameValueZero(other)
    }

    override fun hashCode(): Int {
        return when (type) {
            Type.Empty -> -658902345
            Type.Undefined -> 29358739
            Type.Null -> 843562348
            Type.Boolean -> asBoolean.hashCode()
            Type.String -> asString.hashCode()
            Type.Number -> {
                if (isNegativeZero)
                    return 0.0.hashCode()
                return asDouble.hashCode()
            }
            else -> System.identityHashCode(this)
        }
    }

    enum class Type(val typeName: kotlin.String) {
        Empty("<empty>"),
        Undefined("undefined"),
        Null("null"),
        Boolean("Boolean"),
        String("String"),
        Number("Number"),
        BigInt("BigInt"),
        Symbol("Symbol"),
        Object("Object"),
        NativeProperty("NativeProperty"),
        Accessor("Accessor");

        override fun toString() = typeName
    }

    companion object {
        val INVALID_VALUE = object : JSValue() {}

        @JvmStatic
        fun from(value: Any) = when (value) {
            is Boolean -> value.toValue()
            is String -> value.toValue()
            is Char -> value.toValue()
            is Number -> value.toValue()
            is BigInteger -> value.toValue()
            else -> throw IllegalArgumentException("Cannot convert ${this::class.simpleName} to a JSValue")
        }
    }
}

/**
 * Operations extension methods
 */

val JSValue.isCallable: Boolean get() = Operations.isCallable(this)
val JSValue.isConstructor: Boolean get() = Operations.isConstructor(this)
val JSValue.isIntegralNumber: Boolean get() = Operations.isIntegralNumber(this)
val JSValue.isPropertyKey: Boolean get() = Operations.isPropertyKey(this)
val JSValue.isRegExp: Boolean get() = Operations.isRegExp(this)

fun JSValue.toBoolean() = Operations.toBoolean(this)
fun JSValue.toNumeric() = Operations.toNumeric(this)
fun JSValue.toNumber() = Operations.toNumber(this)
fun JSValue.toJSString() = Operations.toString(this)
fun JSValue.toIntegerOrInfinity() = Operations.toIntegerOrInfinity(this)
fun JSValue.toInt32() = Operations.toInt32(this)
fun JSValue.toUint32() = Operations.toUint32(this)
fun JSValue.toUint16() = Operations.toUint16(this)
fun JSValue.toBigInt() = Operations.toBigInt(this)
fun JSValue.toObject() = Operations.toObject(this)
fun JSValue.toPropertyKey() = Operations.toPropertyKey(this)
fun JSValue.toLength() = Operations.toLength(this)
fun JSValue.toIndex() = Operations.toIndex(this)
fun JSValue.requireObjectCoercible() = Operations.requireObjectCoercible(this)
fun JSValue.lengthOfArrayLike() = Operations.lengthOfArrayLike(this)
fun JSValue.toPrimitive(hint: Operations.ToPrimitiveHint? = null) =
    Operations.toPrimitive(this, hint)

fun JSValue.exp(other: JSValue) = Operations.exp(this, other)
fun JSValue.mul(other: JSValue) = Operations.mul(this, other)
fun JSValue.div(other: JSValue) = Operations.div(this, other)
fun JSValue.mod(other: JSValue) = Operations.mod(this, other)
fun JSValue.add(other: JSValue) = Operations.add(this, other)
fun JSValue.sub(other: JSValue) = Operations.sub(this, other)
fun JSValue.shl(other: JSValue) = Operations.shl(this, other)
fun JSValue.shr(other: JSValue) = Operations.shr(this, other)
fun JSValue.ushr(other: JSValue) = Operations.ushr(this, other)
fun JSValue.and(other: JSValue) = Operations.and(this, other)
fun JSValue.xor(other: JSValue) = Operations.xor(this, other)
fun JSValue.or(other: JSValue) = Operations.or(this, other)
fun JSValue.isLessThan(other: JSValue) = Operations.isLessThan(this, other)
fun JSValue.isLessThanOrEqual(other: JSValue) = Operations.isLessThanOrEqual(this, other)
fun JSValue.isGreaterThan(other: JSValue) = Operations.isGreaterThan(this, other)
fun JSValue.isGreaterThanOrEqual(other: JSValue) = Operations.isGreaterThanOrEqual(this, other)
