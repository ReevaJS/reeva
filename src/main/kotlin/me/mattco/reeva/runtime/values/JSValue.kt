package me.mattco.reeva.runtime.values

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.values.arrays.JSArrayObject
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.primitives.JSNativeProperty
import me.mattco.reeva.runtime.values.primitives.JSNull
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.*
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable
import kotlin.math.floor

abstract class JSValue : Ref {
    val type by lazy {
        when (this) {
            is JSEmpty -> Type.Empty
            is JSUndefined -> Type.Undefined
            is JSNull -> Type.Null
            is JSBoolean -> Type.Boolean
            is JSString -> Type.String
            is JSSymbol -> Type.Symbol
            is JSNumber -> Type.Number
            is JSNativeProperty -> Type.NativeProperty
            is JSAccessor -> Type.Accessor
            is JSObject -> Type.Object
            else -> throw IllegalStateException("Unknown object type")
        }
    }

    val isEmpty by lazy { type == Type.Empty }
    val isUndefined by lazy { type == Type.Undefined }
    val isNull by lazy { type == Type.Null }
    val isBoolean by lazy { type == Type.Boolean }
    val isNumber by lazy { type == Type.Number }
    val isBigInt by lazy { type == Type.BigInt }
    val isString by lazy { type == Type.String }
    val isSymbol by lazy { type == Type.Symbol }
    val isObject by lazy { type == Type.Object }
    val isAccessor by lazy { type == Type.Accessor }
    val isFunction by lazy { this is JSFunction }
    val isArray by lazy { this is JSArrayObject }

    val isNullish by lazy { this == JSNull || this == JSUndefined }
    val isInt by lazy { isNumber && !isInfinite && floor(asDouble) == asDouble }
    val isNaN by lazy { isNumber && asDouble.isNaN() }
    val isFinite by lazy { isNumber && asDouble.isFinite()}
    val isInfinite by lazy { isNumber && asDouble.isInfinite() }
    val isPositiveInfinity by lazy { isNumber && asDouble == Double.POSITIVE_INFINITY }
    val isNegativeInfinity by lazy { isNumber && asDouble == Double.NEGATIVE_INFINITY }
    val isZero by lazy { isNumber && asDouble == 0.0 }
    val isPositiveZero by lazy { isNumber && 1.0 / asDouble == Double.POSITIVE_INFINITY }
    val isNegativeZero by lazy { isNumber && 1.0 / asDouble == Double.NEGATIVE_INFINITY }

    val asBoolean: Boolean
        get() {
            expect(isBoolean)
            return (this as JSBoolean).value
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

    val asDouble: Double
        get() {
            expect(isNumber)
            return (this as JSNumber).number
        }

    val asInt: Int
        get() {
            expect(isNumber)
            return (this as JSNumber).number.toInt()
        }

    val asAccessor: JSNativeProperty
        get() {
            expect(isAccessor)
            return this as JSNativeProperty
        }

    val asFunction: JSFunction
        get() {
            expect(isFunction)
            return this as JSFunction
        }

    val asArray: JSArrayObject
        get() {
            expect(isArray)
            return this as JSArrayObject
        }

    val toString: String
        get() = Operations.toString(this).string

    val toInt32: Int
        get() = Operations.toInt32(this).asInt

    val toBoolean: Boolean
        get() = Operations.toBoolean(this).asBoolean

    @ECMAImpl("7.2.10")
    fun sameValue(other: JSValue): Boolean {
        if (type != other.type)
            return false
        if (type == Type.Number)
            return Operations.numericSameValue(this, other).value
        if (type == Type.BigInt)
            TODO()
        return sameValueNonNumeric(other)
    }

    @ECMAImpl("7.2.11")
    fun sameValueZero(other: JSValue): Boolean {
        if (type != other.type)
            return false
        // TODO: Number vs BigInt
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
            Type.Object -> this === other
            else -> TODO()
        }
    }

    @ECMAImpl("7.1.2")
    fun toBoolean() = when (type) {
        Type.Empty -> unreachable()
        Type.Undefined, Type.Null -> false
        Type.Boolean -> asBoolean
        Type.String -> asString.isNotEmpty()
        Type.Number -> !(isPositiveZero || isNegativeZero || isNaN)
        Type.Object -> true
        else -> TODO()
    }

    @ECMAImpl("7.1.14")
    fun toNumber() = when (type) {
        Type.Empty -> unreachable()
        Type.Undefined -> Double.NaN
        Type.Null -> 0.0
        Type.Boolean -> if (asBoolean) 1.0 else 0.0
        Type.String -> TODO()
        Type.Number -> asDouble
        Type.Object -> TODO()
        else -> TODO()
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
        NativeProperty("NativeProperty (you shouldn't see this)"),
        Accessor("Accessor (you shouldn't see this)");

        override fun toString() = typeName
    }

    companion object {
        val INVALID_VALUE = object : JSValue() {}
    }
}
