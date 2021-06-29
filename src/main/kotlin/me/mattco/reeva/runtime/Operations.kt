@file:Suppress("unused")

package me.mattco.reeva.runtime

import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.ASTNode
import me.mattco.reeva.ast.FunctionDeclarationNode
import me.mattco.reeva.ast.ParameterList
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.interpreter.transformer.Transformer
import me.mattco.reeva.interpreter.transformer.opcodes.IrPrinter
import me.mattco.reeva.jvmcompat.JSClassInstanceObject
import me.mattco.reeva.jvmcompat.JSClassObject
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.parser.ParsingResult
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.arrays.JSArrayObject
import me.mattco.reeva.runtime.builtins.JSMappedArgumentsObject
import me.mattco.reeva.runtime.builtins.JSProxyObject
import me.mattco.reeva.runtime.builtins.JSUnmappedArgumentsObject
import me.mattco.reeva.runtime.builtins.promises.JSCapabilitiesExecutor
import me.mattco.reeva.runtime.builtins.promises.JSPromiseObject
import me.mattco.reeva.runtime.builtins.promises.JSRejectFunction
import me.mattco.reeva.runtime.builtins.promises.JSResolveFunction
import me.mattco.reeva.runtime.builtins.regexp.JSRegExpObject
import me.mattco.reeva.runtime.builtins.regexp.JSRegExpProto
import me.mattco.reeva.runtime.errors.JSSyntaxErrorObject
import me.mattco.reeva.runtime.functions.JSBoundFunction
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.iterators.JSArrayIterator
import me.mattco.reeva.runtime.memory.DataBlock
import me.mattco.reeva.runtime.memory.JSIntegerIndexedObject
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.objects.index.IndexedStorage
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.runtime.wrappers.*
import me.mattco.reeva.utils.*
import org.joni.Matcher
import org.joni.Option
import java.math.BigInteger
import java.nio.ByteBuffer
import java.time.*
import java.time.format.TextStyle
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.*

@OptIn(ExperimentalContracts::class)
object Operations {
    const val MAX_SAFE_INTEGER: Long = (1L shl 53) - 1L
    const val MAX_32BIT_INT = 1L shl 32
    const val MAX_31BIT_INT = 1L shl 31
    const val MAX_16BIT_INT = 1 shl 16
    const val MAX_15BIT_INT = 1 shl 15
    const val MAX_8BIT_INT = 1 shl 8
    const val MAX_7BIT_INT = 1 shl 7
    const val MAX_ARRAY_INDEX = MAX_32BIT_INT - 1L

    val MAX_64BIT_INT = BigInteger(
        1, byteArrayOf(
            0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
    )
    val MAX_63BIT_INT = BigInteger(
        1, byteArrayOf(
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
    fun isNullish(value: JSValue): Boolean {
        return value == JSUndefined || value == JSNull
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
    fun numericBitwiseNOT(realm: Realm, value: JSValue): JSValue {
        expect(value is JSNumber)
        val oldValue = toInt32(realm, value)
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
    fun numericLeftShift(realm: Realm, lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        return JSNumber(toInt32(realm, lhs).asInt shl (toUint32(realm, rhs).asInt % 32))
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.10")
    fun numericSignedRightShift(realm: Realm, lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        return JSNumber(toInt32(realm, lhs).asInt shr (toUint32(realm, rhs).asInt % 32))
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.11")
    fun numericUnsignedRightShift(realm: Realm, lhs: JSValue, rhs: JSValue): JSValue {
        expect(lhs is JSNumber)
        expect(rhs is JSNumber)
        if (lhs.isNaN || rhs.isNaN)
            return JSNumber.NaN
        return JSNumber(toInt32(realm, lhs).asInt ushr (toUint32(realm, rhs).asInt % 32))
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
    fun numericBitwiseAND(realm: Realm, lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(toInt32(realm, lhs).asInt and toInt32(realm, rhs).asInt)
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.18")
    fun numericBitwiseXOR(realm: Realm, lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(toInt32(realm, lhs).asInt xor toInt32(realm, rhs).asInt)
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.19")
    fun numericBitwiseOR(realm: Realm, lhs: JSValue, rhs: JSValue): JSValue {
        return JSNumber(toInt32(realm, lhs).asInt or toInt32(realm, rhs).asInt)
    }

    @JvmStatic
    @ECMAImpl("6.1.6.1.20")
    fun numericToString(value: JSValue): String {
        expect(value is JSNumber)
        if (value.isNaN)
            return "NaN"
        if (value.isZero)
            return "0"
        if (value.number < 0)
            return "-" + numericToString(JSNumber(-value.number))
        if (value.isPositiveInfinity)
            return "Infinity"

        // TODO: Better conversion, preferably V8's algorithm
        // (mfbt/double-conversion/double-conversion.{h,cc}
        if (value.isInt)
            return value.asInt.toString()
        if (value.isLong)
            return value.asLong.toString()
        return value.asDouble.toString()
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
    fun bigintExponentiate(realm: Realm, base: JSValue, exponent: JSValue): JSBigInt {
        expect(base is JSBigInt)
        expect(exponent is JSBigInt)
        if (exponent.number.signum() == -1)
            Errors.BigInt.NegativeExponentiation.throwRangeError(realm)
        if (exponent.number.signum() == 0)
            return JSBigInt.ONE
        try {
            return base.number.pow(exponent.number.intValueExact()).toValue()
        } catch (e: ArithmeticException) {
            Errors.BigInt.OutOfBoundsExponentiation.throwRangeError(realm)
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
    fun bigintDivide(realm: Realm, lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        if (rhs.number == BigInteger.ZERO)
            Errors.BigInt.DivideByZero.throwRangeError(realm)
        return lhs.number.divide(rhs.number).toValue()
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.6")
    fun bigintRemainder(realm: Realm, lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        if (rhs.number == BigInteger.ZERO)
            Errors.BigInt.DivideByZero.throwRangeError(realm)
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
    fun bigintLeftShift(realm: Realm, lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        try {
            return lhs.number.shiftLeft(rhs.number.intValueExact()).toValue()
        } catch (e: ArithmeticException) {
            Errors.BigInt.OutOfBoundsShift.throwRangeError(realm)
        }
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.10")
    fun bigintSignedRightShift(realm: Realm, lhs: JSValue, rhs: JSValue): JSBigInt {
        expect(lhs is JSBigInt)
        expect(rhs is JSBigInt)
        return bigintLeftShift(realm, lhs, rhs.number.negate().toValue())
    }

    @JvmStatic
    @ECMAImpl("6.1.6.2.11")
    fun bigintUnsignedRightShift(realm: Realm, lhs: JSValue, rhs: JSValue): JSBigInt {
        Errors.BigInt.UnsignedRightShift.throwTypeError(realm)
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
    fun createByteDataBlock(realm: Realm, size: Int): DataBlock {
        ecmaAssert(size >= 0)
        try {
            return DataBlock(size)
        } catch (e: OutOfMemoryError) {
            Errors.DataBlock.OutOfMemory(size).throwRangeError(realm)
        }
    }

    @JvmStatic
    fun copyDataBlockBytes(toBlock: DataBlock, toIndex: Int, fromBlock: DataBlock, fromIndex: Int, count: Int) {
        toBlock.copyFrom(fromBlock, fromIndex, toIndex, count)
    }

//    @JvmStatic @ECMAImpl("6.2.4.4")
//    fun getValue(reference: JSValue): JSValue {
//        if (reference !is JSReference)
//            return reference
//        if (reference.isUnresolvableReference)
//            Errors.UnknownReference(reference.name).throwReferenceError(realm)
//        var base = reference.baseValue
//        if (reference.isPropertyReference) {
//            if (reference.hasPrimitiveBase) {
//                expect(base != JSUndefined && base != JSNull)
//
//                val fastPathResult = resolvePrimitiveReference(base as JSValue, reference.name)
//                if (fastPathResult != null)
//                    return fastPathResult
//
//                base = toObject(base)
//            }
//            val value = (base as JSObject).get(reference.name, reference.getThisValue())
//            if (value is JSNativeProperty)
//                return value.get(base)
//            if (value is JSAccessor)
//                return value.callGetter(base)
//            return value
//        }
//
//        expect(base is EnvRecord)
//        expect(reference.name.isString)
//        return base.getBindingValue(reference.name.asString, reference.isStrict)
//    }

//    // A fast-path for common primitive reference operations. If this
//    // is able to complete successfully and return a non-null value, it
//    // avoid an object boxing operation
//    private fun resolvePrimitiveReference(base: JSValue, key: PropertyKey): JSValue? {
//        if (base is JSString) {
//            val str = base.string
//            if (key.isInt) {
//                val index = key.asInt
//                if (index < 0 || index > str.lastIndex)
//                    return JSUndefined
//                return str[index].toValue()
//            }
//
//            if (key.isLong) {
//                val index = key.asLong.toInt()
//                if (index < 0 || index > str.lastIndex)
//                    return JSUndefined
//                return str[index].toValue()
//            }
//        }
//
//        return null
//    }

//    @JvmStatic @ECMAImpl("6.2.4.5")
//    fun putValue(reference: JSValue, value: JSValue) {
//        if (reference !is JSReference)
//            Errors.InvalidLHSAssignment(toPrintableString(reference)).throwReferenceError(realm)
//        var base = reference.baseValue
//        if (reference.isUnresolvableReference) {
//            if (reference.isStrict)
//                Errors.UnresolvableReference(reference.name).throwReferenceError(realm)
//            Reeva.activeAgent.activeRealm.globalObject.set(reference.name, value)
//        } else if (reference.isPropertyReference) {
//            if (reference.hasPrimitiveBase) {
//                ecmaAssert(base != JSUndefined && base != JSNull)
//                base = toObject(base as JSValue)
//            }
//            val succeeded = (base as JSObject).set(reference.name, value, reference.getThisValue())
//            if (!succeeded && reference.isStrict)
//                Errors.StrictModeFailedSet(reference.name, toPrintableString(base)).throwTypeError(realm)
//        } else {
//            ecmaAssert(base is EnvRecord)
//            expect(reference.name.isString)
//            base.setBinding(reference.name.asString, value, reference.isStrict)
//        }
//    }

//    @JvmStatic @ECMAImpl("6.2.4.11")
//    fun initializeReferencedBinding(reference: JSReference, value: JSValue) {
//        ecmaAssert(!reference.isUnresolvableReference, "Unknown reference with identifier ${reference.name}")
//        val base = reference.baseValue
//        ecmaAssert(base is EnvRecord)
//        expect(reference.name.isString)
//        base.initializeBinding(reference.name.asString, value)
//    }

    @JvmStatic
    @ECMAImpl("7.1.1")
    fun toPrimitive(realm: Realm, value: JSValue, type: ToPrimitiveHint? = null): JSValue {
        if (value !is JSObject)
            return value

        val exoticToPrim = getMethod(realm, value, Realm.`@@toPrimitive`)
        if (exoticToPrim !is JSUndefined) {
            val hint = when (type) {
                ToPrimitiveHint.AsDefault, null -> "default"
                ToPrimitiveHint.AsString -> "string"
                ToPrimitiveHint.AsNumber -> "number"
            }.toValue()
            val result = call(realm, exoticToPrim, value, listOf(hint))
            if (result !is JSObject)
                return result
            Errors.BadToPrimitiveReturnValue.throwTypeError(realm)
        }

        return ordinaryToPrimitive(realm, value, type ?: ToPrimitiveHint.AsNumber)
    }

    @JvmStatic
    @ECMAImpl("7.1.1.1")
    fun ordinaryToPrimitive(realm: Realm, value: JSValue, hint: ToPrimitiveHint): JSValue {
        ecmaAssert(value is JSObject)
        ecmaAssert(hint != ToPrimitiveHint.AsDefault)
        val methodNames = when (hint) {
            ToPrimitiveHint.AsString -> listOf("toString", "valueOf")
            else -> listOf("valueOf", "toString")
        }
        methodNames.forEach { methodName ->
            val method = value.get(methodName)
            if (isCallable(method)) {
                val result = call(realm, method, value)
                if (result !is JSObject)
                    return result
            }
        }
        Errors.FailedToPrimitive(toPrintableString(value)).throwTypeError(realm)
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
    fun toNumeric(realm: Realm, value: JSValue): JSValue {
        val primValue = toPrimitive(realm, value, ToPrimitiveHint.AsNumber)
        if (primValue is JSBigInt)
            return primValue
        return toNumber(realm, primValue)
    }

    @JvmStatic
    @ECMAImpl("7.1.4")
    fun toNumber(realm: Realm, value: JSValue): JSNumber {
        return when (value) {
            JSUndefined -> JSNumber.NaN
            JSNull, JSFalse -> JSNumber.ZERO
            JSTrue -> JSNumber(1)
            is JSNumber -> return value
            // TODO: spec-compliant string printing
            is JSString -> when (value.string) {
                "+Infinity", "Infinity" -> JSNumber.POSITIVE_INFINITY
                "-Infinity" -> JSNumber.NEGATIVE_INFINITY
                else -> if ('.' in value.string) {
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
            }
            is JSSymbol, is JSBigInt -> Errors.FailedToNumber(value.type).throwTypeError(realm)
            is JSObject -> toNumber(realm, toPrimitive(realm, value, ToPrimitiveHint.AsNumber))
            else -> unreachable()
        }
    }

    @JvmStatic
    @ECMAImpl("7.1.5")
    fun toIntegerOrInfinity(realm: Realm, value: JSValue): JSNumber {
        val number = toNumber(realm, value)
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
    fun toInt32(realm: Realm, value: JSValue): JSNumber {
        val number = toNumber(realm, value)
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
    fun toUint32(realm: Realm, value: JSValue): JSNumber {
        val number = toNumber(realm, value)
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber.ZERO

        var int = floor(abs(number.asDouble)).toLong()
        if (number.asDouble < 0)
            int *= -1L
        return JSNumber(int % MAX_32BIT_INT)
    }

    @JvmStatic
    @ECMAImpl("7.1.8")
    fun toInt16(realm: Realm, value: JSValue): JSNumber {
        val number = toNumber(realm, value)
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
    fun toUint16(realm: Realm, value: JSValue): JSNumber {
        val number = toNumber(realm, value)
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber.ZERO

        var int = floor(abs(number.asDouble)).toLong()
        if (number.asDouble < 0)
            int *= -1L
        return JSNumber(int % MAX_16BIT_INT)
    }

    @JvmStatic
    @ECMAImpl("7.1.10")
    fun toInt8(realm: Realm, value: JSValue): JSNumber {
        val number = toNumber(realm, value)
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
    fun toUint8(realm: Realm, value: JSValue): JSNumber {
        val number = toNumber(realm, value)
        if (number.isZero || number.isInfinite || number.isNaN)
            return JSNumber.ZERO

        var int = floor(abs(number.asDouble)).toLong()
        if (number.asDouble < 0)
            int *= -1L
        return JSNumber(int % MAX_8BIT_INT)
    }

    @JvmStatic
    @ECMAImpl("7.1.12")
    fun toUint8Clamp(realm: Realm, value: JSValue): JSNumber {
        val number = toNumber(realm, value)
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
    fun toBigInt(realm: Realm, value: JSValue): JSBigInt =
        when (val prim = toPrimitive(realm, value, ToPrimitiveHint.AsNumber)) {
            JSUndefined -> Errors.BigInt.Conversion("undefined").throwTypeError(realm)
            JSNull -> Errors.BigInt.Conversion("null").throwTypeError(realm)
            is JSBoolean -> if (prim.boolean) JSBigInt.ONE else JSBigInt.ZERO
            is JSBigInt -> prim
            is JSNumber -> Errors.BigInt.Conversion(prim.number.toString()).throwTypeError(realm)
            is JSString -> stringToBigInt(realm, prim.string)
            is JSSymbol -> Errors.BigInt.Conversion(prim.descriptiveString()).throwTypeError(realm)
            else -> unreachable()
        }

    @JvmStatic
    @ECMAImpl("7.1.14")
    fun stringToBigInt(realm: Realm, string: String): JSBigInt {
        val trimmed = string.trim()
        if (trimmed.isEmpty())
            return JSBigInt.ZERO
        if (trimmed == "Infinity" || '.' in trimmed || trimmed.matches(exponentRegex))
            Errors.BigInt.Conversion(string).throwSyntaxError(realm)

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
            Errors.BigInt.Conversion(string).throwSyntaxError(realm)
        }
    }

    @JvmStatic
    @ECMAImpl("7.1.15")
    fun toBigInt64(realm: Realm, value: JSValue): JSBigInt {
        val n = toBigInt(realm, value)
        val int64bit = n.number.mod(MAX_64BIT_INT)
        if (int64bit >= MAX_63BIT_INT)
            return int64bit.subtract(MAX_64BIT_INT).toValue()
        return int64bit.toValue()
    }

    @JvmStatic
    @ECMAImpl("7.1.16")
    fun toBigUint64(realm: Realm, value: JSValue): JSBigInt {
        return toBigInt(realm, value).number.mod(MAX_64BIT_INT).toValue()
    }

    @JvmStatic
    @ECMAImpl("7.1.17")
    fun toString(realm: Realm, value: JSValue): JSString {
        return when (value) {
            is JSString -> return value
            JSUndefined -> "undefined"
            JSNull -> "null"
            JSTrue -> "true"
            JSFalse -> "false"
            // TODO: Make sure to follow all of JS's number conversion rules here
            is JSNumber -> numericToString(value)
            is JSSymbol -> Errors.FailedSymbolToString.throwTypeError(realm)
            is JSBigInt -> bigintToString(value)
            is JSObject -> toString(realm, toPrimitive(realm, value, ToPrimitiveHint.AsString)).string
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
            is JSObject -> "[object <${value::class.java.simpleName}>]"
            is JSAccessor -> "<accessor>"
            is JSNativeProperty -> "<native-property>"
            is JSEmpty -> "<empty>"
            else -> TODO()
        }
    }

    @JvmStatic
    @ECMAImpl("7.1.18")
    fun toObject(realm: Realm, value: JSValue): JSObject {
        return when (value) {
            is JSObject -> value
            is JSUndefined, JSNull -> Errors.FailedToObject(value.type).throwTypeError(realm)
            is JSBoolean -> JSBooleanObject.create(realm, value)
            is JSNumber -> JSNumberObject.create(realm, value)
            is JSString -> JSStringObject.create(realm, value)
            is JSSymbol -> JSSymbolObject.create(realm, value)
            is JSBigInt -> JSBigIntObject.create(realm, value)
            else -> TODO()
        }
    }

    @JvmStatic
    @ECMAImpl("7.1.19")
    fun toPropertyKey(realm: Realm, value: JSValue): PropertyKey {
        val key = toPrimitive(realm, value, ToPrimitiveHint.AsString)

        if (key is JSNumber && key.number.let { it in 0.0..IndexedStorage.INDEX_UPPER_BOUND.toDouble() && floor(it) == it })
            return if (key.number > Int.MAX_VALUE) {
                PropertyKey.from(key.number.toLong())
            } else PropertyKey.from(key.number.toInt())

        if (key is JSSymbol)
            return PropertyKey.from(key)

        return PropertyKey.from(toString(realm, key))
    }

    @JvmStatic
    @ECMAImpl("7.1.20")
    fun toLength(realm: Realm, value: JSValue): JSValue {
        val len = toIntegerOrInfinity(realm, value)
        val number = len.asLong
        if (number < 0)
            return 0.toValue()
        return min(number, MAX_SAFE_INTEGER).toValue()
    }

    @ECMAImpl("7.1.21")
    fun canonicalNumericIndexString(realm: Realm, argument: JSValue): JSNumber? {
        if (argument is JSNumber)
            return argument
        ecmaAssert(argument is JSString)
        if (argument.string == "-0")
            return JSNumber.NEGATIVE_ZERO
        val num = toNumber(realm, argument)
        if (toString(realm, num).string != argument.string)
            return null
        return num
    }

    @JvmStatic
    @ECMAImpl("7.1.22")
    fun toIndex(realm: Realm, value: JSValue): Int {
        if (value == JSUndefined)
            return 0
        val intIndex = toIntegerOrInfinity(realm, value)
        if (intIndex.isNegativeInfinity || intIndex.asInt < 0)
            Errors.BadIndex(toPrintableString(value)).throwRangeError(realm)
        val index = toLength(realm, intIndex)
        if (!intIndex.sameValue(index))
            Errors.BadIndex(toPrintableString(value)).throwRangeError(realm)
        return index.asInt
    }

    @JvmStatic
    @ECMAImpl("7.2.1")
    fun requireObjectCoercible(realm: Realm, value: JSValue): JSValue {
        if (value is JSUndefined || value is JSNull)
            Errors.FailedToObject(value.type).throwTypeError(realm)
        return value
    }

    @JvmStatic
    @ECMAImpl("7.2.2")
    fun isArray(realm: Realm, value: JSValue): Boolean {
        if (!value.isObject)
            return false
        if (value is JSArrayObject)
            return true
        if (value is JSProxyObject) {
            if (value.handler == null)
                Errors.Proxy.RevokedGeneric.throwTypeError(realm)
            return isArray(realm, value.target)
        }
        if (value is JSObject) {
            val handler = value.getSlot(SlotName.ProxyHandler)
            if (handler != null)
                return isArray(realm, value.getSlotAs(SlotName.ProxyTarget))
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
        return value is JSFunction && value.isConstructable
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
        val matcher = value.get(Realm.`@@match`)
        if (matcher != JSUndefined)
            return toBoolean(matcher)
        if (value is JSRegExpObject)
            return true
        return value.hasSlot(SlotName.RegExpMatcher)
    }

    @JvmStatic
    @ECMAImpl("7.2.13")
    fun abstractRelationalComparison(realm: Realm, lhs: JSValue, rhs: JSValue, leftFirst: Boolean): JSValue {
        val px: JSValue
        val py: JSValue

        if (leftFirst) {
            px = toPrimitive(realm, lhs, ToPrimitiveHint.AsNumber)
            py = toPrimitive(realm, rhs, ToPrimitiveHint.AsNumber)
        } else {
            py = toPrimitive(realm, rhs, ToPrimitiveHint.AsNumber)
            px = toPrimitive(realm, lhs, ToPrimitiveHint.AsNumber)
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

        val nx = toNumeric(realm, px)
        val ny = toNumeric(realm, py)

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
    fun abstractEqualityComparison(realm: Realm, lhs: JSValue, rhs: JSValue): JSBoolean {
        if (lhs.type == rhs.type)
            return strictEqualityComparison(lhs, rhs)

        if (lhs == JSNull && rhs == JSUndefined)
            return JSTrue
        if (lhs == JSUndefined && rhs == JSNull)
            return JSTrue

        if (lhs is JSNumber && rhs is JSString)
            return abstractEqualityComparison(realm, lhs, toNumber(realm, rhs))
        if (lhs is JSString && rhs is JSNumber)
            return abstractEqualityComparison(realm, toNumber(realm, lhs), rhs)

        if (lhs is JSBigInt && rhs is JSString) {
            return try {
                abstractEqualityComparison(realm, lhs, BigInteger(rhs.string).toValue())
            } catch (e: NumberFormatException) {
                JSFalse
            }
        }

        if (lhs is JSString && rhs is JSBigInt) {
            return try {
                abstractEqualityComparison(realm, BigInteger(lhs.string).toValue(), rhs)
            } catch (e: NumberFormatException) {
                JSFalse
            }
        }

        if (lhs is JSBoolean)
            return abstractEqualityComparison(realm, toNumber(realm, lhs), rhs)
        if (rhs is JSBoolean)
            return abstractEqualityComparison(realm, lhs, toNumber(realm, rhs))

        if ((lhs is JSString || lhs is JSNumber || lhs is JSBigInt || lhs is JSSymbol) && rhs is JSObject)
            return abstractEqualityComparison(realm, lhs, toPrimitive(realm, rhs))
        if ((rhs is JSString || rhs is JSNumber || rhs is JSBigInt || rhs is JSSymbol) && lhs is JSObject)
            return abstractEqualityComparison(realm, toPrimitive(realm, lhs), rhs)

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
    fun strictEqualityComparison(lhs: JSValue, rhs: JSValue): JSBoolean {
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
    fun getV(realm: Realm, target: JSValue, property: PropertyKey): JSValue {
        val obj = toObject(realm, target)
        return obj.get(property)
    }

    fun getV(realm: Realm, target: JSValue, property: JSValue) = getV(realm, target, toPropertyKey(realm, property))

    @JvmStatic
    @ECMAImpl("7.3.4")
    fun set(realm: Realm, obj: JSObject, property: PropertyKey, value: JSValue, throws: Boolean): Boolean {
        val success = obj.set(property, value)
        if (!success && throws)
            Errors.StrictModeFailedSet(property, toPrintableString(obj)).throwTypeError(realm)
        return success
    }

    @JvmStatic
    fun createDataProperty(realm: Realm, target: JSValue, property: JSValue, value: JSValue): Boolean {
        return createDataProperty(target, toPropertyKey(realm, property), value)
    }

    @JvmStatic
    @ECMAImpl("7.3.5")
    fun createDataProperty(target: JSValue, property: PropertyKey, value: JSValue): Boolean {
        ecmaAssert(target is JSObject)
        return target.defineOwnProperty(property, Descriptor(value, Descriptor.DEFAULT_ATTRIBUTES))
    }

    @JvmStatic
    fun createDataPropertyOrThrow(realm: Realm, target: JSValue, property: JSValue, value: JSValue): Boolean {
        return createDataPropertyOrThrow(realm, target, toPropertyKey(realm, property), value)
    }

    @JvmStatic
    @ECMAImpl("7.3.7")
    fun createDataPropertyOrThrow(realm: Realm, target: JSValue, property: PropertyKey, value: JSValue): Boolean {
        if (!createDataProperty(target, property, value))
            Errors.StrictModeFailedSet(property, toPrintableString(target)).throwTypeError(realm)
        return true
    }

    @JvmStatic
    fun definePropertyOrThrow(realm: Realm, target: JSValue, property: JSValue, descriptor: Descriptor): Boolean {
        return definePropertyOrThrow(realm, target, toPropertyKey(realm, property), descriptor)
    }

    @JvmStatic
    @ECMAImpl("7.3.8")
    fun definePropertyOrThrow(realm: Realm, target: JSValue, property: PropertyKey, descriptor: Descriptor): Boolean {
        ecmaAssert(target is JSObject)
        if (!target.defineOwnProperty(property, descriptor))
            Errors.StrictModeFailedSet(property, toPrintableString(target)).throwTypeError(realm)
        return true
    }

    @JvmStatic
    @ECMAImpl("7.3.9")
    fun deletePropertyOrThrow(realm: Realm, target: JSValue, property: PropertyKey): Boolean {
        ecmaAssert(target is JSObject)
        if (!target.delete(property))
            Errors.StrictModeFailedDelete(property, toPrintableString(target)).throwTypeError(realm)
        return true
    }

    @JvmStatic
    @ECMAImpl("7.3.10")
    fun getMethod(realm: Realm, value: JSValue, key: JSValue): JSValue {
        val func = getV(realm, value, key)
        if (func is JSUndefined || func is JSNull)
            return JSUndefined
        if (!isCallable(func))
            Errors.FailedCall(toPrintableString(func)).throwTypeError(realm)
        return func
    }

    @JvmStatic
    @ECMAImpl("7.3.11")
    fun hasProperty(value: JSValue, property: PropertyKey): Boolean {
        ecmaAssert(value is JSObject)
        return value.hasProperty(property)
    }

    @JvmStatic
    @ECMAImpl("7.3.12")
    fun hasOwnProperty(value: JSValue, property: PropertyKey): Boolean {
        ecmaAssert(value is JSObject)
        val desc = value.getOwnProperty(property)
        return desc != JSUndefined
    }

    @JvmStatic
    @ECMAImpl("7.3.13")
    fun call(realm: Realm, function: JSValue, arguments: JSArguments): JSValue {
        if (!isCallable(function))
            Errors.FailedCall(toPrintableString(function)).throwTypeError(realm)
        return function.call(arguments)
    }

    @JvmOverloads
    fun call(realm: Realm, function: JSValue, thisValue: JSValue, arguments: List<JSValue> = emptyList()): JSValue {
        return call(realm, function, JSArguments(arguments, thisValue))
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
        newTarget: JSValue = constructor
    ): JSValue {
        return construct(constructor, JSArguments(arguments, newTarget = newTarget))
    }

    @JvmStatic
    @ECMAImpl("7.3.15")
    fun setIntegrityLevel(realm: Realm, obj: JSObject, level: IntegrityLevel): Boolean {
        if (!obj.preventExtensions())
            return false
        val keys = obj.ownPropertyKeys(onlyEnumerable = false)
        if (level == IntegrityLevel.Sealed) {
            keys.forEach { key ->
                definePropertyOrThrow(realm, obj, key, Descriptor(JSEmpty, Descriptor.HAS_CONFIGURABLE))
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
                definePropertyOrThrow(realm, obj, key, desc)
            }
            obj.isFrozen = true
        }
        return true
    }

    @JvmStatic
    @ECMAImpl("7.3.17")
    fun createArrayFromList(realm: Realm, elements: List<JSValue>): JSValue {
        val array = arrayCreate(realm, elements.size)
        elements.forEachIndexed { index, value ->
            createDataPropertyOrThrow(realm, array, index.toValue(), value)
        }
        return array
    }

    @JvmStatic
    @ECMAImpl("7.3.18")
    fun lengthOfArrayLike(realm: Realm, target: JSValue): Long {
        ecmaAssert(target is JSObject)
        return toLength(realm, target.get("length")).asLong
    }

    @JvmStatic
    @ECMAImpl("7.3.19")
    fun createListFromArrayLike(realm: Realm, obj: JSValue, types: List<JSValue.Type>? = null): List<JSValue> {
        if (obj !is JSObject)
            Errors.FailedToObject(obj.type).throwTypeError(realm)

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

        val length = lengthOfArrayLike(realm, obj)
        val list = mutableListOf<JSValue>()

        for (i in 0 until length) {
            val next = obj.get(i)
            if (next.type !in elementTypes)
                Errors.TODO("createListFromArray").throwTypeError(realm)
            list.add(next)
        }

        return list
    }

    @JvmStatic
    @ECMAImpl("7.3.20")
    fun invoke(realm: Realm, value: JSValue, property: PropertyKey, arguments: List<JSValue> = emptyList()): JSValue {
        return call(realm, getV(realm, value, property), value, arguments)
    }

    fun invoke(realm: Realm, value: JSValue, property: JSValue, arguments: List<JSValue> = emptyList()): JSValue {
        return invoke(realm, value, toPropertyKey(realm, property), arguments)
    }

    @JvmStatic
    @ECMAImpl("7.3.21")
    fun ordinaryHasInstance(realm: Realm, ctor: JSFunction, target: JSValue): JSValue {
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
            Errors.InstanceOfBadRHS.throwTypeError(realm)

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
    fun speciesConstructor(realm: Realm, obj: JSValue, defaultCtor: JSFunction): JSFunction {
        ecmaAssert(obj is JSObject)
        val ctor = obj.get("constructor")
        if (ctor == JSUndefined)
            return defaultCtor
        if (ctor !is JSObject)
            Errors.BadCtor(toPrintableString(obj)).throwTypeError(realm)

        val species = ctor.get(Realm.`@@species`)
        if (species.isNullish)
            return defaultCtor

        if (isConstructor(species))
            return species

        Errors.SpeciesNotCtor.throwTypeError(realm)
    }

    @JvmStatic
    @ECMAImpl("7.3.23")
    fun enumerableOwnPropertyNames(realm: Realm, target: JSValue, kind: JSObject.PropertyKind): List<JSValue> {
        ecmaAssert(target is JSObject)
        val properties = mutableListOf<JSValue>()
        target.ownPropertyKeys(onlyEnumerable = true).forEach { property ->
            if (property.isSymbol)
                return@forEach
            val desc = target.getOwnPropertyDescriptor(property) ?: return@forEach
            if (!desc.isEnumerable)
                return@forEach
            if (kind == JSObject.PropertyKind.Key) {
                properties.add(toString(realm, property.asValue))
            } else {
                val value = target.get(property)
                if (kind == JSObject.PropertyKind.Value) {
                    properties.add(value)
                } else {
                    properties.add(createArrayFromList(realm, listOf(toString(realm, property.asValue), value)))
                }
            }
        }
        return properties
    }

    @JvmStatic
    @ECMAImpl("7.3.25")
    fun copyDataProperties(
        sourceRealm: Realm,
        target: JSObject,
        source: JSValue,
        excludedItems: List<PropertyKey>
    ): JSObject {
        if (source.isNullish)
            return target
        val from = toObject(sourceRealm, source)
        from.ownPropertyKeys(onlyEnumerable = true).forEach outer@{ key ->
            excludedItems.forEach {
                if (it == key)
                    return@outer
            }
            val desc = from.getOwnPropertyDescriptor(key)
            if (desc != null)
                createDataPropertyOrThrow(target.realm, target, key, from.get(key))
        }
        return target
    }

    @JvmStatic
    @JvmOverloads
    @ECMAImpl("7.4.1")
    fun getIterator(
        realm: Realm,
        obj: JSValue,
        hint: IteratorHint? = IteratorHint.Sync,
        method: JSFunction? = null
    ): IteratorRecord {
        if (hint == IteratorHint.Async)
            TODO()
        val theMethod = method ?: getMethod(realm, obj, Realm.`@@iterator`)
        if (theMethod is JSUndefined)
            Errors.NotIterable(toPrintableString(obj)).throwTypeError(realm)
        val iterator = call(realm, theMethod, obj)
        if (iterator !is JSObject)
            Errors.NonObjectIterator.throwTypeError(realm)
        val nextMethod = getV(realm, iterator, "next".toValue())
        return IteratorRecord(iterator, nextMethod, false)
    }

    @JvmStatic
    @ECMAImpl("7.4.2")
    fun iteratorNext(record: IteratorRecord, value: JSValue? = null): JSObject {
        val realm = record.iterator.realm
        val result = if (value == null) {
            call(realm, record.nextMethod, record.iterator)
        } else {
            call(realm, record.nextMethod, record.iterator, listOf(value))
        }
        if (result !is JSObject)
            Errors.NonObjectIteratorReturn.throwTypeError(realm)
        return result
    }

    @JvmStatic
    @ECMAImpl("7.4.3")
    fun iteratorComplete(result: JSValue): Boolean {
        ecmaAssert(result is JSObject)
        return toBoolean(result.get("done"))
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
        return call(record.iterator.realm, method, record.iterator)
    }

    @JvmStatic
    @ECMAImpl("7.4.8")
    fun createIterResultObject(realm: Realm, value: JSValue, done: Boolean): JSValue {
        val obj = JSObject.create(realm)
        createDataPropertyOrThrow(realm, obj, "value".toValue(), value)
        createDataPropertyOrThrow(realm, obj, "done".toValue(), done.toValue())
        return obj
    }

    @JvmStatic
    @ECMAImpl("7.4.10")
    fun iterableToList(realm: Realm, items: JSValue, method: JSValue? = null): List<JSValue> {
        val iteratorRecord = getIterator(realm, items, method = method as? JSFunction)
        val values = mutableListOf<JSValue>()
        while (true) {
            val next = iteratorStep(iteratorRecord)
            if (next == JSFalse)
                break
            values.add(iteratorValue(next))
        }
        return values
    }

//    @JvmStatic @ECMAImpl("8.1.2.1")
//    fun getIdentifierReference(env: EnvRecord?, name: String, isStrict: Boolean): JSReference {
//        return when {
//            env == null -> JSReference(JSUndefined, PropertyKey(name), isStrict)
//            env.hasBinding(name) -> JSReference(env, PropertyKey(name), isStrict)
//            else -> getIdentifierReference(env.outerEnv, name, isStrict)
//        }
//    }
//
//    @JvmStatic @ECMAImpl("8.3.1")
//    fun getActiveScriptOrModule() {
//        TODO()
//    }

//    @JvmStatic @JvmOverloads
//    @ECMAImpl("8.3.2")
//    fun resolveBinding(name: String, env: EnvRecord? = null): JSReference {
//        val actualEnv = env ?: Reeva.activeAgent.runningContext.lexicalEnv!!
//        // TODO: Strict mode checking
//        return getIdentifierReference(actualEnv, name, isStrict())
//    }
//
//    @JvmStatic @ECMAImpl("8.3.3")
//    fun getThisEnvironment(): EnvRecord {
//        // As the spec states, this is guaranteed to resolve without
//        // any NPEs as there is always at least a global environment
//        // with a this-binding
//        var env = Reeva.activeAgent.runningContext.lexicalEnv!!
//        while (!env.hasThisBinding())
//            env = env.outerEnv!!
//        return env
//    }
//
//    @JvmStatic @ECMAImpl("8.3.4")
//    fun resolveThisBinding(): JSValue {
//        return when (val env = getThisEnvironment()) {
//            is FunctionEnvRecord -> env.getThisBinding()
//            is GlobalEnvRecord -> env.getThisBinding()
//            is ModuleEnvRecord -> env.getThisBinding()
//            else -> unreachable()
//        }
//    }
//
//    @JvmStatic @ECMAImpl("8.3.5")
//    fun getNewTarget(): JSValue {
//        val env = getThisEnvironment()
//        ecmaAssert(env is FunctionEnvRecord)
//        return env.newTarget
//    }

    @JvmStatic
    @ECMAImpl("8.3.6")
    fun getGlobalObject(realm: Realm): JSObject {
        return realm.globalObject
    }

    @JvmStatic
    @ECMAImpl("9.1.6.2")
    fun isCompatiblePropertyDescriptor(realm: Realm, extensible: Boolean, desc: Descriptor, current: Descriptor?): Boolean {
        return validateAndApplyPropertyDescriptor(realm, null, null, extensible, desc, current)
    }

    @JvmStatic
    fun validateAndApplyPropertyDescriptor(
        realm: Realm,
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
                val newAttrs = ((currentDesc.attributes and (Descriptor.CONFIGURABLE or Descriptor.ENUMERABLE))
                    or Descriptor.HAS_CONFIGURABLE or Descriptor.HAS_ENUMERABLE)

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
                    if (!newDesc.getActualValue(realm, target).sameValue(currentDesc.getActualValue(realm, target)))
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
                currentDesc.setActualValue(realm, target, newDesc.getActualValue(realm, target))

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
    fun ordinaryObjectCreate(realm: Realm, proto: JSValue, additionalInternalSlotList: List<SlotName>): JSObject {
        // Spec deviation: [[Prototype]] and [[Extensible]] are not implemented as slots,
        // as they are present in every Object, and thus are just plain fields in the
        // Kotlin side
        val obj = JSObject.create(realm, proto)
        additionalInternalSlotList.forEach {
            obj.addSlot(it, JSUndefined)
        }
        return obj
    }

    @JvmStatic
    @ECMAImpl("9.1.13")
    fun ordinaryCreateFromConstructor(
        realm: Realm,
        constructor: JSValue,
        intrinsicDefaultProto: JSObject,
        internalSlotsList: List<SlotName> = emptyList()
    ): JSObject {
        val proto = getPrototypeFromConstructor(constructor, intrinsicDefaultProto)
        return ordinaryObjectCreate(realm, proto, internalSlotsList)
    }

    @JvmStatic
    @ECMAImpl("9.1.14")
    fun getPrototypeFromConstructor(constructor: JSValue, intrinsicDefaultProto: JSObject): JSObject {
        ecmaAssert(isCallable(constructor))
        val proto = (constructor as JSObject).get("prototype")
        if (proto is JSObject)
            return proto
        return intrinsicDefaultProto
    }

    @JvmStatic
    @ECMAImpl("9.1.15")
    fun requireInternalSlot(obj: JSValue, slot: SlotName): Boolean {
        contract {
            returns(true) implies (obj is JSObject)
        }
        return obj is JSObject && obj.hasSlot(slot)
    }

//    @JvmStatic @ECMAImpl("9.2.1.1")
//    fun prepareForOrdinaryCall(function: JSFunction, newTarget: JSValue): ExecutionContext {
//        ecmaAssert(newTarget is JSUndefined || newTarget is JSObject)
//        val calleeContext = ExecutionContext(function.realm, function)
//        val localEnv = FunctionEnvRecord.create(function, newTarget)
//        calleeContext.lexicalEnv = localEnv
//        calleeContext.variableEnv = localEnv
//        Agent.pushContext(calleeContext)
//        return calleeContext
//    }

    // TODO: Do we really need the calleeContext here?
    // prepareForOrdinaryCall will have just set it as the running
    // execution context
//    @JvmStatic @ECMAImpl("9.2.1.2")
//    fun ordinaryCallBindThis(function: JSFunction, calleeContext: ExecutionContext, thisArgument: JSValue): JSValue {
//        if (function.thisMode == JSFunction.ThisMode.Lexical)
//            return JSUndefined
//        val thisValue = if (function.thisMode == JSFunction.ThisMode.Strict) {
//            thisArgument
//        } else if (thisArgument == JSUndefined || thisArgument == JSNull) {
//            function.realm.globalEnv.globalThis
//        } else toObject(thisArgument)
//
//        val localEnv = calleeContext.lexicalEnv
//        ecmaAssert(localEnv is FunctionEnvRecord)
//        return localEnv.bindThisValue(thisValue)
//    }

//    @JvmStatic @JvmOverloads
//    @ECMAImpl("9.2.5")
//    fun makeConstructor(function: JSFunction, writablePrototype: Boolean = true, prototype: JSObject? = null) {
//        ecmaAssert(!hasOwnProperty(function, "prototype".key()))
//        ecmaAssert(!function.isConstructable)
//        ecmaAssert(function.isExtensible())
//
//        function.constructorKind = JSFunction.ConstructorKind.Base
//        function.isConstructable = true
//
//        val realProto = prototype ?: run {
//            val proto = JSObject.create(function.realm)
//            var attrs = Descriptor.HAS_BASIC or Descriptor.CONFIGURABLE
//            if (writablePrototype)
//                attrs = attrs or Descriptor.WRITABLE
//            definePropertyOrThrow(proto, "constructor".key(), Descriptor(function, attrs))
//            proto
//        }
//        var attrs = Descriptor.HAS_BASIC
//        if (writablePrototype)
//            attrs = attrs or Descriptor.WRITABLE
//        definePropertyOrThrow(function, "prototype".key(), Descriptor(realProto, attrs))
//    }

//    @JvmStatic @ECMAImpl("9.2.7")
//    fun makeMethod(function: JSFunction, homeObject: JSObject): JSValue {
//        function.homeObject = homeObject
//        return JSUndefined
//    }

    @JvmStatic
    @JvmOverloads
    @ECMAImpl("9.2.8")
    fun setFunctionName(realm: Realm, function: JSFunction, name: PropertyKey, prefix: String? = null): Boolean {
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
            realm,
            function,
            "name".toValue(),
            Descriptor(nameString.toValue(), Descriptor.CONFIGURABLE)
        )
    }

    @JvmStatic
    @ECMAImpl("9.4.1.3")
    fun boundFunctionCreate(realm: Realm, targetFunction: JSFunction, arguments: JSArguments): JSFunction {
        val proto = targetFunction.getPrototype()
        return JSBoundFunction.create(realm, targetFunction, arguments, proto)
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

    fun arrayCreate(realm: Realm, length: Int, proto: JSValue = realm.arrayProto): JSObject {
        return arrayCreate(realm, length.toLong(), proto)
    }

    @JvmStatic
    @JvmOverloads
    @ECMAImpl("9.4.2.2")
    fun arrayCreate(realm: Realm, length: Long, proto: JSValue = realm.arrayProto): JSObject {
        if (length >= MAX_32BIT_INT - 1)
            Errors.InvalidArrayLength(length).throwRangeError(realm)
        val array = JSArrayObject.create(realm, proto)
        array.indexedProperties.setArrayLikeSize(length)
        return array
    }

    @JvmStatic
    @ECMAImpl("9.4.2.3")
    fun arraySpeciesCreate(realm: Realm, originalArray: JSObject, length: Int) =
        arraySpeciesCreate(realm, originalArray, length.toLong())

    @JvmStatic
    @ECMAImpl("9.4.2.3")
    fun arraySpeciesCreate(realm: Realm, originalArray: JSObject, length: Long): JSValue {
        if (!isArray(realm, originalArray))
            return arrayCreate(realm, length)
        var ctor = originalArray.get("constructor")
        if (isConstructor(ctor)) {
            val ctorRealm = (ctor as JSObject).realm
            if (realm != ctorRealm && ctor.sameValue(ctorRealm.arrayCtor))
                ctor = JSUndefined
        }
        if (ctor is JSObject) {
            ctor = ctor.get(Realm.`@@species`)
            if (ctor == JSNull)
                ctor = JSUndefined
        }
        if (ctor == JSUndefined)
            return arrayCreate(realm, length)
        if (!isConstructor(ctor))
            Errors.SpeciesNotCtor.throwTypeError(realm)
        return construct(ctor, listOf(length.toValue()))
    }

    @JvmStatic
    @ECMAImpl("9.4.4.6")
    fun createUnmappedArgumentsObject(realm: Realm, arguments: List<JSValue>): JSValue {
        val obj = JSUnmappedArgumentsObject.create(realm)
        definePropertyOrThrow(
            realm,
            obj,
            "length".key(),
            Descriptor(arguments.size.toValue(), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        )
        arguments.forEachIndexed { index, value ->
            createDataPropertyOrThrow(realm, obj, index.key(), value)
        }
        definePropertyOrThrow(
            realm,
            obj,
            Realm.`@@iterator`.key(),
            Descriptor(realm.arrayProto.get("values"), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        )

        val calleeGetter = JSNativeFunction.fromLambda(realm, "get callee", 0) { r, _ ->
            Errors.CalleePropertyAccess.throwTypeError(r)
        }

        val calleeSetter = JSNativeFunction.fromLambda(realm, "set callee", 1) { r, _ ->
            Errors.CalleePropertyAccess.throwTypeError(r)
        }

        definePropertyOrThrow(
            realm,
            obj,
            "callee".key(),
            Descriptor(JSAccessor(calleeGetter, calleeSetter), 0),
        )

        return obj
    }

    @JvmStatic @ECMAImpl("9.4.4.7")
    fun createMappedArgumentsObject(
        realm: Realm,
        function: JSObject,
        parameters: ParameterList,
        arguments: List<JSValue>,
        envRecord: EnvRecord,
    ): JSMappedArgumentsObject {
        val obj = JSMappedArgumentsObject.create(realm)
        val map = JSObject.create(realm, JSNull)
        obj.parameterMap = map

        for ((index, argument) in arguments.withIndex())
            createDataPropertyOrThrow(realm, obj, PropertyKey.from(index), argument)

        definePropertyOrThrow(
            realm,
            obj,
            "length".key(),
            Descriptor(arguments.size.toValue(), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        )

        val mappedNames = mutableSetOf<String>()

        for (index in parameters.lastIndex downTo 0) {
            val name = parameters[index].identifier.identifierName
            if (name !in mappedNames) {
                mappedNames.add(name)
                if (index < arguments.size) {
                    val getter = JSNativeFunction.fromLambda(realm, "", 0) { _, _ ->
                        envRecord.getBindingValue(name, false)
                    }
                    val setter = JSNativeFunction.fromLambda(realm, "", 1) { _, args ->
                        envRecord.setMutableBinding(name, args.argument(0), false)
                        JSUndefined
                    }

                    map.defineOwnProperty(index, JSAccessor(getter, setter), Descriptor.CONFIGURABLE)
                }
            }
        }

        definePropertyOrThrow(
            realm,
            obj,
            Realm.`@@iterator`,
            Descriptor(realm.arrayProto.get("values"), Descriptor.CONFIGURABLE or Descriptor.WRITABLE),
        )

        definePropertyOrThrow(
            realm,
            obj,
            "callee".key(),
            Descriptor(function, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        )

        return obj
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
    fun integerIndexedElementSet(realm: Realm, obj: JSValue, index: JSValue, value: JSValue) {
        ecmaAssert(obj is JSIntegerIndexedObject)

        if (index !is JSNumber || !isValidIntegerIndex(obj, index))
            return

        val kind = obj.getSlotAs<TypedArrayKind>(SlotName.TypedArrayKind)
        val numValue = if (kind.isBigInt) {
            value.toBigInt(realm)
        } else value.toNumber(realm)

        val offset = obj.getSlotAs<Int>(SlotName.ByteOffset)
        val indexedPosition = (index.asInt * kind.size) + offset
        setValueInBuffer(
            realm,
            obj.getSlotAs(SlotName.ViewedArrayBuffer),
            indexedPosition,
            kind,
            numValue,
            true,
            TypedArrayOrder.Unordered
        )
    }

//    @JvmStatic @ECMAImpl("9.4.4.7.1")
//    fun makeArgGetter(name: String, env: EnvRecord): NativeGetterSignature {
//        return { _ ->
//            val function = Reeva.activeAgent.runningContext.function
//            expect(function != null)
//            env.getBindingValue(name, false)
//        }
//    }

//    @JvmStatic @ECMAImpl("9.4.4.7.2")
//    fun makeArgSetter(name: String, env: EnvRecord): NativeSetterSignature {
//        return { _, newValue ->
//            val function = Reeva.activeAgent.runningContext.function
//            expect(function != null)
//            env.setBinding(name, newValue, false)
//        }
//    }

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

//    @JvmStatic @ECMAImpl("12.3.3")
//    fun evaluatePropertyAccessWithExpressionKey(baseValue: JSValue, property: JSValue, isStrict: Boolean): JSValue {
//        val propertyValue = getValue(property)
//        val bv = requireObjectCoercible(baseValue)
//        val propertyKey = toPropertyKey(propertyValue)
//        return JSReference(bv, propertyKey, isStrict)
//    }

//    @JvmStatic @ECMAImpl("12.3.4")
//    fun evaluatePropertyAccessWithIdentifierKey(baseValue: JSValue, property: String, isStrict: Boolean): JSValue {
//        val bv = requireObjectCoercible(baseValue)
//        return JSReference(bv, PropertyKey(property), isStrict)
//    }

//    @JvmStatic @ECMAImpl("12.3.5.1.1")
//    fun evaluateNew(target: JSValue, arguments: Array<JSValue>): JSValue {
//        val constructor = getValue(target)
//        if (!isConstructor(constructor))
//            Errors.NotACtor(toPrintableString(target)).throwTypeError(realm)
//        return construct(constructor, arguments.toList())
//    }

//    @JvmStatic @ECMAImpl("12.3.6.2")
//    fun evaluateCall(target: JSValue, reference: JSValue, arguments: List<JSValue>, tailPosition: Boolean): JSValue {
//        val thisValue = if (reference is JSReference) {
//            if (reference.isPropertyReference) {
//                reference.getThisValue()
//            } else {
//                ecmaAssert(reference.baseValue is EnvRecord)
//                reference.baseValue.withBaseObject()
//            }
//        } else JSUndefined
//
//        if (!isCallable(target))
//            Errors.NotCallable(toPrintableString(target)).throwTypeError(realm)
//        if (tailPosition)
//            TODO()
//        return call(target, thisValue, arguments.toList())
//    }
//
//    @JvmStatic @ECMAImpl("12.3.7.2")
//    fun getSuperConstructor(): JSValue {
//        val env = getThisEnvironment()
//        ecmaAssert(env is FunctionEnvRecord)
//        val activeFunction = env.function
//        return activeFunction.getPrototype()
//    }
//
//    @JvmStatic @ECMAImpl("12.3.7.3")
//    fun makeSuperPropertyReference(thisValue: JSValue, key: PropertyKey, isStrict: Boolean): JSReference {
//        val env = getThisEnvironment()
//        ecmaAssert(env.hasSuperBinding())
//        val baseValue = (env as FunctionEnvRecord).getSuperBase()
//        requireObjectCoercible(baseValue)
//        return JSSuperReference(baseValue, key, isStrict, thisValue)
//    }

//    @JvmStatic @ECMAImpl("12.5.3")
//    fun deleteOperator(value: JSValue): JSValue {
//        if (value !is JSReference)
//            return JSTrue
//        if (value.isUnresolvableReference) {
//            ecmaAssert(!value.isStrict)
//            return JSTrue
//        }
//        return if (value.isPropertyReference) {
//            if (value.isSuperReference)
//                TODO()
//            expect(value.baseValue is JSValue)
//            val baseObj = toObject(value.baseValue)
//            val deleteStatus = baseObj.delete(value.name)
//            if (!deleteStatus && value.isStrict)
//                TODO()
//            deleteStatus.toValue()
//        } else {
//            ecmaAssert(value.baseValue is EnvRecord)
//            expect(value.name.isString)
//            value.baseValue.deleteBinding(value.name.asString).toValue()
//        }
//    }

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
    fun instanceofOperator(realm: Realm, target: JSValue, ctor: JSValue): JSValue {
        if (ctor !is JSObject)
            Errors.InstanceOfBadRHS.throwTypeError(realm)

        val instOfHandler = getMethod(realm, target, Realm.`@@hasInstance`)
        if (instOfHandler !is JSUndefined) {
            val temp = call(realm, instOfHandler, ctor, listOf(target))
            return toBoolean(temp).toValue()
        }

        if (!isCallable(ctor))
            Errors.InstanceOfBadRHS.throwTypeError(realm)

        return ordinaryHasInstance(realm, ctor, target)
    }

    @JvmStatic
    @ECMAImpl("12.15.5")
    fun applyStringOrNumericBinaryOperator(realm: Realm, lhs: JSValue, rhs: JSValue, op: String): JSValue {
        if (op == "+") {
            val lprim = toPrimitive(realm, lhs)
            val rprim = toPrimitive(realm, rhs)
            if (lprim.isString || rprim.isString) {
                val lstr = toString(realm, lprim)
                val rstr = toString(realm, rprim)
                return JSString(lstr.string + rstr.string)

            }
        }

        val lnum = toNumeric(realm, lhs)
        val rnum = toNumeric(realm, rhs)
        if (lnum.type != rnum.type)
            Errors.BadOperator(op, lnum.type, rnum.type).throwTypeError(realm)

        return if (lnum.type == JSValue.Type.BigInt) {
            when (op) {
                "**" -> bigintExponentiate(realm, lnum, rnum)
                "*" -> bigintMultiply(lnum, rnum)
                "/" -> bigintDivide(realm, lnum, rnum)
                "%" -> bigintRemainder(realm, lnum, rnum)
                "+" -> bigintAdd(lnum, rnum)
                "-" -> bigintSubtract(lnum, rnum)
                "<<" -> bigintLeftShift(realm, lnum, rnum)
                ">>" -> bigintSignedRightShift(realm, lnum, rnum)
                ">>>" -> bigintUnsignedRightShift(realm, lnum, rnum)
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
            "<<" -> numericLeftShift(realm, lnum, rnum)
            ">>" -> numericSignedRightShift(realm, lnum, rnum)
            ">>>" -> numericUnsignedRightShift(realm, lnum, rnum)
            "&" -> numericBitwiseAND(realm, lnum, rnum)
            "^" -> numericBitwiseXOR(realm, lnum, rnum)
            "|" -> numericBitwiseOR(realm, lnum, rnum)
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
        realm: Realm,
        constructor: JSObject,
        newTarget_: JSValue,
        kind: FunctionKind,
        args: JSArguments
    ): JSValue {
        val agent = Reeva.activeAgent

        // TODO: Figure out the caller vs callee realm separation
        agent.hostHooks.ensureCanCompileStrings(realm, realm)

        val newTarget = if (newTarget_ == JSUndefined) constructor else newTarget_ as JSObject

        val fallbackProto = when (kind) {
            FunctionKind.Normal -> newTarget.realm.functionProto
            FunctionKind.Generator -> newTarget.realm.generatorFunctionProto
            else -> TODO()
        }

        val bodyArgString = if (args.isEmpty()) "" else args.last().toJSString(realm).string

        val argString = if (args.size <= 1) "" else buildString {
            for ((index, arg) in args.dropLast(1).withIndex()) {
                val string = arg.toJSString(realm).string
                if (index != 0)
                    append(',')
                append(string)
            }
        }

        val bodyString = "\n$bodyArgString\n"
        val prefix = kind.prefix
        val sourceString = "$prefix anonymous($argString\n) {$bodyString}"

        // TODO: 20.2.1.1.1 step 24:
        // NOTE: The parameters and body are parsed separately to ensure that each is valid alone. For
        // example, new Function("/*", "*/ ) {") is not legal.
        val parser = Parser(sourceString)

        // TODO: Get access to the Agent for printAST/printIR

        val functionNode = when (val result = parser.parseFunction(kind)) {
            is ParsingResult.InternalError -> throw result.cause
            is ParsingResult.ParseError -> {
                val exception = JSSyntaxErrorObject.create(realm, result.reason)
                throw ThrowException(exception)
            }
            is ParsingResult.Success -> result.node as FunctionDeclarationNode
        }

        if (agent.printAST) {
            functionNode.debugPrint()
            println("\n")
        }

        val proto = getPrototypeFromConstructor(constructor, fallbackProto)

        val ir = Transformer().transform(functionNode)

        if (agent.printIR) {
            IrPrinter(ir).print()
            println("\n")
        }

        IrPrinter(ir).print()
        println("\n")

        // TODO: This globalEnv access will throw an exception if this is access
        // after the entire script has stopped running (for example, if a function
        // containing a new Function call is executed on the JVM side). This will
        // somehow have to climb up the EnvRecord stack of the calling function.
        val function = Interpreter.wrap(ir, realm, realm.globalEnv, kind)
        function.setPrototype(proto)

        setFunctionName(realm, function, "anonymous".key())

        when (kind) {
            FunctionKind.Normal -> {
                // TODO:
                // makeConstructor(function)
            }
            FunctionKind.Generator -> {
                val prototype = JSObject.create(realm, realm.generatorFunctionProto)
                definePropertyOrThrow(realm, function, "prototype".key(), Descriptor(prototype, Descriptor.WRITABLE))
            }
            else -> TODO()
        }

        return function
    }

    @JvmStatic
    @ECMAImpl("20.4.1.11")
    fun makeTime(realm: Realm, hour: JSValue, min: JSValue, sec: JSValue, ms: JSValue): JSValue {
        if (!hour.isFinite || !min.isFinite || !sec.isFinite || !ms.isFinite)
            return JSNumber.NaN

        val h = toIntegerOrInfinity(realm, hour).asInt
        val m = toIntegerOrInfinity(realm, min).asInt
        val s = toIntegerOrInfinity(realm, sec).asInt
        val milli = toIntegerOrInfinity(realm, ms).asInt

        val lt = LocalTime.of(h, m, s, milli * 1_000_000)

        return (lt.second * 1000 + lt.nano / 1_000_000).toValue()
    }

    @JvmStatic
    @ECMAImpl("20.4.1.12")
    fun makeDay(realm: Realm, year: JSValue, month: JSValue, day: JSValue): JSValue {
        if (!year.isFinite || !month.isFinite || !day.isFinite)
            return JSNumber.NaN

        val y = toIntegerOrInfinity(realm, year).asInt
        val m = toIntegerOrInfinity(realm, month).asInt
        val d = toIntegerOrInfinity(realm, day).asInt

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
    fun trimString(realm: Realm, string: JSValue, where: TrimType): String {
        val str = toString(realm, requireObjectCoercible(realm, string)).string

        fun removable(ch: Char) = isWhitespace(ch) || isLineTerminator(ch)

        return when (where) {
            TrimType.Start -> str.dropWhile(::removable)
            TrimType.End -> str.dropLastWhile(::removable)
            TrimType.StartEnd -> str.dropWhile(::removable).dropLastWhile(::removable)
        }
    }

    private fun isWhitespace(ch: Char) = ch == '\u0009' || ch == '\u000b' || ch == '\u000c' || ch == ' ' ||
        ch == '\u00a0' || ch == '\uffef' || ch.isSpaceSeparator()

    private fun isLineTerminator(ch: Char) = ch == '\u000a' || ch == '\u000d' || ch == '\u2028' || ch == '\u2029'

    @JvmStatic
    @ECMAImpl("21.2.3.2.1")
    fun regExpAlloc(realm: Realm, newTarget: JSValue): JSObject {
        val slots = listOf(SlotName.RegExpMatcher, SlotName.OriginalSource, SlotName.OriginalFlags)
        val obj = ordinaryCreateFromConstructor(realm, newTarget, realm.regExpProto, slots)
        definePropertyOrThrow(realm, obj, "lastIndex".key(), Descriptor(JSEmpty, attrs { +writ - enum - conf }))
        return obj
    }

    @JvmStatic
    @ECMAImpl("21.2.3.2.2")
    fun regExpInitialize(realm: Realm, obj: JSObject, patternArg: JSValue, flagsArg: JSValue): JSObject {
        val pattern = if (patternArg == JSUndefined) "" else toString(realm, patternArg).string
        val flags = if (flagsArg == JSUndefined) "" else toString(realm, flagsArg).string

        val invalidFlag = flags.firstOrNull { JSRegExpObject.Flag.values().none { flag -> flag.char == it } }
        if (invalidFlag != null)
            Errors.RegExp.InvalidFlag(invalidFlag).throwSyntaxError(realm)
        if (flags.toCharArray().distinct().size != flags.length)
            Errors.RegExp.DuplicateFlag.throwSyntaxError(realm)

        obj.setSlot(SlotName.OriginalSource, pattern)
        obj.setSlot(SlotName.OriginalFlags, flags)
        obj.setSlot(SlotName.RegExpMatcher, JSRegExpObject.makeClosure(realm, pattern, flags))
        set(realm, obj, "lastIndex".key(), 0.toValue(), true)

        return obj
    }

    @JvmStatic
    @ECMAImpl("21.2.3.2.4")
    fun regExpCreate(realm: Realm, pattern: JSValue, flags: JSValue): JSObject {
        val obj = regExpAlloc(realm, realm.regExpCtor)
        return regExpInitialize(realm, obj, pattern, flags)
    }

    @ECMAImpl("21.2.5.2.1")
    fun regExpExec(realm: Realm, thisValue: JSValue, string: JSValue, methodName: String): JSValue {
        ecmaAssert(thisValue is JSObject)
        ecmaAssert(string is JSString)

        val exec = thisValue.get("exec")
        if (isCallable(exec)) {
            val result = call(realm, exec, thisValue, listOf(string))
            if (result !is JSObject && result != JSNull)
                Errors.RegExp.ExecBadReturnType.throwTypeError(realm)
            return result
        }
        if (thisValue !is JSRegExpProto)
            Errors.IncompatibleMethodCall("RegExp.prototype$methodName").throwTypeError(realm)
        return regExpBuiltinExec(realm, thisValue, string)
    }

    @ECMAImpl("21.2.5.2.2")
    fun regExpBuiltinExec(realm: Realm, thisValue: JSValue, string: JSValue): JSValue {
        ecmaAssert(thisValue is JSObject)
        ecmaAssert(string is JSString)
        requireInternalSlot(thisValue, SlotName.RegExpMatcher)

        val length = string.string.length
        val bytes = string.string.toByteArray()
        val flags = thisValue.getSlotAs<String>(SlotName.OriginalFlags)
        val global = JSRegExpObject.Flag.Global.char in flags
        val sticky = JSRegExpObject.Flag.Sticky.char in flags
        val fullUnicode = JSRegExpObject.Flag.Unicode.char in flags
        var lastIndex = if (global || sticky) {
            toLength(realm, thisValue.get("lastIndex")).asInt
        } else 0

        val regex = thisValue.getSlotAs<org.joni.Regex>(SlotName.RegExpMatcher)
        val matcher = regex.matcher(bytes, 0, length)

        var matchSucceeded = false
        while (!matchSucceeded) {
            if (lastIndex > length) {
                if (global || sticky)
                    set(realm, thisValue, "lastIndex".key(), 0.toValue(), true)
                return JSNull
            }
            val result = matcher.search(lastIndex, length - lastIndex, Option.DEFAULT)
            if (result == Matcher.FAILED || result == Matcher.INTERRUPTED) {
                if (sticky) {
                    set(realm, thisValue, "lastIndex".key(), 0.toValue(), true)
                    return JSNull
                }
                if (fullUnicode)
                    TODO()
                lastIndex++
            } else {
                matchSucceeded = true
            }
        }

        val eagerRegion = matcher.eagerRegion

        val arr = arrayCreate(realm, eagerRegion.numRegs)
        createDataPropertyOrThrow(realm, arr, "index".key(), lastIndex.toValue())
        createDataPropertyOrThrow(realm, arr, "input".key(), string)

        val matchedSubstr = string.string.substring(lastIndex, eagerRegion.end[0])
        createDataPropertyOrThrow(realm, arr, 0.key(), matchedSubstr.toValue())

        val groupNames = regex.namedBackrefIterator().asSequence().toList()
        val groups = if (groupNames.isNotEmpty()) {
            JSObject.create(realm, JSNull)
        } else JSUndefined

        createDataPropertyOrThrow(realm, arr, "groups".key(), groups)

        for (i in 1 until eagerRegion.numRegs) {
            val capturedValue = if (fullUnicode) {
                TODO()
            } else {
                string.string.substring(eagerRegion.beg[i], eagerRegion.end[i])
            }.toValue()
            createDataPropertyOrThrow(realm, arr, i.key(), capturedValue)
            val namedGroup = groupNames.firstOrNull { i in it.backRefs }
            if (namedGroup != null) {
                createDataPropertyOrThrow(
                    realm,
                    groups,
                    thisValue.getSlotAs<String>(SlotName.OriginalSource).substring(namedGroup.nameP, namedGroup.nameEnd)
                        .key(),
                    capturedValue
                )
            }
        }

        return arr
    }

    @ECMAImpl("22.1.5.1")
    fun createArrayIterator(realm: Realm, array: JSObject, kind: JSObject.PropertyKind): JSValue {
        return JSArrayIterator.create(realm, array, 0, kind)
    }

    @JvmStatic
    @ECMAImpl("22.2.4.1")
    fun typedArraySpeciesCreate(realm: Realm, exemplar: JSValue, arguments: JSArguments): JSObject {
        ecmaAssert(exemplar is JSObject && exemplar.hasSlots(SlotName.TypedArrayName, SlotName.ContentType))

        val kind = exemplar.getSlotAs<TypedArrayKind>(SlotName.TypedArrayKind)
        val defaultConstructor = kind.getCtor(exemplar.realm)
        val constructor = speciesConstructor(realm, exemplar, defaultConstructor)
        val result = typedArrayCreate(realm, constructor, arguments)
        if (result.getSlot(SlotName.ContentType) != exemplar.getSlot(SlotName.ContentType))
            Errors.TODO("typedArraySpeciesCreate").throwTypeError(realm)
        return result
    }

    @JvmStatic
    @ECMAImpl("22.2.4.2")
    fun typedArrayCreate(realm: Realm, constructor: JSValue, arguments: JSArguments): JSObject {
        val newTypedArray = construct(constructor, arguments)
        validateTypedArray(realm, newTypedArray)
        expect(newTypedArray is JSObject)
        if (arguments.size == 1) {
            val arg = arguments.argument(0)
            if (arg is JSNumber && newTypedArray.getSlotAs<Int>(SlotName.ArrayLength) != arg.asInt)
                Errors.TODO("typedArrayCreate").throwTypeError(realm)
        }
        return newTypedArray
    }

    @JvmStatic
    @ECMAImpl("22.2.4.3")
    fun validateTypedArray(realm: Realm, obj: JSValue) {
        if (!requireInternalSlot(obj, SlotName.TypedArrayName))
            Errors.TODO("validateTypedArray requireInternalSlot").throwTypeError(realm)
        ecmaAssert(obj.hasSlot(SlotName.ViewedArrayBuffer))
        val buffer = obj.getSlotAs<JSObject>(SlotName.ViewedArrayBuffer)
        if (isDetachedBuffer(buffer))
            Errors.TODO("validateTypedArray isDetachedBuffer").throwTypeError(realm)
    }

    @JvmStatic
    @ECMAImpl("23.1.1.2")
    fun addEntriesFromIterable(realm: Realm, target: JSObject, iterable: JSValue, adder: JSValue): JSValue {
        if (!isCallable(adder))
            Errors.TODO("addEntriesFromIterable 1").throwTypeError(realm)

        // TODO: This whole method is super scuffed
        ecmaAssert(iterable != JSUndefined && iterable != JSNull)
        val record = getIterator(realm, iterable) as? IteratorRecord ?: return JSEmpty
        while (true) {
            val next = iteratorStep(record)
            if (next == JSFalse)
                return target
            val nextItem = iteratorValue(next)
            if (nextItem !is JSObject) {
                iteratorClose(record, JSEmpty)
                Errors.TODO("addEntriesFromIterable 2").throwTypeError(realm)
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
                call(realm, adder, target, listOf(key, value))
            } catch (e: Throwable) {
                iteratorClose(record, JSEmpty)
                throw e
            }
        }
    }

    @JvmStatic
    @ECMAImpl("24.1.2.1")
    fun allocateArrayBuffer(realm: Realm, constructor: JSValue, byteLength: Int): JSObject {
        val slots = listOf(SlotName.ArrayBufferData, SlotName.ArrayBufferByteLength, SlotName.ArrayBufferDetachKey)
        val obj = ordinaryCreateFromConstructor(realm, constructor, realm.arrayBufferProto, slots)
        val block = createByteDataBlock(realm, byteLength)
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
    fun detachArrayBuffer(realm: Realm, arrayBuffer: JSValue, key: JSValue = JSUndefined) {
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
            Errors.ArrayBuffer.BadDetachKey(expectedKey.toPrintableString(), key.toPrintableString()).throwTypeError(realm)

        arrayBuffer.setSlot(SlotName.ArrayBufferData, null)
        arrayBuffer.setSlot(SlotName.ArrayBufferByteLength, 0)
    }

    @JvmStatic
    @ECMAImpl("24.1.2.4")
    fun cloneArrayBuffer(
        realm: Realm,
        srcBuffer: JSValue,
        srcByteOffset: Int,
        srcLength: Int,
        cloneConstructor: JSValue
    ): JSObject {
        ecmaAssert(srcBuffer is JSObject && srcBuffer.hasSlot(SlotName.ArrayBufferData))
        ecmaAssert(cloneConstructor.isConstructor)

        val targetBuffer = allocateArrayBuffer(realm, cloneConstructor, srcLength)
        if (isDetachedBuffer(srcBuffer))
            Errors.TODO("cloneArrayBuffer isDetachedBuffer").throwTypeError(realm)
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
        isLittleEndian: Boolean = Reeva.activeAgent.isLittleEndian
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
    fun numericToRawBytes(realm: Realm, type: TypedArrayKind, value: JSValue, isLittleEndian: Boolean): ByteArray {
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
                val numValue = convOp(realm, value).let {
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
        realm: Realm,
        arrayBuffer: JSValue,
        byteIndex: Int,
        type: TypedArrayKind,
        value: JSValue,
        isTypedArray: Boolean,
        order: TypedArrayOrder,
        isLittleEndian: Boolean = Reeva.activeAgent.isLittleEndian
    ) {
        ecmaAssert(arrayBuffer is JSObject)
        ecmaAssert(!isDetachedBuffer(arrayBuffer))
        ecmaAssert(if (value is JSBigInt) isBigIntElementType(type) else value is JSNumber)

        val block = arrayBuffer.getSlotAs<DataBlock>(SlotName.ArrayBufferData)
        ecmaAssert(byteIndex + type.size <= block.size)

        val rawBytes = numericToRawBytes(realm, type, value, isLittleEndian)
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
        realm: Realm,
        view: JSValue,
        requestIndex: JSValue,
        littleEndian: JSValue,
        kind: TypedArrayKind
    ): JSValue {
        if (!requireInternalSlot(view, SlotName.DataView))
            Errors.TODO("getViewValue requireInternalSlot").throwTypeError(realm)

        val index = requestIndex.toIndex(realm)
        val isLittleEndian = littleEndian.toBoolean()
        val buffer = view.getSlotAs<JSObject>(SlotName.ViewedArrayBuffer)
        if (isDetachedBuffer(buffer))
            Errors.TODO("getViewValue isDetachedBuffer").throwTypeError(realm)

        val viewOffset = view.getSlotAs<Int>(SlotName.ByteOffset)
        val viewSize = view.getSlotAs<Int>(SlotName.ByteLength)
        if (index + kind.size > viewSize)
            Errors.TODO("getViewValue out of range").throwRangeError(realm)

        return getValueFromBuffer(buffer, index + viewOffset, kind, false, TypedArrayOrder.Unordered, isLittleEndian)
    }

    @JvmStatic
    @ECMAImpl("24.3.1.2")
    fun setViewValue(
        realm: Realm,
        view: JSValue,
        requestIndex: JSValue,
        littleEndian: JSValue,
        kind: TypedArrayKind,
        value: JSValue
    ) {
        if (!requireInternalSlot(view, SlotName.DataView))
            Errors.TODO("setViewValue requireInternalSlot").throwTypeError(realm)

        val index = requestIndex.toIndex(realm)
        val numberValue = if (kind.isBigInt) value.toBigInt(realm) else value.toNumber(realm)
        val isLittleEndian = littleEndian.toBoolean()
        val buffer = view.getSlotAs<JSObject>(SlotName.ViewedArrayBuffer)
        if (isDetachedBuffer(buffer))
            Errors.TODO("getViewValue isDetachedBuffer").throwTypeError(realm)

        val viewOffset = view.getSlotAs<Int>(SlotName.ByteOffset)
        val viewSize = view.getSlotAs<Int>(SlotName.ByteLength)
        if (index + kind.size > viewSize)
            Errors.TODO("getViewValue out of range").throwRangeError(realm)

        setValueInBuffer(
            realm,
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
        val resolve = JSResolveFunction.create(promise, resolvedStatus, promise.realm)
        val reject = JSRejectFunction.create(promise, resolvedStatus, promise.realm)
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
    fun newPromiseCapability(realm: Realm, ctor: JSValue): PromiseCapability {
        if (!isConstructor(ctor))
            Errors.TODO("newPromiseCapability").throwTypeError(realm)
        val capability = PromiseCapability(JSEmpty, null, null)
        val executor = JSCapabilitiesExecutor.create((ctor as JSObject).realm, capability)
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
    @ECMAImpl("26.6.1.7")
    internal fun rejectPromise(realm: Realm, promise: JSObject, reason: JSValue): JSValue {
        ecmaAssert(promise.getSlot(SlotName.PromiseState) == PromiseState.Pending)
        val reactions = promise.getSlotAs<List<PromiseReaction>>(SlotName.PromiseRejectReactions).toList()
        promise.setSlot(SlotName.PromiseResult, reason)
        promise.setSlot(SlotName.PromiseFulfillReactions, mutableListOf<PromiseReaction>())
        promise.setSlot(SlotName.PromiseRejectReactions, mutableListOf<PromiseReaction>())
        promise.setSlot(SlotName.PromiseState, PromiseState.Rejected)

        if (!promise.getSlotAs<Boolean>(SlotName.PromiseIsHandled))
            Reeva.activeAgent.hostHooks.promiseRejectionTracker(realm, promise, "reject")

        return triggerPromiseReactions(reactions, reason)
    }

    @JvmStatic
    @ECMAImpl("26.6.1.8")
    fun triggerPromiseReactions(reactions: List<PromiseReaction>, argument: JSValue): JSValue {
        reactions.forEach { reaction ->
            val job = newPromiseReactionJob(reaction, argument)
            Reeva.activeAgent.hostHooks.enqueuePromiseJob(job.job, job.realm)
        }
        return JSUndefined
    }

    @JvmStatic
    @ECMAImpl("26.6.2.1")
    fun newPromiseReactionJob(reaction: PromiseReaction, argument: JSValue): PromiseReactionJob {
        val handlerRealm = if (reaction.handler != null) reaction.handler.realm else null

        return PromiseReactionJob(handlerRealm) job@{
            val handlerResult: Any = if (reaction.handler == null) {
                if (reaction.type == PromiseReaction.Type.Fulfill) {
                    argument
                } else {
                    ThrowException(argument)
                }
            } else try {
                call(reaction.handler.realm, reaction.handler, JSUndefined, listOf(argument))
            } catch (e: ThrowException) {
                e
            }

            if (reaction.capability == null) {
                ecmaAssert(handlerResult !is ThrowException)
                return@job
            }

            if (handlerResult is ThrowException) {
                val rejectFunc = reaction.capability.reject!!
                call(rejectFunc.realm, rejectFunc, JSUndefined, listOf(handlerResult.value))
            } else {
                val resolveFunc = reaction.capability.resolve!!
                call(resolveFunc.realm, resolveFunc, JSUndefined, listOf(handlerResult as JSValue))
            }
        }
    }

    @JvmStatic
    @ECMAImpl("26.6.2.2")
    fun newPromiseResolveThenableJob(
        realm: Realm,
        promise: JSObject,
        thenable: JSValue,
        then: JSValue
    ): PromiseReactionJob {
        // TODO: then is always an object?
        val thenRealm = if (then is JSObject) then.realm else realm
        return PromiseReactionJob(thenRealm) {
            val (resolveFunction, rejectFunction) = createResolvingFunctions(promise)
            try {
                call(realm, then, thenable, listOf(resolveFunction, rejectFunction))
            } catch (e: ThrowException) {
                call(realm, rejectFunction, JSUndefined, listOf(e.value))
            }
        }
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

        val capability = newPromiseCapability(constructor.realm, constructor)
        call(constructor.realm, capability.resolve!!, JSUndefined, listOf(value))
        return capability.promise
    }

    @JvmStatic
    @ECMAImpl("26.6.5.4.1")
    fun performPromiseThen(
        realm: Realm,
        promise: JSObject,
        onFulfilled: JSValue,
        onRejected: JSValue,
        resultCapability: PromiseCapability?
    ): JSValue {
        val onFulfilledCallback = if (isCallable(onFulfilled)) {
            onFulfilled
        } else null
        val onRejectedCallback = if (isCallable(onRejected)) {
            onRejected
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
                Reeva.activeAgent.hostHooks.enqueuePromiseJob(fulfillJob.job, fulfillJob.realm)
            }
            else -> {
                if (!promise.getSlotAs<Boolean>(SlotName.PromiseIsHandled))
                    Reeva.activeAgent.hostHooks.promiseRejectionTracker(realm, promise, "handle")
                val rejectJob = newPromiseReactionJob(rejectReaction, promise.getSlotAs(SlotName.PromiseResult))
                Reeva.activeAgent.hostHooks.enqueuePromiseJob(rejectJob.job, rejectJob.realm)
            }
        }

        promise.setSlot(SlotName.PromiseIsHandled, true)

        return resultCapability?.promise ?: JSUndefined
    }

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

    data class CodepointRecord(val codepoint: Int, val codeUnitCount: Int, val isUnpairedSurrogate: Boolean)

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
        val convOp: ((Realm, JSValue) -> JSValue)?
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
        val handler: JSFunction?,
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
