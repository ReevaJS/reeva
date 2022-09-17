@file:Suppress("unused")

package com.reevajs.reeva.runtime

import com.reevajs.reeva.ast.ASTNode
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.environment.DeclarativeEnvRecord
import com.reevajs.reeva.core.environment.GlobalEnvRecord
import com.reevajs.reeva.core.environment.ObjectEnvRecord
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.jvmcompat.JSClassInstanceObject
import com.reevajs.reeva.jvmcompat.JSClassObject
import com.reevajs.reeva.mfbt.Dtoa
import com.reevajs.reeva.mfbt.StringToFP
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.arrays.JSArrayObject
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.errors.JSErrorObject
import com.reevajs.reeva.runtime.errors.JSErrorProto
import com.reevajs.reeva.runtime.functions.JSBoundFunction
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.iterators.JSArrayIterator
import com.reevajs.reeva.runtime.memory.DataBlock
import com.reevajs.reeva.runtime.memory.JSIntegerIndexedObject
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.objects.index.IndexedStorage
import com.reevajs.reeva.runtime.other.JSProxyObject
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.runtime.promises.JSCapabilitiesExecutor
import com.reevajs.reeva.runtime.promises.JSPromiseObject
import com.reevajs.reeva.runtime.promises.JSRejectFunction
import com.reevajs.reeva.runtime.promises.JSResolveFunction
import com.reevajs.reeva.runtime.regexp.JSRegExpObject
import com.reevajs.reeva.runtime.regexp.JSRegExpProto
import com.reevajs.reeva.runtime.wrappers.JSBigIntObject
import com.reevajs.reeva.runtime.wrappers.JSBooleanObject
import com.reevajs.reeva.runtime.wrappers.JSNumberObject
import com.reevajs.reeva.runtime.wrappers.JSSymbolObject
import com.reevajs.reeva.runtime.wrappers.strings.JSStringObject
import com.reevajs.reeva.utils.*
import com.reevajs.regexp.RegExp
import java.math.BigInteger
import java.nio.ByteBuffer
import java.time.*
import java.time.format.TextStyle
import java.util.*
import java.util.function.Function
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.*

@OptIn(ExperimentalContracts::class)
object Operations {
    private val realm: Realm
        inline get() = Agent.activeAgent.getActiveRealm()

    const val MAX_SAFE_INTEGER: Long = (1L shl 53) - 1L
    const val MAX_32BIT_INT = 1L shl 32
    const val MAX_31BIT_INT = 1L shl 31
    const val MAX_16BIT_INT = 1 shl 16
    const val MAX_15BIT_INT = 1 shl 15
    const val MAX_8BIT_INT = 1 shl 8
    const val MAX_7BIT_INT = 1 shl 7
    const val MAX_ARRAY_INDEX = MAX_32BIT_INT - 1L

    val MAX_64BIT_INT = BigInteger(
        1,
        byteArrayOf(
            0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
    )
    val MAX_63BIT_INT = BigInteger(
        1,
        byteArrayOf(
            0x40.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
    )

    val defaultZone = ZoneId.systemDefault()
    val defaultZoneOffset = defaultZone.rules.getOffset(Instant.now())
    val defaultLocale = Locale.getDefault()

    private val exponentRegex = Regex("""[eE][+-]?[0-9]+${'$'}""")

    // Note this common gotcha: In Kotlin this really does accept any
    // value, however it gets translated to Object in Java, which can't
    // accept primitives.
    @JvmStatic
    fun wrapInValue(value: Any?): JSValue = when (value) {
        null -> throw IllegalArgumentException("Ambiguous use of null in wrapInValue")
        is Double -> JSNumber(value)
        is Number -> JSNumber(value.toDouble())
        is String -> JSString(value)
        is Boolean -> if (value) JSTrue else JSFalse
        else -> throw IllegalArgumentException("Cannot wrap type ${value::class.java.simpleName}")
    }

    @JvmStatic
    fun checkNotBigInt(value: JSValue) {
        expect(value !is JSBigInt)
    }

    @JvmStatic
    fun mapWrappedArrayIndex(index: JSNumber, arrayLength: Long): Long = when {
        index.isNegativeInfinity -> 0L
        index.asLong < 0L -> max(arrayLength + index.asLong, 0L)
        else -> min(index.asLong, arrayLength)
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.1")
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

    @JvmStatic
    @ECMAImpl("6.1.6.1.2")
    fun numericBitwiseNOT(value: JSValue): JSValue {
        expect(value is JSNumber)
        val oldValue = value.toInt32()
        return JSNumber(oldValue.asDouble.toInt().inv())
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.3")
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

    @JvmStatic
    @ECMAImpl("6.1.6.1.4")
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

    @JvmStatic
    @ECMAImpl("6.1.6.1.5")
    fun numericDivide(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        // TODO: Other requirements
        return JSNumber(lhs.asDouble / rhs.asDouble)
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.6")
    fun numericRemainder(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        // TODO: Other requirements
        return JSNumber(lhs.asDouble.rem(rhs.asDouble))
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.7")
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

    @JvmStatic
    @ECMAImpl("6.1.6.1.8")
    fun numericSubtract(lhs: JSValue, rhs: JSValue): JSValue {
        return numericAdd(lhs, numericUnaryMinus(rhs))
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.9")
    fun numericLeftShift(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        return JSNumber(lhs.toInt32().asInt shl (rhs.toUint32().asInt % 32))
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.10")
    fun numericSignedRightShift(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        return JSNumber(lhs.toInt32().asInt shr (rhs.toUint32().asInt % 32))
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.11")
    fun numericUnsignedRightShift(lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        return JSNumber(lhs.toInt32().asInt ushr (rhs.toUint32().asInt % 32))
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.12")
    fun numericLessThan(lhs: JSValue, rhs: JSValue): JSValue {
        if (lhs.isNaN || rhs.isNaN)
            return JSUndefined
        // TODO: Other requirements
        return (lhs.asDouble < rhs.asDouble).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.13")
    fun numericEqual(lhs: JSValue, rhs: JSValue): JSBoolean {
        if (lhs.isNaN || rhs.isNaN)
            return JSFalse
        // TODO: Other requirements
        return (lhs.asDouble == rhs.asDouble).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.14")
    fun numericSameValue(lhs: JSValue, rhs: JSValue): JSBoolean {
        if (lhs.isNaN && rhs.isNaN)
            return JSTrue
        if (lhs.isPositiveZero && rhs.isNegativeZero)
            return false.toValue()
        if (lhs.isNegativeZero && rhs.isPositiveZero)
            return false.toValue()
        return (lhs.asDouble == rhs.asDouble).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.15")
    fun numericSameValueZero(lhs: JSValue, rhs: JSValue): JSBoolean {
        if (lhs.isNaN && rhs.isNaN)
            return JSTrue
        if (lhs.isZero && rhs.isZero)
            return true.toValue()
        return (lhs.asDouble == rhs.asDouble).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.17")
    fun numericBitwiseAND(lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(lhs.toInt32().asInt and rhs.toInt32().asInt)
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.18")
    fun numericBitwiseXOR(lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(lhs.toInt32().asInt xor rhs.toInt32().asInt)
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.19")
    fun numericBitwiseOR(lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(lhs.toInt32().asInt or rhs.toInt32().asInt)
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.20")
    fun numericToString(value: JSValue): String {
        expect(value is JSNumber)
        return numberToString(value.number)
    }

    fun numberToString(value: Double): String {
        return Dtoa.toShortest(value) ?: TODO()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.1")
    fun bigintUnaryMinus(value: JSValue): JSBigInt {
        expect(value is JSBigInt)
        if (value.number == BigInteger.ZERO)
            return value
        return value.number.negate().toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.2")
    fun bigintBitwiseNOT(value: JSValue): JSBigInt {
        expect(value is JSBigInt)
        return value.number.inv().toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.3")
    fun bigintExponentiate(base: JSValue, exponent: JSValue): JSBigInt {
        expect(base is JSBigInt)
        expect(exponent is JSBigInt)
        if (exponent.number.signum() == -1)
            Errors.BigInt.NegativeExponentiation.throwRangeError()
        if (exponent.number.signum() == 0)
            return JSBigInt.ONE
        try {
            return base.number.pow(exponent.number.intValueExact()).toValue()
        } catch (e: ArithmeticException) {
            Errors.BigInt.OutOfBoundsExponentiation.throwRangeError()
        }
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.4")
    fun bigintMultiply(lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        return lhs.number.multiply(rhs.number).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.5")
    fun bigintDivide(lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        if (rhs.number == BigInteger.ZERO)
            Errors.BigInt.DivideByZero.throwRangeError()
        return lhs.number.divide(rhs.number).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.6")
    fun bigintRemainder(lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        if (rhs.number == BigInteger.ZERO)
            Errors.BigInt.DivideByZero.throwRangeError()
        if (lhs.number == BigInteger.ZERO)
            return JSBigInt.ZERO
        return lhs.number.remainder(rhs.number).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.7")
    fun bigintAdd(lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        return lhs.number.add(rhs.number).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.8")
    fun bigintSubtract(lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        return lhs.number.subtract(rhs.number).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.9")
    fun bigintLeftShift(lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        try {
            return lhs.number.shiftLeft(rhs.number.intValueExact()).toValue()
        } catch (e: ArithmeticException) {
            Errors.BigInt.OutOfBoundsShift.throwRangeError()
        }
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.10")
    fun bigintSignedRightShift(lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        return bigintLeftShift(lhs, rhs.number.negate().toValue())
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.11")
    fun bigintUnsignedRightShift(lhs: JSValue, rhs: JSValue): JSBigInt {
        Errors.BigInt.UnsignedRightShift.throwTypeError()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.12")
    fun bigintLessThan(lhs: JSValue, rhs: JSValue): JSBoolean {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        return (lhs.number < rhs.number).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.12")
    fun bigintEqual(lhs: JSValue, rhs: JSValue): JSBoolean {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        return (lhs.number == rhs.number).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.12")
    fun bigintSameValue(lhs: JSValue, rhs: JSValue): JSBoolean {
        return bigintEqual(lhs, rhs)
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.12")
    fun bigintSameValueZero(lhs: JSValue, rhs: JSValue): JSBoolean {
        return bigintEqual(lhs, rhs)
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.20")
    fun bigintBitwiseAND(lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        return lhs.number.and(rhs.number).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.21")
    fun bigintBitwiseXOR(lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        return lhs.number.xor(rhs.number).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.22")
    fun bigintBitwiseOR(lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        return lhs.number.or(rhs.number).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.23")
    fun bigintToString(value: JSValue): String {
        expect(value is JSBigInt)
        return value.number.toString(10)
    }

    @JvmStatic
    @ECMAImpl("6.2.8.1")
    fun createByteDataBlock(size: Int): DataBlock {
        ecmaAssert(size >= 0)
        try {
            return DataBlock(size)
        } catch (e: OutOfMemoryError) {
            Errors.DataBlock.OutOfMemory(size).throwRangeError()
        }
    }

    @JvmStatic
    fun copyDataBlockBytes(toBlock: DataBlock, toIndex: Int, fromBlock: DataBlock, fromIndex: Int, count: Int) {
        toBlock.copyFrom(fromBlock, fromIndex, toIndex, count)
    }

    @JvmStatic
    @ECMAImpl("7.1.1")
    fun toPrimitive(value: JSValue, type: ToPrimitiveHint? = null): JSValue {
        if (value !is JSObject)
            return value

        val exoticToPrim = getMethod(value, Realm.WellKnownSymbols.toPrimitive)
        if (exoticToPrim !is JSUndefined) {
            val hint = when (type) {
                ToPrimitiveHint.AsDefault, null -> "default"
                ToPrimitiveHint.AsString -> "string"
                ToPrimitiveHint.AsNumber -> "number"
            }.toValue()
            val result = call(exoticToPrim, value, listOf(hint))
            if (result !is JSObject)
                return result
            Errors.BadToPrimitiveReturnValue.throwTypeError()
        }

        return ordinaryToPrimitive(value, type ?: ToPrimitiveHint.AsNumber)
    }

    @JvmStatic
    @ECMAImpl("7.1.1.1")
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
        Errors.FailedToPrimitive(value.toString()).throwTypeError()
    }

    @JvmStatic
    @ECMAImpl("7.1.2")
    fun toBoolean(value: JSValue): Boolean = when (value.type) {
        JSValue.Type.Empty -> unreachable()
        JSValue.Type.Undefined -> false
        JSValue.Type.Null -> false
        JSValue.Type.Boolean -> value == JSTrue
        JSValue.Type.String -> value.asString.isNotEmpty()
        JSValue.Type.Number -> !value.isZero && !value.isNaN
        JSValue.Type.BigInt -> (value as JSBigInt).number.signum() != 0
        JSValue.Type.Symbol -> true
        JSValue.Type.Object -> true
        else -> unreachable()
    }

    @JvmStatic
    @ECMAImpl("7.1.3")
    fun toNumeric(value: JSValue): JSValue {
        val primValue = value.toPrimitive(ToPrimitiveHint.AsNumber)
        if (primValue is JSBigInt)
            return primValue
        return primValue.toNumber()
    }

    @JvmStatic
    @ECMAImpl("7.1.4")
    fun toNumber(value: JSValue): JSNumber {
        return when (value) {
            JSUndefined -> JSNumber.NaN
            JSNull, JSFalse -> JSNumber.ZERO
            JSTrue -> JSNumber(1)
            is JSNumber -> return value
            is JSString -> (stringToNumber(value.string)).toValue()
            is JSSymbol, is JSBigInt -> Errors.FailedToNumber(value.type).throwTypeError()
            is JSObject -> value.toPrimitive(ToPrimitiveHint.AsNumber).toNumber()
            else -> unreachable()
        }
    }

    fun stringToNumber(string: String): Double {
        return StringToFP(string).parse() ?: java.lang.Double.NaN
    }

    @JvmStatic
    @ECMAImpl("7.1.5")
    fun toIntegerOrInfinity(value: JSValue): JSNumber {
        val number = value.toNumber()
        if (number.isNaN || number.isZero)
            return 0.toValue()
        if (number.isInfinite)
            return number
        return abs(number.asLong).let {
            if (number.asLong < 0) it * -1 else it
        }.toValue()
    }

    @JvmStatic
    @ECMAImpl("7.1.6")
    fun toInt32(value: JSValue): JSNumber {
        val number = value.toNumber()
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber.ZERO

        var int = floor(abs(number.asDouble)).toLong()
        if (number.asDouble < 0)
            int *= -1L

        val int32bit = int % MAX_32BIT_INT
        if (int32bit >= MAX_31BIT_INT)
            return JSNumber(int32bit - MAX_32BIT_INT)
        return JSNumber(int32bit)
    }

    @JvmStatic
    @ECMAImpl("7.1.7")
    fun toUint32(value: JSValue): JSNumber {
        val number = value.toNumber()
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber.ZERO

        var int = floor(abs(number.asDouble)).toLong()
        if (number.asDouble < 0)
            int *= -1L
        return JSNumber(int % MAX_32BIT_INT)
    }

    @JvmStatic
    @ECMAImpl("7.1.8")
    fun toInt16(value: JSValue): JSNumber {
        val number = value.toNumber()
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber.ZERO

        var int = floor(abs(number.asDouble)).toLong()
        if (number.asDouble < 0)
            int *= -1L

        val int16bit = int % MAX_16BIT_INT
        if (int16bit >= MAX_15BIT_INT)
            return JSNumber(int16bit - MAX_16BIT_INT)
        return JSNumber(int16bit)
    }

    @JvmStatic
    @ECMAImpl("7.1.9")
    fun toUint16(value: JSValue): JSNumber {
        val number = value.toNumber()
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber.ZERO

        var int = floor(abs(number.asDouble)).toLong()
        if (number.asDouble < 0)
            int *= -1L
        return JSNumber(int % MAX_16BIT_INT)
    }

    @JvmStatic
    @ECMAImpl("7.1.10")
    fun toInt8(value: JSValue): JSNumber {
        val number = value.toNumber()
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber.ZERO

        var int = floor(abs(number.asDouble)).toLong()
        if (number.asDouble < 0)
            int *= -1L

        val int8bit = int % MAX_8BIT_INT
        if (int8bit >= MAX_7BIT_INT)
            return JSNumber(int8bit - MAX_8BIT_INT)
        return JSNumber(int8bit)
    }

    @JvmStatic
    @ECMAImpl("7.1.11")
    fun toUint8(value: JSValue): JSNumber {
        val number = value.toNumber()
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber.ZERO

        var int = floor(abs(number.asDouble)).toLong()
        if (number.asDouble < 0)
            int *= -1L
        return JSNumber(int % MAX_8BIT_INT)
    }

    @JvmStatic
    @ECMAImpl("7.1.12")
    fun toUint8Clamp(value: JSValue): JSNumber {
        val number = value.toNumber()
        if (number.isNaN)
            return JSNumber.ZERO
        if (number.number <= 0)
            return JSNumber.ZERO
        if (number.number >= 255)
            return JSNumber(255)

        val double = number.number
        val floored = floor(double)
        if (floored + 0.5 < double)
            return JSNumber(floored + 1.0)
        if (double < floored + 0.5)
            return JSNumber(floored)
        if (floored.toInt() % 2 == 1)
            return JSNumber(floored + 1.0)
        return JSNumber(floored)
    }

    @JvmStatic
    @ECMAImpl("7.1.13")
    fun toBigInt(value: JSValue): JSBigInt =
        when (val prim = value.toPrimitive(ToPrimitiveHint.AsNumber)) {
            JSUndefined -> Errors.BigInt.Conversion("undefined").throwTypeError()
            JSNull -> Errors.BigInt.Conversion("null").throwTypeError()
            is JSBoolean -> if (prim.boolean) JSBigInt.ONE else JSBigInt.ZERO
            is JSBigInt -> prim
            is JSNumber -> Errors.BigInt.Conversion(prim.number.toString()).throwTypeError()
            is JSString -> stringToBigInt(prim.string)
            is JSSymbol -> Errors.BigInt.Conversion(prim.descriptiveString()).throwTypeError()
            else -> unreachable()
        }

    @JvmStatic
    @ECMAImpl("7.1.14")
    fun stringToBigInt(string: String): JSBigInt {
        val trimmed = string.trim()
        if (trimmed.isEmpty())
            return JSBigInt.ZERO
        if (trimmed == "Infinity" || '.' in trimmed || trimmed.matches(exponentRegex))
            Errors.BigInt.Conversion(string).throwSyntaxError()

        var lc = trimmed.lowercase()
        val radix = when {
            lc.startsWith("0x") -> {
                lc = lc.drop(2)
                16
            }
            lc.startsWith("0o") -> {
                lc = lc.drop(2)
                8
            }
            lc.startsWith("0b") -> {
                lc = lc.drop(2)
                2
            }
            else -> 10
        }

        try {
            return BigInteger(lc, radix).toValue()
        } catch (e: NumberFormatException) {
            Errors.BigInt.Conversion(string).throwSyntaxError()
        }
    }

    @JvmStatic
    @ECMAImpl("7.1.15")
    fun toBigInt64(value: JSValue): JSBigInt {
        val n = value.toBigInt()
        val int64bit = n.number.mod(MAX_64BIT_INT)
        if (int64bit >= MAX_63BIT_INT)
            return int64bit.subtract(MAX_64BIT_INT).toValue()
        return int64bit.toValue()
    }

    @JvmStatic
    @ECMAImpl("7.1.16")
    fun toBigUint64(value: JSValue): JSBigInt {
        return value.toBigInt().number.mod(MAX_64BIT_INT).toValue()
    }

    @JvmStatic
    @ECMAImpl("7.1.17")
    fun toString(value: JSValue): JSString {
        return when (value) {
            is JSString -> return value
            JSUndefined -> "undefined"
            JSNull -> "null"
            JSTrue -> "true"
            JSFalse -> "false"
            is JSNumber -> numericToString(value)
            is JSSymbol -> Errors.FailedSymbolToString.throwTypeError()
            is JSBigInt -> bigintToString(value)
            is JSObject -> value.toPrimitive(ToPrimitiveHint.AsString).toJSString().string
            else -> unreachable()
        }.let(::JSString)
    }

    @JvmStatic
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
            is JSBigInt -> value.number.toString(10) + "n"
            is JSString -> "\"${value.string}\""
            is JSSymbol -> value.descriptiveString()
            is JSErrorObject -> {
                val name = (value.getPrototype() as JSErrorProto).name
                "$name: ${value.message}"
            }
            is JSFunction -> "[Function <${value.debugName}>]"
            is JSObject -> "[object <${value::class.java.simpleName}>]"
            is JSAccessor -> "<accessor>"
            is JSNativeProperty -> "<native-property>"
            is JSEmpty -> "<empty>"
            else -> TODO()
        }
    }

    @JvmStatic
    @ECMAImpl("7.1.18")
    fun toObject(value: JSValue): JSObject {
        return when (value) {
            is JSObject -> value
            is JSUndefined, JSNull -> Errors.FailedToObject(value.type).throwTypeError()
            is JSBoolean -> JSBooleanObject.create(value)
            is JSNumber -> JSNumberObject.create(value)
            is JSString -> JSStringObject.create(value)
            is JSSymbol -> JSSymbolObject.create(value)
            is JSBigInt -> JSBigIntObject.create(value)
            else -> TODO()
        }
    }

    @JvmStatic
    @ECMAImpl("7.1.19")
    fun toPropertyKey(value: JSValue): PropertyKey {
        val key = value.toPrimitive(ToPrimitiveHint.AsString)

        if (key is JSNumber && key.number.let {
                it in 0.0..IndexedStorage.INDEX_UPPER_BOUND.toDouble() &&
                    floor(it) == it
            }
        ) {
            return if (key.number > Int.MAX_VALUE) {
                PropertyKey.from(key.number.toLong())
            } else PropertyKey.from(key.number.toInt())
        }

        if (key is JSSymbol)
            return PropertyKey.from(key)

        return PropertyKey.from(key.toJSString())
    }

    @JvmStatic
    @ECMAImpl("7.1.20")
    fun toLength(value: JSValue): JSValue {
        val len = value.toIntegerOrInfinity()
        val number = len.asLong
        if (number < 0)
            return 0.toValue()
        return min(number, MAX_SAFE_INTEGER).toValue()
    }

    @ECMAImpl("7.1.21")
    fun canonicalNumericIndexString(argument: JSValue): JSNumber? {
        if (argument is JSNumber)
            return argument
        ecmaAssert(argument is JSString)
        if (argument.string == "-0")
            return JSNumber.NEGATIVE_ZERO
        val num = argument.toNumber()
        if (num.toJSString().string != argument.string)
            return null
        return num
    }

    @JvmStatic
    @ECMAImpl("7.1.22")
    fun toIndex(value: JSValue): Int {
        if (value == JSUndefined)
            return 0
        val intIndex = value.toIntegerOrInfinity()
        if (intIndex.isNegativeInfinity || intIndex.asInt < 0)
            Errors.BadIndex(value.toString()).throwRangeError()
        val index = intIndex.toLength()
        if (!intIndex.sameValue(index))
            Errors.BadIndex(value.toString()).throwRangeError()
        return index.asInt
    }

    @JvmStatic
    @ECMAImpl("7.2.1")
    fun requireObjectCoercible(value: JSValue): JSValue {
        if (value is JSUndefined || value is JSNull)
            Errors.FailedToObject(value.type).throwTypeError()
        return value
    }

    @JvmStatic
    @ECMAImpl("7.2.2")
    fun isArray(value: JSValue): Boolean {
        if (!value.isObject)
            return false
        if (value is JSArrayObject)
            return true
        if (value is JSProxyObject) {
            if (value.handler == null)
                Errors.Proxy.RevokedGeneric.throwTypeError()
            return isArray(value.target)
        }
        if (value is JSObject) {
            val handler = value.getSlot(SlotName.ProxyHandler)
            if (handler != null)
                return isArray(value.getSlotAs(SlotName.ProxyTarget))
        }
        return false
    }

    @JvmStatic
    @ECMAImpl("7.2.3")
    fun isCallable(value: JSValue): Boolean {
        contract { returns(true) implies (value is JSFunction) }
        return value is JSFunction && value.isCallable
    }

    @JvmStatic
    @ECMAImpl("7.2.4")
    fun isConstructor(value: JSValue): Boolean {
        contract { returns(true) implies (value is JSFunction) }
        return value is JSFunction && value.isConstructor()
    }

    @JvmStatic
    @ECMAImpl("7.2.6")
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

    @JvmStatic
    @ECMAImpl("7.2.7")
    fun isPropertyKey(value: JSValue) = value is JSString || value is JSSymbol

    @JvmStatic
    @ECMAImpl("7.2.8")
    fun isRegExp(value: JSValue): Boolean {
        if (value !is JSObject)
            return false
        val matcher = value.get(Realm.WellKnownSymbols.match)
        if (matcher != JSUndefined)
            return matcher.toBoolean()
        if (value is JSRegExpObject)
            return true
        return value.hasSlot(SlotName.RegExpMatcher)
    }

    fun isLessThan(lhs: JSValue, rhs: JSValue): Boolean {
        return isLessThan(lhs, rhs, true).ifUndefined { JSFalse } == JSTrue
    }

    fun isLessThanOrEqual(lhs: JSValue, rhs: JSValue): Boolean {
        return isLessThan(rhs, lhs, false) == JSFalse
    }

    fun isGreaterThan(lhs: JSValue, rhs: JSValue): Boolean {
        return isLessThan(rhs, lhs, false).ifUndefined { JSFalse } == JSTrue
    }

    fun isGreaterThanOrEqual(lhs: JSValue, rhs: JSValue): Boolean {
        return isLessThan(lhs, rhs, true) == JSFalse
    }

    @JvmStatic
    @ECMAImpl("7.2.13")
    fun isLessThan(lhs: JSValue, rhs: JSValue, leftFirst: Boolean): JSValue {
        val px: JSValue
        val py: JSValue

        if (leftFirst) {
            px = lhs.toPrimitive(ToPrimitiveHint.AsNumber)
            py = rhs.toPrimitive(ToPrimitiveHint.AsNumber)
        } else {
            py = rhs.toPrimitive(ToPrimitiveHint.AsNumber)
            px = lhs.toPrimitive(ToPrimitiveHint.AsNumber)
        }

        if (px is JSString && py is JSString)
            return (px.string < py.string).toValue()

        if (px is JSBigInt && py is JSString) {
            return try {
                bigintLessThan(px, BigInteger(py.string).toValue())
            } catch (e: NumberFormatException) {
                return JSUndefined
            }
        }

        if (px is JSString && py is JSBigInt) {
            return try {
                bigintLessThan(BigInteger(px.string).toValue(), py)
            } catch (e: NumberFormatException) {
                return JSUndefined
            }
        }

        val nx = px.toNumeric()
        val ny = py.toNumeric()

        if (nx is JSNumber && ny is JSNumber)
            return numericLessThan(nx, ny)
        if (nx is JSBigInt && ny is JSBigInt)
            return bigintLessThan(nx, ny)

        if (nx.isNaN || ny.isNaN)
            return JSUndefined

        if (nx.isNegativeInfinity || ny.isPositiveInfinity)
            return JSTrue
        if (nx.isPositiveInfinity || ny.isNegativeInfinity)
            return JSFalse

        if (nx is JSBigInt)
            return (nx.number < BigInteger.valueOf(ny.asLong)).toValue()
        if (ny is JSBigInt)
            return (BigInteger.valueOf(nx.asLong) < ny.number).toValue()

        unreachable()
    }

    @JvmStatic
    @ECMAImpl("7.2.14")
    fun isStrictlyEqual(lhs: JSValue, rhs: JSValue): JSBoolean {
        if (lhs.type == rhs.type)
            return isLooselyEqual(lhs, rhs)

        if (lhs == JSNull && rhs == JSUndefined)
            return JSTrue
        if (lhs == JSUndefined && rhs == JSNull)
            return JSTrue

        if (lhs is JSNumber && rhs is JSString)
            return isStrictlyEqual(lhs, rhs.toNumber())
        if (lhs is JSString && rhs is JSNumber)
            return isStrictlyEqual(lhs.toNumber(), rhs)

        if (lhs is JSBigInt && rhs is JSString) {
            return try {
                isStrictlyEqual(lhs, BigInteger(rhs.string).toValue())
            } catch (e: NumberFormatException) {
                JSFalse
            }
        }

        if (lhs is JSString && rhs is JSBigInt) {
            return try {
                isStrictlyEqual(BigInteger(lhs.string).toValue(), rhs)
            } catch (e: NumberFormatException) {
                JSFalse
            }
        }

        if (lhs is JSBoolean)
            return isStrictlyEqual(lhs.toNumber(), rhs)
        if (rhs is JSBoolean)
            return isStrictlyEqual(lhs, rhs.toNumber())

        if ((lhs is JSString || lhs is JSNumber || lhs is JSBigInt || lhs is JSSymbol) && rhs is JSObject)
            return isStrictlyEqual(lhs, rhs.toPrimitive())
        if ((rhs is JSString || rhs is JSNumber || rhs is JSBigInt || rhs is JSSymbol) && lhs is JSObject)
            return isStrictlyEqual(lhs.toPrimitive(), rhs)

        if ((lhs is JSBigInt && rhs is JSNumber) || (lhs is JSNumber && rhs is JSBigInt)) {
            if (!lhs.isFinite || !rhs.isFinite)
                return JSFalse
            if (lhs is JSBigInt)
                return (lhs.number < BigInteger.valueOf(rhs.asLong)).toValue()
            expect(rhs is JSBigInt)
            return (BigInteger.valueOf(lhs.asLong) < rhs.number).toValue()
        }

        return JSFalse
    }

    @JvmStatic
    @ECMAImpl("7.2.15")
    fun isLooselyEqual(lhs: JSValue, rhs: JSValue): JSBoolean {
        if (lhs.type != rhs.type)
            return JSFalse
        if (lhs is JSNumber)
            return numericEqual(lhs, rhs)
        if (lhs is JSBigInt)
            return bigintEqual(lhs, rhs)
        return lhs.sameValueNonNumeric(rhs).toValue()
    }

    @JvmStatic
    @ECMAImpl("7.3.3")
    fun getV(target: JSValue, property: PropertyKey): JSValue {
        return target.toObject().get(property)
    }

    fun getV(target: JSValue, property: JSValue) = getV(target, property.toPropertyKey())

    @JvmStatic
    @ECMAImpl("7.3.4")
    fun set(obj: JSObject, property: PropertyKey, value: JSValue, throws: Boolean): Boolean {
        val success = obj.set(property, value)
        if (!success && throws)
            Errors.StrictModeFailedSet(property, obj.toString()).throwTypeError()
        return success
    }

    @JvmStatic
    fun createDataProperty(target: JSValue, property: JSValue, value: JSValue): Boolean {
        return createDataProperty(target, property.toPropertyKey(), value)
    }

    @JvmStatic
    @ECMAImpl("7.3.5")
    fun createDataProperty(target: JSValue, property: PropertyKey, value: JSValue): Boolean {
        ecmaAssert(target is JSObject)
        return target.defineOwnProperty(property, Descriptor(value, Descriptor.DEFAULT_ATTRIBUTES))
    }

    @JvmStatic
    fun createDataPropertyOrThrow(target: JSValue, property: JSValue, value: JSValue): Boolean {
        return createDataPropertyOrThrow(target, property.toPropertyKey(), value)
    }

    @JvmStatic
    @ECMAImpl("7.3.6")
    fun createMethodProperty(obj: JSObject, key: PropertyKey, value: JSValue) {
        obj.defineOwnProperty(key, Descriptor(value, Descriptor.CONFIGURABLE or Descriptor.WRITABLE))
    }

    @JvmStatic
    @ECMAImpl("7.3.7")
    fun createDataPropertyOrThrow(target: JSValue, property: PropertyKey, value: JSValue): Boolean {
        if (!createDataProperty(target, property, value))
            Errors.StrictModeFailedSet(property, target.toString()).throwTypeError()
        return true
    }

    @JvmStatic
    fun definePropertyOrThrow(target: JSValue, property: JSValue, descriptor: Descriptor): Boolean {
        return definePropertyOrThrow(target, property.toPropertyKey(), descriptor)
    }

    @JvmStatic
    @ECMAImpl("7.3.8")
    fun definePropertyOrThrow(target: JSValue, property: PropertyKey, descriptor: Descriptor): Boolean {
        ecmaAssert(target is JSObject)
        if (!target.defineOwnProperty(property, descriptor))
            Errors.StrictModeFailedSet(property, target.toString()).throwTypeError()
        return true
    }

    @JvmStatic
    @ECMAImpl("7.3.9")
    fun deletePropertyOrThrow(target: JSValue, property: PropertyKey): Boolean {
        ecmaAssert(target is JSObject)
        if (!target.delete(property))
            Errors.StrictModeFailedDelete(property, target.toString()).throwTypeError()
        return true
    }

    @JvmStatic
    @ECMAImpl("7.3.10")
    fun getMethod(value: JSValue, key: JSValue): JSValue {
        val func = getV(value, key)
        if (func is JSUndefined || func is JSNull)
            return JSUndefined
        if (!isCallable(func))
            Errors.FailedCall(func.toString()).throwTypeError()
        return func
    }

    @JvmStatic
    @ECMAImpl("7.3.11")
    fun hasProperty(value: JSObject, property: PropertyKey): Boolean {
        return value.hasProperty(property)
    }

    @JvmStatic
    @ECMAImpl("7.3.12")
    fun hasOwnProperty(value: JSObject, property: PropertyKey): Boolean {
        val desc = value.getOwnProperty(property)
        return desc != JSUndefined
    }

    @JvmStatic
    @ECMAImpl("7.3.13")
    fun call(function: JSValue, arguments: JSArguments): JSValue {
        if (!isCallable(function))
            Errors.FailedCall(function.toString()).throwTypeError(realm)
        return function.call(arguments)
    }

    @JvmOverloads
    fun call(function: JSValue, thisValue: JSValue, arguments: List<JSValue> = emptyList()): JSValue {
        return call(function, JSArguments(arguments, thisValue))
    }

    @JvmStatic
    @ECMAImpl("7.3.14")
    fun construct(constructor: JSValue, arguments: JSArguments): JSValue {
        ecmaAssert(isConstructor(constructor))
        ecmaAssert(isConstructor(arguments.newTarget))
        return constructor.construct(arguments)
    }

    @JvmOverloads
    fun construct(
        constructor: JSValue,
        arguments: List<JSValue> = emptyList(),
        newTarget: JSValue = constructor,
    ): JSValue {
        return construct(constructor, JSArguments(arguments, newTarget = newTarget))
    }

    @JvmStatic
    @ECMAImpl("7.3.15")
    fun setIntegrityLevel(obj: JSObject, level: IntegrityLevel): Boolean {
        if (!obj.preventExtensions())
            return false
        val keys = obj.ownPropertyKeys(onlyEnumerable = false)
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

    @JvmStatic
    @ECMAImpl("7.3.17")
    fun createArrayFromList(elements: List<JSValue>): JSValue {
        val array = arrayCreate(elements.size)
        elements.forEachIndexed { index, value ->
            createDataPropertyOrThrow(array, index.toValue(), value)
        }
        return array
    }

    @JvmStatic
    @ECMAImpl("7.3.18")
    fun lengthOfArrayLike(target: JSValue): Long {
        ecmaAssert(target is JSObject)
        return target.get("length").toLength().asLong
    }

    @JvmStatic
    @ECMAImpl("7.3.19")
    fun createListFromArrayLike(obj: JSValue, types: List<JSValue.Type>? = null): List<JSValue> {
        if (obj !is JSObject)
            Errors.FailedToObject(obj.type).throwTypeError()

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

        val length = lengthOfArrayLike(obj)
        val list = mutableListOf<JSValue>()

        for (i in 0 until length) {
            val next = obj.get(i)
            if (next.type !in elementTypes)
                Errors.TODO("createListFromArray").throwTypeError()
            list.add(next)
        }

        return list
    }

    @JvmStatic
    @ECMAImpl("7.3.20")
    fun invoke(value: JSValue, property: PropertyKey, arguments: List<JSValue> = emptyList()): JSValue {
        return call(getV(value, property), value, arguments)
    }

    fun invoke(value: JSValue, property: JSValue, arguments: List<JSValue> = emptyList()): JSValue {
        return invoke(value, property.toPropertyKey(), arguments)
    }

    @JvmStatic
    @ECMAImpl("7.3.21")
    fun ordinaryHasInstance(ctor: JSFunction, target: JSValue): JSValue {
        if (!isCallable(ctor))
            return JSFalse

        // TODO: [[BoundTargetFunction]] slot check
        if (target !is JSObject)
            return JSFalse

        if (ctor is JSClassObject) {
            if (target !is JSClassInstanceObject)
                return JSFalse
            return ctor.clazz.isInstance(target.obj).toValue()
        }

        val ctorProto = ctor.get("prototype")
        if (ctorProto !is JSObject)
            Errors.InstanceOfBadRHS.throwTypeError()

        var obj = target
        while (true) {
            obj = (obj as JSObject).getPrototype()
            if (obj == JSNull)
                return JSFalse
            if (ctorProto.sameValue(obj))
                return JSTrue
        }
    }

    @JvmStatic
    @ECMAImpl("7.3.22")
    fun speciesConstructor(obj: JSValue, defaultCtor: JSFunction): JSFunction {
        ecmaAssert(obj is JSObject)
        val ctor = obj.get("constructor")
        if (ctor == JSUndefined)
            return defaultCtor
        if (ctor !is JSObject)
            Errors.BadCtor(obj.toString()).throwTypeError()

        val species = ctor.get(Realm.WellKnownSymbols.species)
        if (species.isNullish)
            return defaultCtor

        if (isConstructor(species))
            return species

        Errors.SpeciesNotCtor.throwTypeError()
    }

    @JvmStatic
    @ECMAImpl("7.3.23")
    fun enumerableOwnPropertyNames(target: JSValue, kind: JSObject.PropertyKind): List<JSValue> {
        ecmaAssert(target is JSObject)
        val properties = mutableListOf<JSValue>()
        target.ownPropertyKeys(onlyEnumerable = true).forEach { property ->
            if (property.isSymbol)
                return@forEach
            val desc = target.getOwnPropertyDescriptor(property) ?: return@forEach
            if (!desc.isEnumerable)
                return@forEach
            if (kind == JSObject.PropertyKind.Key) {
                properties.add(property.asValue.toJSString())
            } else {
                val value = target.get(property)
                if (kind == JSObject.PropertyKind.Value) {
                    properties.add(value)
                } else {
                    properties.add(createArrayFromList(listOf(property.asValue.toJSString(), value)))
                }
            }
        }
        return properties
    }

    @JvmStatic
    @ECMAImpl("7.3.25")
    fun getFunctionRealm(value: JSValue): Realm {
        return when (value) {
            is JSBoundFunction -> getFunctionRealm(value.boundTargetFunction)
            is JSProxyObject -> {
                if (value.handler == null)
                    Errors.Proxy.RevokedGeneric.throwTypeError()
                getFunctionRealm(value.target)
            }
            is JSFunction -> value.realm
            else -> Agent.activeAgent.getActiveRealm()
        }
    }

    @JvmStatic
    @ECMAImpl("7.3.26")
    fun copyDataProperties(target: JSObject, source: JSValue, excludedItems: List<PropertyKey>): JSObject {
        if (source.isNullish)
            return target
        val from = source.toObject()
        from.ownPropertyKeys(onlyEnumerable = true).forEach outer@{ key ->
            excludedItems.forEach {
                if (it == key)
                    return@outer
            }
            val desc = from.getOwnPropertyDescriptor(key)
            if (desc != null)
                createDataPropertyOrThrow(target, key, from.get(key))
        }
        return target
    }

    @JvmStatic
    @JvmOverloads
    @ECMAImpl("7.4.1")
    fun getIterator(
        obj: JSValue,
        hint: IteratorHint? = IteratorHint.Sync,
        method: JSFunction? = null
    ): IteratorRecord {
        if (hint == IteratorHint.Async)
            TODO()
        val theMethod = method ?: getMethod(obj, Realm.WellKnownSymbols.iterator)
        if (theMethod is JSUndefined)
            Errors.NotIterable(obj.toString()).throwTypeError()
        val iterator = call(theMethod, obj)
        if (iterator !is JSObject)
            Errors.NonObjectIterator.throwTypeError()
        val nextMethod = getV(iterator, "next".toValue())
        return IteratorRecord(iterator, nextMethod, false)
    }

    @JvmStatic
    @ECMAImpl("7.4.2")
    fun iteratorNext(record: IteratorRecord, value: JSValue? = null): JSObject {
        val result = if (value == null) {
            call(record.nextMethod, record.iterator)
        } else {
            call(record.nextMethod, record.iterator, listOf(value))
        }
        if (result !is JSObject)
            Errors.NonObjectIteratorReturn.throwTypeError()
        return result
    }

    @JvmStatic
    @ECMAImpl("7.4.3")
    fun iteratorComplete(result: JSValue): Boolean {
        ecmaAssert(result is JSObject)
        return result.get("done").toBoolean()
    }

    @JvmStatic
    @ECMAImpl("7.4.4")
    fun iteratorValue(result: JSValue): JSValue {
        ecmaAssert(result is JSObject)
        return result.get("value")
    }

    @JvmStatic
    @ECMAImpl("7.4.5")
    fun iteratorStep(record: IteratorRecord): JSValue {
        val result = iteratorNext(record)
        if (iteratorComplete(result))
            return JSFalse
        return result
    }

    @JvmStatic
    @ECMAImpl("7.4.6")
    fun iteratorClose(record: IteratorRecord, value: JSValue): JSValue {
        val method = record.iterator.get("return")
        if (method == JSUndefined)
            return value
        return call(method, record.iterator)
    }

    @JvmStatic
    @ECMAImpl("7.4.8")
    fun createIterResultObject(value: JSValue, done: Boolean): JSValue {
        val obj = JSObject.create()
        createDataPropertyOrThrow(obj, "value".toValue(), value)
        createDataPropertyOrThrow(obj, "done".toValue(), done.toValue())
        return obj
    }

    @JvmStatic
    @ECMAImpl("7.4.10")
    fun iterableToList(items: JSValue, method: JSValue? = null): List<JSValue> {
        val iteratorRecord = getIterator(items, method = method as? JSFunction)
        val values = mutableListOf<JSValue>()
        while (true) {
            val next = iteratorStep(iteratorRecord)
            if (next == JSFalse)
                break
            values.add(iteratorValue(next))
        }
        return values
    }

    @JvmStatic
    @ECMAImpl("8.3.6")
    fun getGlobalObject(): JSObject {
        return realm.globalObject
    }

    @JvmStatic
    @ECMAImpl("9.1.2.5")
    fun newGlobalEnvironment(realm: Realm, globalObject: JSObject, thisValue: JSObject): GlobalEnvRecord {
        // 1. Let objRec be NewObjectEnvironment(G, false, null).
        val objectRecord = ObjectEnvRecord(realm, globalObject, false, null)

        // 2. Let dclRec be a new declarative Environment Record containing no bindings.
        val declarativeRecord = DeclarativeEnvRecord(realm, DeclarativeEnvRecord.UnoptimizedBindings(), null)

        // 3. Let env be a new global Environment Record.
        // 4. Set env.[[ObjectRecord]] to objRec.
        // 5. Set env.[[GlobalThisValue]] to thisValue.
        // 6. Set env.[[DeclarativeRecord]] to dclRec.
        // 7. Set env.[[VarNames]] to a new empty List.
        // 8. Set env.[[OuterEnv]] to null.
        // 9. Return env.
        return GlobalEnvRecord(realm, objectRecord, declarativeRecord, thisValue)
    }

    @JvmStatic
    @ECMAImpl("9.1.6.2")
    fun isCompatiblePropertyDescriptor(
        extensible: Boolean,
        desc: Descriptor,
        current: Descriptor?
    ): Boolean {
        return validateAndApplyPropertyDescriptor(null, null, extensible, desc, current)
    }

    @JvmStatic
    fun validateAndApplyPropertyDescriptor(
        target: JSObject?,
        property: PropertyKey?,
        extensible: Boolean,
        newDesc: Descriptor,
        _currentDesc: Descriptor?
    ): Boolean {
        var currentDesc = _currentDesc
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
                if (!newDesc.hasConfigurable)
                    newDesc.setConfigurable(false)
                if (!newDesc.hasEnumerable)
                    newDesc.setEnumerable(false)
            }
            target?.addProperty(property!!, newDesc)
            return true
        }

        if (currentDesc.run { hasConfigurable && !isConfigurable }) {
            if (newDesc.isConfigurable)
                return false
            if (newDesc.hasEnumerable && currentDesc.isEnumerable != newDesc.isEnumerable)
                return false
        }

        if (!newDesc.isGenericDescriptor) {
            if (currentDesc.isDataDescriptor != newDesc.isDataDescriptor) {
                if (currentDesc.run { hasConfigurable && !isConfigurable })
                    return false
                val newAttrs = (
                    (currentDesc.attributes and (Descriptor.CONFIGURABLE or Descriptor.ENUMERABLE))
                        or Descriptor.HAS_CONFIGURABLE or Descriptor.HAS_ENUMERABLE
                    )

                currentDesc = if (currentDesc.isDataDescriptor) {
                    Descriptor(JSAccessor(null, null), newAttrs)
                } else {
                    Descriptor(JSUndefined, newAttrs)
                }
                target?.addProperty(property!!, currentDesc)
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
                if (newDesc.hasSetter && newSetter != currentSetter)
                    return false
                val currentGetter = currentDesc.getter
                val newGetter = newDesc.getter
                if (newDesc.hasGetter && newGetter != currentGetter)
                    return false
                return true
            }
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

            target.addProperty(property!!, currentDesc)
        }

        return true
    }

    @JvmStatic
    @ECMAImpl("9.1.12")
    fun ordinaryObjectCreate(
        proto: JSValue,
        additionalInternalSlotList: List<SlotName>,
        realm: Realm = this.realm,
    ): JSObject {
        // Spec deviation: [[Prototype]] and [[Extensible]] are not implemented as slots,
        // as they are present in every Object, and thus are just plain fields in the
        // Kotlin side
        val obj = JSObject.create(realm, proto = proto)
        additionalInternalSlotList.forEach {
            obj.addSlot(it, JSUndefined)
        }
        return obj
    }

    @JvmStatic
    @ECMAImpl("9.1.13")
    fun ordinaryCreateFromConstructor(
        constructor: JSValue,
        internalSlotsList: List<SlotName> = emptyList(),
        realm: Realm = this.realm,
        defaultProto: Function<Realm, JSObject>,
    ): JSObject {
        val proto = getPrototypeFromConstructor(constructor, defaultProto)
        return ordinaryObjectCreate(proto, internalSlotsList, realm)
    }

    @JvmStatic
    @ECMAImpl("9.1.14")
    fun getPrototypeFromConstructor(
        constructor: JSValue,
        intrinsicDefaultProtoProducer: Function<Realm, JSObject>,
    ): JSObject {
        ecmaAssert(isCallable(constructor))
        val proto = (constructor as JSObject).get("prototype")
        if (proto is JSObject)
            return proto

        return intrinsicDefaultProtoProducer.apply(getFunctionRealm(constructor))
    }

    @JvmStatic
    @ECMAImpl("9.1.15")
    fun requireInternalSlot(obj: JSValue, slot: SlotName): Boolean {
        contract {
            returns(true) implies (obj is JSObject)
        }
        return obj is JSObject && obj.hasSlot(slot)
    }

    @JvmStatic
    @ECMAImpl("9.4.1.3")
    fun boundFunctionCreate(targetFunction: JSFunction, arguments: JSArguments): JSFunction {
        val proto = targetFunction.getPrototype()
        return JSBoundFunction.create(targetFunction, arguments, proto)
    }

    /**
     * Non-standard function.
     *
     * This function gets all of the object's numeric indices,
     * as well as optionally the numeric indices of its complete
     * prototype chain. This should generally be used wherever the
     * spec requires a loop from 0 to a very large number (usually
     * 2 ^ 53 - 1) in order to do hasProperty checks.
     */
    fun objectIndices(obj: JSObject, includePrototypes: Boolean = true) = sequence {
        // special-case string
        if (obj is JSStringObject) {
            for (i in obj.string.string.indices)
                yield(i.toLong())
            return@sequence
        }

        yieldAll(obj.indexedProperties.indices())
        if (includePrototypes) {
            var proto = obj.getPrototype()
            while (proto != JSNull) {
                yieldAll((proto as JSObject).indexedProperties.indices())
                proto = proto.getPrototype()
            }
        }
    }

    fun arrayCreate(length: Int, proto: JSValue = realm.arrayProto): JSObject {
        return arrayCreate(length.toLong(), proto)
    }

    @JvmStatic
    @JvmOverloads
    @ECMAImpl("9.4.2.2")
    fun arrayCreate(length: Long, proto: JSValue = realm.arrayProto): JSObject {
        if (length >= MAX_32BIT_INT - 1)
            Errors.InvalidArrayLength(length).throwRangeError()
        val array = JSArrayObject.create(proto = proto)
        array.indexedProperties.setArrayLikeSize(length)
        return array
    }

    @JvmStatic
    @ECMAImpl("9.4.2.3")
    fun arraySpeciesCreate(originalArray: JSObject, length: Int) =
        arraySpeciesCreate(originalArray, length.toLong())

    @JvmStatic
    @ECMAImpl("9.4.2.3")
    fun arraySpeciesCreate(originalArray: JSObject, length: Long): JSValue {
        if (!isArray(originalArray))
            return arrayCreate(length)
        var ctor = originalArray.get("constructor")
        if (isConstructor(ctor)) {
            val thisRealm = Agent.activeAgent.getActiveRealm()
            val ctorRealm = getFunctionRealm(ctor)
            if (thisRealm != ctorRealm && ctor.sameValue(ctorRealm.arrayCtor))
                ctor = JSUndefined
        }
        if (ctor is JSObject) {
            ctor = ctor.get(Realm.WellKnownSymbols.species)
            if (ctor == JSNull)
                ctor = JSUndefined
        }
        if (ctor == JSUndefined)
            return arrayCreate(length)
        if (!isConstructor(ctor))
            Errors.SpeciesNotCtor.throwTypeError()
        return construct(ctor, listOf(length.toValue()))
    }

    @JvmStatic
    @ECMAImpl("9.4.5.9")
    fun isValidIntegerIndex(obj: JSValue, index: JSNumber): Boolean {
        ecmaAssert(obj is JSIntegerIndexedObject)
        if (isDetachedBuffer(obj.getSlotAs(SlotName.ViewedArrayBuffer)))
            return false
        if (!isIntegralNumber(index))
            return false
        if (index.isNegativeZero)
            return false
        if (index.number < 0 || index.number > obj.getSlotAs<Int>(SlotName.ArrayLength))
            return false
        return true
    }

    @JvmStatic
    @ECMAImpl("9.4.5.10")
    fun integerIndexedElementGet(obj: JSValue, index: JSValue): JSValue {
        ecmaAssert(obj is JSIntegerIndexedObject)
        if (index !is JSNumber || !isValidIntegerIndex(obj, index))
            return JSUndefined
        val offset = obj.getSlotAs<Int>(SlotName.ByteOffset)
        val kind = obj.getSlotAs<TypedArrayKind>(SlotName.TypedArrayKind)
        val indexedPosition = (index.asInt * kind.size) + offset
        return getValueFromBuffer(
            obj.getSlotAs(SlotName.ViewedArrayBuffer),
            indexedPosition,
            kind,
            true,
            TypedArrayOrder.Unordered
        )
    }

    @JvmStatic
    @ECMAImpl("9.4.5.11")
    fun integerIndexedElementSet(obj: JSValue, index: JSValue, value: JSValue) {
        ecmaAssert(obj is JSIntegerIndexedObject)

        if (index !is JSNumber || !isValidIntegerIndex(obj, index))
            return

        val kind = obj.getSlotAs<TypedArrayKind>(SlotName.TypedArrayKind)
        val numValue = if (kind.isBigInt) {
            value.toBigInt()
        } else value.toNumber()

        val offset = obj.getSlotAs<Int>(SlotName.ByteOffset)
        val indexedPosition = (index.asInt * kind.size) + offset
        setValueInBuffer(
            obj.getSlotAs(SlotName.ViewedArrayBuffer),
            indexedPosition,
            kind,
            numValue,
            true,
            TypedArrayOrder.Unordered
        )
    }

    @JvmStatic
    @ECMAImpl("9.4.7.2")
    fun setImmutablePrototype(obj: JSObject, proto: JSValue): Boolean {
        ecmaAssert(proto is JSObject || proto == JSNull)
        val current = obj.getPrototype()
        return proto.sameValue(current)
    }

    private fun utf16SurrogatePairToCodePoint(leading: Int, trailing: Int): Int {
        return (leading - 0xd800) * 0x400 + (trailing - 0xdc00) + 0x10000
    }

    @JvmStatic
    @ECMAImpl("10.1.4")
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

    @JvmStatic
    @ECMAImpl("10.1.5")
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

    @JvmStatic
    @ECMAImpl("10.2.5")
    fun makeConstructor(
        function: JSFunction,
        writablePrototype: Boolean = true,
        prototype: JSObject? = null
    ) {
        // This function will be a constructor already, as [[Construct]] isn't an actual slot in Reeva
        // ecmaAssert(!isConstructor(function))
        ecmaAssert(function.isExtensible())
        ecmaAssert(!hasOwnProperty(function, "prototype".key()))

        val attributes = if (writablePrototype) Descriptor.WRITABLE else 0
        function.constructorKind = JSFunction.ConstructorKind.Base

        val actualPrototype = prototype ?: let {
            JSObject.create().also {
                definePropertyOrThrow(
                    it,
                    "constructor".key(),
                    Descriptor(function, attributes or Descriptor.CONFIGURABLE)
                )
            }
        }

        definePropertyOrThrow(function, "prototype".key(), Descriptor(actualPrototype, attributes))
    }

    @JvmStatic
    @ECMAImpl("10.2.6")
    fun makeClassConstructor(function: JSFunction) {
        ecmaAssert(!function.isClassConstructor)
        function.isClassConstructor = true
    }

    @JvmStatic
    @ECMAImpl("10.2.7")
    fun makeMethod(function: JSFunction, homeObject: JSValue) {
        ecmaAssert(homeObject is JSObject)
        function.homeObject = homeObject
    }

    @JvmStatic
    @ECMAImpl("10.2.8")
    fun defineMethodProperty(
        key: PropertyKey,
        homeObject: JSValue,
        closure: JSFunction,
        enumerable: Boolean
    ) {
        setFunctionName(closure, key)
        var attributes = Descriptor.WRITABLE or Descriptor.CONFIGURABLE
        if (enumerable)
            attributes = attributes or Descriptor.ENUMERABLE
        definePropertyOrThrow(homeObject, key, Descriptor(closure, attributes))
    }

    @JvmStatic
    @JvmOverloads
    @ECMAImpl("10.2.9")
    fun setFunctionName(function: JSFunction, name: PropertyKey, prefix: String? = null): Boolean {
        ecmaAssert(function.isExtensible())
        val nameString = when {
            name.isSymbol -> name.asSymbol.description.let {
                if (it == null) "" else "[${name.asSymbol.description}]"
            }
            name.isInt -> name.asInt.toString()
            else -> name.asString
        }.let {
            if (prefix != null) {
                "$prefix $it"
            } else it
        }
        return definePropertyOrThrow(
            function,
            "name".toValue(),
            Descriptor(nameString.toValue(), Descriptor.CONFIGURABLE)
        )
    }

    fun setFunctionLength(function: JSFunction, length: Int) {
        definePropertyOrThrow(
            function,
            "length".key(),
            Descriptor(length.toValue(), attrs { +conf; -enum; -writ }),
        )
    }

    @JvmStatic
    @ECMAImpl("12.5.5")
    fun typeofOperator(value: JSValue): JSValue {
        return when (value) {
            JSUndefined -> "undefined"
            JSNull -> "object"
            is JSBoolean -> "boolean"
            is JSNumber -> "number"
            is JSString -> "string"
            is JSSymbol -> "symbol"
            is JSBigInt -> "bigint"
            is JSFunction -> "function"
            is JSProxyObject -> return typeofOperator(value.target)
            is JSObject -> "object"
            else -> unreachable()
        }.toValue()
    }

    @JvmStatic
    @ECMAImpl("12.10.4")
    fun instanceofOperator(target: JSValue, ctor: JSValue): JSValue {
        if (ctor !is JSObject)
            Errors.InstanceOfBadRHS.throwTypeError()

        val instOfHandler = getMethod(target, Realm.WellKnownSymbols.hasInstance)
        if (instOfHandler !is JSUndefined)
            return call(instOfHandler, ctor, listOf(target)).toBoolean().toValue()

        if (!isCallable(ctor))
            Errors.InstanceOfBadRHS.throwTypeError()

        return ordinaryHasInstance(ctor, target)
    }

    fun exp(lhs: JSValue, rhs: JSValue) = applyStringOrNumericBinaryOperator(lhs, rhs, "**")
    fun mul(lhs: JSValue, rhs: JSValue) = applyStringOrNumericBinaryOperator(lhs, rhs, "*")
    fun div(lhs: JSValue, rhs: JSValue) = applyStringOrNumericBinaryOperator(lhs, rhs, "/")
    fun mod(lhs: JSValue, rhs: JSValue) = applyStringOrNumericBinaryOperator(lhs, rhs, "%")
    fun add(lhs: JSValue, rhs: JSValue) = applyStringOrNumericBinaryOperator(lhs, rhs, "+")
    fun sub(lhs: JSValue, rhs: JSValue) = applyStringOrNumericBinaryOperator(lhs, rhs, "-")
    fun shl(lhs: JSValue, rhs: JSValue) = applyStringOrNumericBinaryOperator(lhs, rhs, "<<")
    fun shr(lhs: JSValue, rhs: JSValue) = applyStringOrNumericBinaryOperator(lhs, rhs, ">>")
    fun ushr(lhs: JSValue, rhs: JSValue) = applyStringOrNumericBinaryOperator(lhs, rhs, ">>>")
    fun and(lhs: JSValue, rhs: JSValue) = applyStringOrNumericBinaryOperator(lhs, rhs, "&")
    fun xor(lhs: JSValue, rhs: JSValue) = applyStringOrNumericBinaryOperator(lhs, rhs, "^")
    fun or(lhs: JSValue, rhs: JSValue) = applyStringOrNumericBinaryOperator(lhs, rhs, "|")

    @JvmStatic
    @ECMAImpl("12.15.5")
    fun applyStringOrNumericBinaryOperator(lhs: JSValue, rhs: JSValue, op: String): JSValue {
        if (op == "+") {
            val lprim = lhs.toPrimitive()
            val rprim = rhs.toPrimitive()
            if (lprim.isString || rprim.isString) {
                val lstr = lprim.toJSString()
                val rstr = rprim.toJSString()
                return JSString(lstr.string + rstr.string)
            }
        }

        val lnum = lhs.toNumeric()
        val rnum = rhs.toNumeric()
        if (lnum.type != rnum.type)
            Errors.BadOperator(op, lnum.type, rnum.type).throwTypeError()

        return if (lnum.type == JSValue.Type.BigInt) {
            when (op) {
                "**" -> bigintExponentiate(lnum, rnum)
                "*" -> bigintMultiply(lnum, rnum)
                "/" -> bigintDivide(lnum, rnum)
                "%" -> bigintRemainder(lnum, rnum)
                "+" -> bigintAdd(lnum, rnum)
                "-" -> bigintSubtract(lnum, rnum)
                "<<" -> bigintLeftShift(lnum, rnum)
                ">>" -> bigintSignedRightShift(lnum, rnum)
                ">>>" -> bigintUnsignedRightShift(lnum, rnum)
                "&" -> bigintBitwiseAND(lnum, rnum)
                "^" -> bigintBitwiseXOR(lnum, rnum)
                "|" -> bigintBitwiseOR(lnum, rnum)
                else -> unreachable()
            }
        } else when (op) {
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

    @JvmStatic
    @ECMAImpl("14.1.12")
    fun isAnonymousFunctionDefinition(node: ASTNode): Boolean {
        // TODO
        return false

//        return node.isFunctionDefinition() && !node.hasName()
    }

    @JvmStatic
    @ECMAImpl("20.2.1.1.1")
    fun createDynamicFunction(
        constructor: JSObject,
        newTarget_: JSValue,
        kind: FunctionKind,
        args: JSArguments
    ): JSValue {
        // TODO: Ideally most of this code would be done through an Agent to
        //       avoid code duplication
        unreachable()

        // val agent = Agent.activeAgent
        //
        // // TODO: Figure out the caller vs callee realm separation
        // agent.hostHooks.ensureCanCompileStrings(realm)
        //
        // val newTarget = if (newTarget_ == JSUndefined) constructor else newTarget_ as JSObject
        //
        // val fallbackProto = when (kind) {
        //     FunctionKind.Normal -> newTarget.realm.functionProto
        //     FunctionKind.Generator -> newTarget.realm.generatorFunctionProto
        //     else -> TODO()
        // }
        //
        // val bodyArgString = if (args.isEmpty()) "" else args.last().toJSString(realm).string
        //
        // val argString = if (args.size <= 1) "" else buildString {
        //     for ((index, arg) in args.dropLast(1).withIndex()) {
        //         val string = arg.toJSString(realm).string
        //         if (index != 0)
        //             append(',')
        //         append(string)
        //     }
        // }
        //
        // val bodyString = "\n$bodyArgString\n"
        // val prefix = kind.prefix
        // val sourceString = "$prefix anonymous($argString\n) {$bodyString}"
        //
        // // TODO: 20.2.1.1.1 step 24:
        // // NOTE: The parameters and body are parsed separately to ensure that each is valid alone. For
        // // example, new Function("/*", "*/ ) {") is not legal.
        // val parser = Parser(sourceString)
        //
        // // TODO: Get access to the Agent for printAST/printIR
        //
        // val functionNode = when (val result = parser.parseFunction(kind)) {
        //     is ParsingResult.InternalError -> throw result.cause
        //     is ParsingResult.ParseError -> {
        //         val exception = JSSyntaxErrorObject.create(result.reason)
        //         throw ThrowException(exception)
        //     }
        //     is ParsingResult.Success -> result.node as FunctionDeclarationNode
        // }
        //
        // if (agent.printAST) {
        //     functionNode.debugPrint()
        //     println("\n")
        // }
        //
        // val proto = getPrototypeFromConstructor(constructor, fallbackProto)
        //
        // val ir = Transformer().transform(functionNode)
        //
        // if (agent.printIR) {
        //     IRPrinter(ir).print()
        //     println("\n")
        // }
        //
        // IRPrinter(ir).print()
        // println("\n")
        //
        // // TODO: This globalEnv access will throw an exception if this is access
        // // after the entire script has stopped running (for example, if a function
        // // containing a new Function call is executed on the JVM side). This will
        // // somehow have to climb up the EnvRecord stack of the calling function.
        // val function = Interpreter.wrap(ir, realm, realm.globalEnv, kind)
        // function.setPrototype(proto)
        // definePropertyOrThrow(
        //     realm,
        //     function,
        //     "length".key(),
        //     Descriptor(functionNode.parameters.size.toValue(), Descriptor.CONFIGURABLE)
        // )
        //
        // setFunctionName(function, "anonymous".key())
        //
        // when (kind) {
        //     FunctionKind.Normal -> {
        //         // TODO:
        //         // makeConstructor(function)
        //     }
        //     FunctionKind.Generator -> {
        //         val prototype = JSObject.create(realm.generatorFunctionProto)
        //         definePropertyOrThrow(function, "prototype".key(), Descriptor(prototype, Descriptor.WRITABLE))
        //     }
        //     else -> TODO()
        // }
        //
        // return function
    }

    @JvmStatic
    @ECMAImpl("20.4.1.11")
    fun makeTime(hour: JSValue, min: JSValue, sec: JSValue, ms: JSValue): JSValue {
        if (!hour.isFinite || !min.isFinite || !sec.isFinite || !ms.isFinite)
            return JSNumber.NaN

        val h = hour.toIntegerOrInfinity().asInt
        val m = min.toIntegerOrInfinity().asInt
        val s = sec.toIntegerOrInfinity().asInt
        val milli = ms.toIntegerOrInfinity().asInt

        val lt = LocalTime.of(h, m, s, milli * 1_000_000)

        return (lt.second * 1000 + lt.nano / 1_000_000).toValue()
    }

    @JvmStatic
    @ECMAImpl("20.4.1.12")
    fun makeDay(year: JSValue, month: JSValue, day: JSValue): JSValue {
        if (!year.isFinite || !month.isFinite || !day.isFinite)
            return JSNumber.NaN

        val y = year.toIntegerOrInfinity().asInt
        val m = month.toIntegerOrInfinity().asInt
        val d = day.toIntegerOrInfinity().asInt

        return makeDay(y, m, d).toValue()
    }

    @JvmStatic
    @ECMAImpl("20.4.1.12")
    fun makeDay(year: Int, month: Int, day: Int): Long {
        // TODO: Out of range check
        return LocalDate.of(year, month, day).toEpochDay()
    }

    @JvmStatic
    @ECMAImpl("20.4.1.13")
    fun makeDate(day: JSValue, time: JSValue): JSValue {
        if (!day.isFinite || !time.isFinite)
            return JSNumber.NaN

        return makeDate(day.asLong, time.asLong).toValue()
    }

    @JvmStatic
    @ECMAImpl("20.4.1.13")
    fun makeDate(day: Long, time: Long): Long {
        return day * 86400000L + time
    }

    @JvmStatic
    @ECMAImpl("20.4.1.14")
    fun timeClip(zdt: ZonedDateTime): ZonedDateTime? {
        return if (abs(zdt.toInstant().toEpochMilli()) > 8.64e15) null else zdt
    }

    @JvmStatic
    @ECMAImpl("20.4.4.41.2")
    fun timeString(zdt: ZonedDateTime): String {
        val hour = "%02d".format(zdt.hour)
        val minute = "%02d".format(zdt.minute)
        val second = "%02d".format(zdt.second)
        return "$hour:$minute:$second GMT"
    }

    @JvmStatic
    @ECMAImpl("20.4.4.41.2")
    fun dateString(zdt: ZonedDateTime): String {
        val weekday = zdt.dayOfWeek.getDisplayName(TextStyle.SHORT, defaultLocale)
        val month = zdt.month.getDisplayName(TextStyle.SHORT, defaultLocale)
        val day = "%02d".format(zdt.dayOfMonth)
        val year = zdt.year
        val yearSign = if (year >= 0) "" else "-"
        val paddedYear = "%04d".format(abs(year))
        return "$weekday $month $day $yearSign$paddedYear"
    }

    @JvmStatic
    @ECMAImpl("20.4.4.41.2")
    fun timeZoneString(zdt: ZonedDateTime): String {
        // TODO: Check if this is the correct range, i.e., negative or positive around UTC
        val offsetSeconds = zdt.offset.totalSeconds
        val offsetMinutes = "%02d".format((abs(offsetSeconds / 60) % 60))
        val offsetHours = "%02d".format(abs(offsetSeconds / (60 * 60) % 24))
        val offsetSign = if (offsetSeconds < 0) "-" else "+"

        return "$offsetSign$offsetHours$offsetMinutes (${defaultZone.getDisplayName(TextStyle.FULL, defaultLocale)})"
    }

    @JvmStatic
    @ECMAImpl("20.4.4.41.4")
    fun toDateString(tv: ZonedDateTime): String {
        return buildString {
            append(dateString(tv))
            append(" ")
            append(timeString(tv))
            append(timeZoneString(tv))
        }
    }

    @JvmStatic
    @ECMAImpl("21.1.3.29.1")
    fun trimString(string: JSValue, where: TrimType): String {
        val str = string.requireObjectCoercible().toJSString().string

        fun removable(ch: Char) = ch.isWhiteSpace() || isLineTerminator(ch)

        return when (where) {
            TrimType.Start -> str.dropWhile(::removable)
            TrimType.End -> str.dropLastWhile(::removable)
            TrimType.StartEnd -> str.dropWhile(::removable).dropLastWhile(::removable)
        }
    }

    private fun isLineTerminator(ch: Char) = ch == '\u000a' || ch == '\u000d' || ch == '\u2028' || ch == '\u2029'

    @JvmStatic
    @ECMAImpl("21.2.3.2.1")
    fun regExpAlloc(newTarget: JSValue): JSObject {
        val slots = listOf(SlotName.RegExpMatcher, SlotName.OriginalSource, SlotName.OriginalFlags)
        val obj = ordinaryCreateFromConstructor(newTarget, slots, defaultProto = Realm::regExpProto)
        definePropertyOrThrow(obj, "lastIndex".key(), Descriptor(JSEmpty, attrs { +writ; -enum; -conf }))
        return obj
    }

    @JvmStatic
    @ECMAImpl("21.2.3.2.2")
    fun regExpInitialize(obj: JSObject, patternArg: JSValue, flagsArg: JSValue): JSObject {
        val pattern = if (patternArg == JSUndefined) "" else patternArg.toJSString().string
        val flags = if (flagsArg == JSUndefined) "" else flagsArg.toJSString().string

        val invalidFlag = flags.firstOrNull { JSRegExpObject.Flag.values().none { flag -> flag.char == it } }
        if (invalidFlag != null)
            Errors.RegExp.InvalidFlag(invalidFlag).throwSyntaxError()
        if (flags.toCharArray().distinct().size != flags.length)
            Errors.RegExp.DuplicateFlag.throwSyntaxError()

        obj.setSlot(SlotName.OriginalSource, pattern)
        obj.setSlot(SlotName.OriginalFlags, flags)
        obj.setSlot(SlotName.RegExpMatcher, JSRegExpObject.makeClosure(pattern, flags))
        set(obj, "lastIndex".key(), 0.toValue(), true)

        return obj
    }

    @JvmStatic
    @ECMAImpl("21.2.3.2.4")
    fun regExpCreate(pattern: JSValue, flags: JSValue): JSObject {
        val obj = regExpAlloc(realm.regExpCtor)
        return regExpInitialize(obj, pattern, flags)
    }

    @ECMAImpl("21.2.5.2.1")
    fun regExpExec(thisValue: JSValue, string: JSValue, methodName: String): JSValue {
        ecmaAssert(thisValue is JSObject)
        ecmaAssert(string is JSString)

        val exec = thisValue.get("exec")
        if (isCallable(exec)) {
            val result = call(exec, thisValue, listOf(string))
            if (result !is JSObject && result != JSNull)
                Errors.RegExp.ExecBadReturnType.throwTypeError()
            return result
        }
        if (thisValue !is JSRegExpProto)
            Errors.IncompatibleMethodCall("RegExp.prototype$methodName").throwTypeError()
        return regExpBuiltinExec(thisValue, string)
    }

    @ECMAImpl("21.2.5.2.2")
    fun regExpBuiltinExec(thisValue: JSValue, string: JSValue): JSValue {
        ecmaAssert(thisValue is JSObject)
        ecmaAssert(string is JSString)
        requireInternalSlot(thisValue, SlotName.RegExpMatcher)

        val text = string.string
        val flags = thisValue.getSlotAs<String>(SlotName.OriginalFlags)
        val global = JSRegExpObject.Flag.Global.char in flags
        val sticky = JSRegExpObject.Flag.Sticky.char in flags
        val lastIndex = if (global || sticky) {
            thisValue.get("lastIndex").toLength().asInt
        } else 0

        val regex = thisValue.getSlotAs<RegExp>(SlotName.RegExpMatcher)
        val match = regex.matcher(text).match(lastIndex)

        if (match == null) {
            if ((global || sticky))
                set(thisValue, "lastIndex".key(), 0.toValue(), true)
            return JSNull
        }

        if (global || sticky)
            set(thisValue, "lastIndex".key(), (match.range.last + 1).toValue(), true)

        val arr = arrayCreate(match.indexedGroups.size)
        createDataPropertyOrThrow(arr, "index".key(), lastIndex.toValue())
        createDataPropertyOrThrow(arr, "input".key(), string)

        if (match.indexedGroups.isNotEmpty()) {
            (0..match.indexedGroups.lastKey()).forEach {
                val value = match.indexedGroups[it]?.value?.toValue() ?: JSUndefined
                createDataProperty(arr, it.key(), value)
            }
        }

        val groups = if (match.namedGroups.isNotEmpty()) {
            JSObject.create(proto = JSNull)
        } else JSUndefined

        createDataPropertyOrThrow(arr, "groups".key(), groups)

        for ((name, group) in match.namedGroups)
            createDataPropertyOrThrow(groups, name.key(), group.value.toValue())

        return arr
    }

    @ECMAImpl("22.1.5.1")
    fun createArrayIterator(array: JSObject, kind: JSObject.PropertyKind): JSValue {
        return JSArrayIterator.create(array, 0, kind)
    }

    @JvmStatic
    @ECMAImpl("22.2.4.1")
    fun typedArraySpeciesCreate(exemplar: JSValue, arguments: JSArguments): JSObject {
        ecmaAssert(exemplar is JSObject && exemplar.hasSlots(SlotName.TypedArrayName, SlotName.ContentType))

        val kind = exemplar.getSlotAs<TypedArrayKind>(SlotName.TypedArrayKind)
        val defaultConstructor = kind.getCtor(Agent.activeAgent.getActiveRealm())
        val constructor = speciesConstructor(exemplar, defaultConstructor)
        val result = typedArrayCreate(constructor, arguments)
        if (result.getSlot(SlotName.ContentType) != exemplar.getSlot(SlotName.ContentType))
            Errors.TODO("typedArraySpeciesCreate").throwTypeError()
        return result
    }

    @JvmStatic
    @ECMAImpl("22.2.4.2")
    fun typedArrayCreate(constructor: JSValue, arguments: JSArguments): JSObject {
        val newTypedArray = construct(constructor, arguments)
        validateTypedArray(newTypedArray)
        expect(newTypedArray is JSObject)
        if (arguments.size == 1) {
            val arg = arguments.argument(0)
            if (arg is JSNumber && newTypedArray.getSlotAs<Int>(SlotName.ArrayLength) != arg.asInt)
                Errors.TODO("typedArrayCreate").throwTypeError()
        }
        return newTypedArray
    }

    @JvmStatic
    @ECMAImpl("22.2.4.3")
    fun validateTypedArray(obj: JSValue) {
        if (!requireInternalSlot(obj, SlotName.TypedArrayName))
            Errors.TODO("validateTypedArray requireInternalSlot").throwTypeError()
        ecmaAssert(obj.hasSlot(SlotName.ViewedArrayBuffer))
        val buffer = obj.getSlotAs<JSObject>(SlotName.ViewedArrayBuffer)
        if (isDetachedBuffer(buffer))
            Errors.TODO("validateTypedArray isDetachedBuffer").throwTypeError()
    }

    @JvmStatic
    @ECMAImpl("23.1.1.2")
    fun addEntriesFromIterable(target: JSObject, iterable: JSValue, adder: JSValue): JSValue {
        if (!isCallable(adder))
            Errors.TODO("addEntriesFromIterable 1").throwTypeError()

        // TODO: This whole method is super scuffed
        ecmaAssert(iterable != JSUndefined && iterable != JSNull)
        val record = getIterator(iterable) as? IteratorRecord ?: return JSEmpty
        while (true) {
            val next = iteratorStep(record)
            if (next == JSFalse)
                return target
            val nextItem = iteratorValue(next)
            if (nextItem !is JSObject) {
                iteratorClose(record, JSEmpty)
                Errors.TODO("addEntriesFromIterable 2").throwTypeError()
            }
            val key = try {
                nextItem.get(0)
            } catch (e: ThrowException) {
                iteratorClose(record, JSEmpty)
                throw e
            }
            val value = try {
                nextItem.get(1)
            } catch (e: ThrowException) {
                iteratorClose(record, JSEmpty)
                throw e
            }
            try {
                call(adder, target, listOf(key, value))
            } catch (e: ThrowException) {
                iteratorClose(record, JSEmpty)
                throw e
            }
        }
    }

    @JvmStatic
    @ECMAImpl("24.1.2.1")
    fun allocateArrayBuffer(constructor: JSValue, byteLength: Int): JSObject {
        val slots = listOf(SlotName.ArrayBufferData, SlotName.ArrayBufferByteLength, SlotName.ArrayBufferDetachKey)
        val obj = ordinaryCreateFromConstructor(constructor, slots, defaultProto = Realm::arrayBufferProto)
        val block = createByteDataBlock(byteLength)
        obj.setSlot(SlotName.ArrayBufferData, block)
        obj.setSlot(SlotName.ArrayBufferByteLength, byteLength)
        return obj
    }

    @JvmStatic
    @ECMAImpl("24.1.2.2")
    fun isDetachedBuffer(buffer: JSValue): Boolean {
        ecmaAssert(buffer is JSObject && buffer.hasSlot(SlotName.ArrayBufferData))
        return buffer.getSlot(SlotName.ArrayBufferData) == null
    }

    @JvmStatic
    @ECMAImpl("24.1.2.3")
    fun detachArrayBuffer(arrayBuffer: JSValue, key: JSValue = JSUndefined) {
        ecmaAssert(arrayBuffer is JSObject)
        ecmaAssert(
            arrayBuffer.hasSlots(
                SlotName.ArrayBufferData,
                SlotName.ArrayBufferByteLength,
                SlotName.ArrayBufferDetachKey
            )
        )
        ecmaAssert(!isSharedArrayBuffer(arrayBuffer))

        val expectedKey = arrayBuffer.getSlotAs<JSValue>(SlotName.ArrayBufferDetachKey)
        if (!expectedKey.sameValue(key))
            Errors.ArrayBuffer.BadDetachKey(expectedKey.toString(), key.toString())
                .throwTypeError()

        arrayBuffer.setSlot(SlotName.ArrayBufferData, null)
        arrayBuffer.setSlot(SlotName.ArrayBufferByteLength, 0)
    }

    @JvmStatic
    @ECMAImpl("24.1.2.4")
    fun cloneArrayBuffer(
        srcBuffer: JSValue,
        srcByteOffset: Int,
        srcLength: Int,
        cloneConstructor: JSValue
    ): JSObject {
        ecmaAssert(srcBuffer is JSObject && srcBuffer.hasSlot(SlotName.ArrayBufferData))
        ecmaAssert(cloneConstructor.isConstructor)

        val targetBuffer = allocateArrayBuffer(cloneConstructor, srcLength)
        if (isDetachedBuffer(srcBuffer))
            Errors.TODO("cloneArrayBuffer isDetachedBuffer").throwTypeError()
        val srcBlock = srcBuffer.getSlotAs<DataBlock>(SlotName.ArrayBufferData)
        val targetBlock = targetBuffer.getSlotAs<DataBlock>(SlotName.ArrayBufferData)
        copyDataBlockBytes(targetBlock, 0, srcBlock, srcByteOffset, srcLength)
        return targetBuffer
    }

    @JvmStatic
    @ECMAImpl("24.1.2.5")
    fun isUnsignedElementType(type: TypedArrayKind) = type.isUnsigned

    @JvmStatic
    @ECMAImpl("24.1.2.6")
    fun isUnclampedIntegerElementType(type: TypedArrayKind) = type.isUnclampedInt

    @JvmStatic
    @ECMAImpl("24.1.2.7")
    fun isBigIntElementType(type: TypedArrayKind) = type.isBigInt

    @JvmStatic
    @ECMAImpl("24.1.2.8")
    fun isNoTearConfiguration(type: TypedArrayKind, order: TypedArrayOrder): Boolean {
        if (isUnclampedIntegerElementType(type))
            return true
        if (isBigIntElementType(type) && order != TypedArrayOrder.Init && order != TypedArrayOrder.Unordered)
            return true
        return false
    }

    @JvmStatic
    @ECMAImpl("24.1.2.9")
    fun rawBytesToNumeric(type: TypedArrayKind, rawBytes: ByteArray, isLittleEndian: Boolean): JSValue {
        if (isLittleEndian)
            rawBytes.reverse()

        if (type == TypedArrayKind.Float32)
            return ByteBuffer.wrap(rawBytes).float.toValue()
        if (type == TypedArrayKind.Float64)
            return ByteBuffer.wrap(rawBytes).double.toValue()

        return if (isBigIntElementType(type)) {
            if (isUnsignedElementType(type)) {
                JSBigInt(BigInteger(1, rawBytes))
            } else JSBigInt(BigInteger(rawBytes))
        } else {
            // TODO: There's gotta be a better way to do this...
            if (isUnsignedElementType(type)) {
                if (rawBytes[0] < 0) {
                    val buff = ByteBuffer.wrap(rawBytes)
                    var value = 0.0

                    while (buff.hasRemaining()) {
                        value *= 256.0
                        value += java.lang.Byte.toUnsignedInt(buff.get()).toDouble()
                    }

                    return value.toValue()
                }
            }

            val buff = ByteBuffer.wrap(rawBytes)

            when (type.size) {
                1 -> buff.get(0)
                2 -> buff.short
                4 -> buff.int
                else -> unreachable()
            }.toValue()
        }
    }

    @JvmStatic
    @ECMAImpl("24.1.2.10")
    fun getValueFromBuffer(
        arrayBuffer: JSValue,
        byteIndex: Int,
        kind: TypedArrayKind,
        isTypedArray: Boolean,
        order: TypedArrayOrder,
        isLittleEndian: Boolean = Agent.activeAgent.isLittleEndian
    ): JSValue {
        ecmaAssert(arrayBuffer is JSObject)
        ecmaAssert(!isDetachedBuffer(arrayBuffer))
        val block = arrayBuffer.getSlotAs<DataBlock>(SlotName.ArrayBufferData)
        ecmaAssert(byteIndex + kind.size - 1 < block.size)

        if (isSharedArrayBuffer(arrayBuffer))
            TODO()

        val rawBytes = block.getBytes(byteIndex, kind.size)
        return rawBytesToNumeric(kind, rawBytes, isLittleEndian)
    }

    @JvmStatic
    @ECMAImpl("24.1.2.11")
    fun numericToRawBytes(type: TypedArrayKind, value: JSValue, isLittleEndian: Boolean): ByteArray {
        when (type) {
            TypedArrayKind.Float32 -> {
                expect(value is JSNumber)
                return ByteBuffer.allocate(4).putInt(value.number.toFloat().toRawBits()).array().also {
                    if (isLittleEndian)
                        it.reverse()
                }
            }
            TypedArrayKind.Float64 -> {
                expect(value is JSNumber)
                return ByteBuffer.allocate(8).putLong(value.number.toRawBits()).array().also {
                    if (isLittleEndian)
                        it.reverse()
                }
            }
            else -> {
                val convOp = type.convOp!!
                val numValue = convOp(value).let {
                    if (it is JSBigInt) {
                        var arr = it.number.toByteArray()
                        if (arr.size < type.size) {
                            val newArr = ByteArray(type.size)
                            System.arraycopy(arr, 0, newArr, type.size - arr.size, arr.size)
                            if (it.number.signum() < 0) {
                                for (i in 0 until (type.size - arr.size))
                                    newArr[i] = 0xff.toByte()
                            }
                            arr = newArr
                        }
                        if (isLittleEndian)
                            arr.reverse()
                        return arr
                    } else ((it as JSNumber).number.toLong() and 0xFFFFFFFF).toInt()
                }

                val buff = ByteBuffer.allocate(type.size)
                when (type.size) {
                    1 -> buff.put(numValue.toByte())
                    2 -> buff.putShort(numValue.toShort())
                    4 -> buff.putInt(numValue)
                    else -> unreachable()
                }

                return buff.array().also {
                    if (isLittleEndian)
                        it.reverse()
                }
            }
        }
    }

    @JvmStatic
    @ECMAImpl("24.1.2.12")
    fun setValueInBuffer(
        arrayBuffer: JSValue,
        byteIndex: Int,
        type: TypedArrayKind,
        value: JSValue,
        isTypedArray: Boolean,
        order: TypedArrayOrder,
        isLittleEndian: Boolean = Agent.activeAgent.isLittleEndian
    ) {
        ecmaAssert(arrayBuffer is JSObject)
        ecmaAssert(!isDetachedBuffer(arrayBuffer))
        ecmaAssert(if (value is JSBigInt) isBigIntElementType(type) else value is JSNumber)

        val block = arrayBuffer.getSlotAs<DataBlock>(SlotName.ArrayBufferData)
        ecmaAssert(byteIndex + type.size <= block.size)

        val rawBytes = numericToRawBytes(type, value, isLittleEndian)
        if (isSharedArrayBuffer(arrayBuffer))
            TODO()

        block.copyFrom(rawBytes, 0, byteIndex, type.size)
    }

    // TODO: Reeva doesn't support SharedArrayBuffers
    @JvmStatic
    @ECMAImpl("24.2.1.2")
    fun isSharedArrayBuffer(obj: JSValue) = false

    @JvmStatic
    @ECMAImpl("24.3.1.1")
    fun getViewValue(
        view: JSValue,
        requestIndex: JSValue,
        littleEndian: JSValue,
        kind: TypedArrayKind
    ): JSValue {
        if (!requireInternalSlot(view, SlotName.DataView))
            Errors.TODO("getViewValue requireInternalSlot").throwTypeError()

        val index = requestIndex.toIndex()
        val isLittleEndian = littleEndian.toBoolean()
        val buffer = view.getSlotAs<JSObject>(SlotName.ViewedArrayBuffer)
        if (isDetachedBuffer(buffer))
            Errors.TODO("getViewValue isDetachedBuffer").throwTypeError()

        val viewOffset = view.getSlotAs<Int>(SlotName.ByteOffset)
        val viewSize = view.getSlotAs<Int>(SlotName.ByteLength)
        if (index + kind.size > viewSize)
            Errors.TODO("getViewValue out of range").throwRangeError()

        return getValueFromBuffer(buffer, index + viewOffset, kind, false, TypedArrayOrder.Unordered, isLittleEndian)
    }

    @JvmStatic
    @ECMAImpl("24.3.1.2")
    fun setViewValue(
        view: JSValue,
        requestIndex: JSValue,
        littleEndian: JSValue,
        kind: TypedArrayKind,
        value: JSValue
    ) {
        if (!requireInternalSlot(view, SlotName.DataView))
            Errors.TODO("setViewValue requireInternalSlot").throwTypeError()

        val index = requestIndex.toIndex()
        val numberValue = if (kind.isBigInt) value.toBigInt() else value.toNumber()
        val isLittleEndian = littleEndian.toBoolean()
        val buffer = view.getSlotAs<JSObject>(SlotName.ViewedArrayBuffer)
        if (isDetachedBuffer(buffer))
            Errors.TODO("getViewValue isDetachedBuffer").throwTypeError()

        val viewOffset = view.getSlotAs<Int>(SlotName.ByteOffset)
        val viewSize = view.getSlotAs<Int>(SlotName.ByteLength)
        if (index + kind.size > viewSize)
            Errors.TODO("getViewValue out of range").throwRangeError()

        setValueInBuffer(
            buffer,
            index + viewOffset,
            kind,
            numberValue,
            false,
            TypedArrayOrder.Unordered,
            isLittleEndian
        )
    }

    @JvmStatic
    @ECMAImpl("26.6.1.3")
    fun createResolvingFunctions(promise: JSObject): Pair<JSFunction, JSFunction> {
        val resolvedStatus = Wrapper(false)
        val resolve = JSResolveFunction.create(promise, resolvedStatus)
        val reject = JSRejectFunction.create(promise, resolvedStatus)
        return resolve to reject
    }

    @JvmStatic
    @ECMAImpl("26.6.1.4")
    fun fulfillPromise(promise: JSObject, reason: JSValue): JSValue {
        ecmaAssert(promise.getSlot(SlotName.PromiseState) == PromiseState.Pending)
        val reactions = promise.getSlotAs<List<PromiseReaction>>(SlotName.PromiseFulfillReactions).toList()
        promise.setSlot(SlotName.PromiseResult, reason)
        promise.setSlot(SlotName.PromiseFulfillReactions, mutableListOf<PromiseReaction>())
        promise.setSlot(SlotName.PromiseRejectReactions, mutableListOf<PromiseReaction>())
        promise.setSlot(SlotName.PromiseState, PromiseState.Fulfilled)
        return triggerPromiseReactions(reactions, reason)
    }

    @JvmStatic
    @ECMAImpl("26.6.1.5")
    fun newPromiseCapability(ctor: JSValue): PromiseCapability {
        if (!isConstructor(ctor))
            Errors.TODO("newPromiseCapability").throwTypeError()
        val capability = PromiseCapability(JSEmpty, null, null)
        val executor = JSCapabilitiesExecutor.create(capability)
        val promise = construct(ctor, listOf(executor))
        capability.promise = promise
        return capability
    }

    @JvmStatic
    @ECMAImpl("26.6.1.6")
    fun isPromise(value: JSValue): Boolean {
        contract {
            returns(true) implies (value is JSObject)
        }
        if (value !is JSObject)
            return false
        if (value is JSPromiseObject)
            return true
        if (value is JSProxyObject)
            return isPromise(value.target)
        return value.hasSlot(SlotName.PromiseState)
    }

    @JvmStatic
    fun unwrapPromise(value: JSValue): JSValue {
        if (Agent.activeAgent.contextStack().isNotEmpty()) {
            throw IllegalStateException(
                "Operations.unwrapPromise is a helper method and cannot be called during JS execution, " +
                    "as it must arbitrarily spin the microtask queue"
            )
        }

        if (!isPromise(value))
            return value

        while (value.getSlotAs<PromiseState>(SlotName.PromiseState) == PromiseState.Pending)
            Agent.activeAgent.microtaskQueue.checkpoint()

        val result = value.getSlotAs<JSValue>(SlotName.PromiseResult)

        if (value.getSlotAs<PromiseState>(SlotName.PromiseState) == PromiseState.Fulfilled)
            return result

        throw ThrowException(result)
    }

    @JvmStatic
    @ECMAImpl("26.6.1.7")
    internal fun rejectPromise(promise: JSObject, reason: JSValue): JSValue {
        ecmaAssert(promise.getSlot(SlotName.PromiseState) == PromiseState.Pending)
        val reactions = promise.getSlotAs<List<PromiseReaction>>(SlotName.PromiseRejectReactions).toList()
        promise.setSlot(SlotName.PromiseResult, reason)
        promise.setSlot(SlotName.PromiseFulfillReactions, mutableListOf<PromiseReaction>())
        promise.setSlot(SlotName.PromiseRejectReactions, mutableListOf<PromiseReaction>())
        promise.setSlot(SlotName.PromiseState, PromiseState.Rejected)

        if (!promise.getSlotAs<Boolean>(SlotName.PromiseIsHandled))
            Agent.activeAgent.hostHooks.promiseRejectionTracker(promise, "reject")

        return triggerPromiseReactions(reactions, reason)
    }

    @JvmStatic
    @ECMAImpl("26.6.1.8")
    fun triggerPromiseReactions(reactions: List<PromiseReaction>, argument: JSValue): JSValue {
        reactions.forEach { reaction ->
            val job = newPromiseReactionJob(reaction, argument)
            Agent.activeAgent.hostHooks.enqueuePromiseJob(job.realm, job.job)
        }
        return JSUndefined
    }

    @JvmStatic
    @ECMAImpl("26.6.2.1")
    fun newPromiseReactionJob(reaction: PromiseReaction, argument: JSValue): PromiseReactionJob {
        val job = job@{
            val handlerResult: Any = if (reaction.handler == null) {
                if (reaction.type == PromiseReaction.Type.Fulfill) {
                    argument
                } else {
                    ThrowException(argument)
                }
            } else try {
                Agent.activeAgent.hostHooks.callJobCallback(
                    reaction.handler,
                    JSArguments(listOf(argument))
                )
            } catch (e: ThrowException) {
                e
            }

            if (reaction.capability == null) {
                ecmaAssert(handlerResult !is ThrowException)
                return@job
            }

            if (handlerResult is ThrowException) {
                call(reaction.capability.reject!!, JSUndefined, listOf(handlerResult.value))
            } else {
                call(reaction.capability.resolve!!, JSUndefined, listOf(handlerResult as JSValue))
            }
        }

        val handlerRealm = if (reaction.handler != null) {
            try {
                getFunctionRealm(reaction.handler.callback)
            } catch (e: ThrowException) {
                Agent.activeAgent.getActiveRealm()
            }
        } else null

        return PromiseReactionJob(handlerRealm, job)
    }

    @JvmStatic
    @ECMAImpl("26.6.2.2")
    fun newPromiseResolveThenableJob(
        promise: JSObject,
        thenable: JSValue,
        then: JobCallback
    ): PromiseReactionJob {
        val job = {
            val (resolveFunction, rejectFunction) = createResolvingFunctions(promise)
            try {
                Agent.activeAgent.hostHooks.callJobCallback(
                    then,
                    JSArguments(listOf(resolveFunction, rejectFunction), thenable)
                )
            } catch (e: ThrowException) {
                call(rejectFunction, JSUndefined, listOf(e.value))
            }
            Unit
        }

        val thenRealm = try {
            getFunctionRealm(then.callback)
        } catch (e: ThrowException) {
            Agent.activeAgent.getActiveRealm()
        }

        return PromiseReactionJob(thenRealm, job)
    }

    @JvmStatic
    @ECMAImpl("26.6.4.1.1")
    fun getPromiseResolve(constructor: JSValue): JSValue {
        ecmaAssert(isConstructor(constructor))
        val resolve = (constructor as JSObject).get("resolve")
        if (!isCallable(resolve))
            Errors.TODO("getPromiseResolve").throwTypeError(constructor.realm)
        return resolve
    }

    @JvmStatic
    @ECMAImpl("26.6.4.7")
    fun promiseResolve(constructor: JSObject, value: JSValue): JSValue {
        if (isPromise(value)) {
            val valueCtor = value.get("constructor")
            if (valueCtor.sameValue(constructor))
                return value
        }

        val capability = newPromiseCapability(constructor)
        call(capability.resolve!!, JSUndefined, listOf(value))
        return capability.promise
    }

    @JvmStatic
    @ECMAImpl("26.6.5.4.1")
    fun performPromiseThen(
        promise: JSObject,
        onFulfilled: JSValue,
        onRejected: JSValue,
        resultCapability: PromiseCapability?
    ): JSValue {
        val onFulfilledCallback = if (isCallable(onFulfilled)) {
            Agent.activeAgent.hostHooks.makeJobCallback(onFulfilled)
        } else null
        val onRejectedCallback = if (isCallable(onRejected)) {
            Agent.activeAgent.hostHooks.makeJobCallback(onRejected)
        } else null

        val fulfillReaction = PromiseReaction(resultCapability, PromiseReaction.Type.Fulfill, onFulfilledCallback)
        val rejectReaction = PromiseReaction(resultCapability, PromiseReaction.Type.Reject, onRejectedCallback)

        when (promise.getSlotAs<PromiseState>(SlotName.PromiseState)) {
            PromiseState.Pending -> {
                promise.getSlotAs<MutableList<PromiseReaction>>(SlotName.PromiseFulfillReactions).add(fulfillReaction)
                promise.getSlotAs<MutableList<PromiseReaction>>(SlotName.PromiseRejectReactions).add(rejectReaction)
            }
            PromiseState.Fulfilled -> {
                val fulfillJob =
                    newPromiseReactionJob(fulfillReaction, promise.getSlotAs(SlotName.PromiseResult))
                Agent.activeAgent.hostHooks.enqueuePromiseJob(fulfillJob.realm, fulfillJob.job)
            }
            else -> {
                if (!promise.getSlotAs<Boolean>(SlotName.PromiseIsHandled))
                    Agent.activeAgent.hostHooks.promiseRejectionTracker(promise, "handle")
                val rejectJob = newPromiseReactionJob(rejectReaction, promise.getSlotAs(SlotName.PromiseResult))
                Agent.activeAgent.hostHooks.enqueuePromiseJob(rejectJob.realm, rejectJob.job)
            }
        }

        promise.setSlot(SlotName.PromiseIsHandled, true)

        return resultCapability?.promise ?: JSUndefined
    }

    data class JobCallback(val callback: JSFunction, val hostDefined: JSValue = JSEmpty)

    enum class ToPrimitiveHint(private val _text: String) {
        AsDefault("default"),
        AsString("string"),
        AsNumber("number");

        val value: JSString
            get() = JSString(_text)
    }

    enum class IntegrityLevel {
        Sealed,
        Frozen,
    }

    enum class FunctionKind(val prefix: String, val isAsync: Boolean = false, val isGenerator: Boolean = false) {
        Normal("function"),
        Async("async function", isAsync = true),
        Generator("function*", isGenerator = true),
        AsyncGenerator("async function*", isAsync = true, isGenerator = true),
    }

    enum class IteratorHint {
        Sync,
        Async
    }

    data class IteratorRecord(val iterator: JSObject, val nextMethod: JSValue, var isDone: Boolean) : JSValue()

    data class CodepointRecord(val codePoint: Int, val codeUnitCount: Int, val isUnpairedSurrogate: Boolean)

    enum class TrimType {
        Start,
        End,
        StartEnd
    }

    enum class TypedArrayKind(
        val size: Int,
        val isUnsigned: Boolean,
        val isUnclampedInt: Boolean,
        val isBigInt: Boolean,
        val convOp: ((JSValue) -> JSValue)?
    ) {
        Int8(1, false, true, false, ::toInt8),
        Uint8(1, true, true, false, ::toUint8),
        Uint8C(1, true, false, false, ::toUint8Clamp),
        Int16(2, false, true, false, ::toInt16),
        Uint16(2, true, true, false, ::toUint16),
        Int32(4, false, true, false, ::toInt32),
        Uint32(4, true, true, false, ::toUint32),
        Float32(4, false, false, false, null),
        Float64(8, false, false, false, null),
        BigInt64(8, false, false, true, ::toBigInt64),
        BigUint64(8, true, false, true, ::toBigUint64);

        fun getCtor(realm: Realm) = when (this) {
            Int8 -> realm.int8ArrayCtor
            Uint8 -> realm.uint8ArrayCtor
            Uint8C -> realm.uint8CArrayCtor
            Int16 -> realm.int16ArrayCtor
            Uint16 -> realm.uint16ArrayCtor
            Int32 -> realm.int32ArrayCtor
            Uint32 -> realm.uint32ArrayCtor
            Float32 -> realm.float32ArrayCtor
            Float64 -> realm.float64ArrayCtor
            BigInt64 -> realm.bigInt64ArrayCtor
            BigUint64 -> realm.bigUint64ArrayCtor
        }

        fun getProto(realm: Realm) = when (this) {
            Int8 -> realm.int8ArrayProto
            Uint8 -> realm.uint8ArrayProto
            Uint8C -> realm.uint8CArrayProto
            Int16 -> realm.int16ArrayProto
            Uint16 -> realm.uint16ArrayProto
            Int32 -> realm.int32ArrayProto
            Uint32 -> realm.uint32ArrayProto
            Float32 -> realm.float32ArrayProto
            Float64 -> realm.float64ArrayProto
            BigInt64 -> realm.bigInt64ArrayProto
            BigUint64 -> realm.bigUint64ArrayProto
        }
    }

    enum class TypedArrayOrder {
        Init,
        Unordered,
    }

    data class PromiseReaction(
        val capability: PromiseCapability?,
        val type: Type,
        val handler: JobCallback?,
    ) {
        enum class Type {
            Fulfill,
            Reject,
        }
    }

    data class Wrapper<T>(var value: T)

    data class PromiseReactionJob(val realm: Realm?, val job: () -> Unit)

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
}
