package me.mattco.jsthing.runtime.values

import me.mattco.jsthing.runtime.annotations.ECMAImpl
import me.mattco.jsthing.runtime.values.primitives.JSNull
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject
import me.mattco.jsthing.runtime.values.primitives.*
import me.mattco.jsthing.utils.ecmaAssert
import me.mattco.jsthing.utils.expect
import me.mattco.jsthing.utils.unreachable

abstract class JSValue {
    val type by lazy {
        when (this) {
            is JSEmpty -> Type.Empty
            is JSUndefined -> Type.Undefined
            is JSNull -> Type.Null
            is JSTrue, is JSFalse -> Type.Boolean
            is JSString -> Type.String
            is JSNumber -> Type.Number
            is JSObject -> Type.Object
            else -> throw IllegalStateException("Unknown object type")
        }
    }

    val asBoolean: Boolean
        get() {
            expect(type == Type.Boolean)
            return (this as JSBoolean).value
        }

    val asString: String
        get() {
            expect(type == Type.String)
            return (this as JSString).value
        }

    val asDouble: Double
        get() {
            expect(type == Type.Number)
            return (this as JSNumber).value
        }

    val asInt: Int
        get() {
            expect(type == Type.Number)
            return (this as JSNumber).value.toInt()
        }

    val isEmpty by lazy { type == Type.Empty }
    val isUndefined by lazy { type == Type.Undefined }
    val isNull by lazy { type == Type.Null }
    val isBoolean by lazy { type == Type.Boolean }
    val isNumber by lazy { type == Type.Number }
    val isString by lazy { type == Type.String }
    val isObject by lazy { type == Type.Object }

    val isNullish by lazy { this == JSNull || this == JSUndefined }
    val isNaN by lazy { isNumber && asDouble.isNaN() }
    val isInfinite by lazy { isNumber && asDouble.isInfinite() }
    val isPositiveInfinity by lazy { isNumber && asDouble == Double.POSITIVE_INFINITY }
    val isNegativeInfinity by lazy { isNumber && asDouble == Double.NEGATIVE_INFINITY }
    val isPositiveZero by lazy { isNumber && 1.0 / asDouble == Double.POSITIVE_INFINITY }
    val isNegativeZero by lazy { isNumber && 1.0 / asDouble == Double.NEGATIVE_INFINITY }

    @ECMAImpl("SameValue", "7.2.10")
    fun sameValue(other: JSValue): Boolean {
        if (type != other.type)
            return false
        // TODO: Number vs BigInt
        return sameValueNonNumeric(other)
    }

    @ECMAImpl("SameValueZero", "7.2.11")
    fun sameValueZero(other: JSValue): Boolean {
        if (type != other.type)
            return false
        // TODO: Number vs BigInt
        return sameValueNonNumeric(other)
    }

    @ECMAImpl("SameValueNonNumeric", "7.2.12")
    fun sameValueNonNumeric(other: JSValue): Boolean {
        ecmaAssert(type != Type.Number /* && type != Type.BigInt */)
        ecmaAssert(type == other.type)
        expect(type != Type.Empty)

        return when (type) {
            Type.Undefined -> true
            Type.Null -> true
            Type.Boolean -> asBoolean == other.asBoolean
            Type.String -> asString == other.asString
            Type.Object -> this === other
            else -> unreachable()
        }
    }

    @ECMAImpl("ToBoolean", "7.1.2")
    fun toBoolean() = when (type) {
        Type.Empty -> unreachable()
        Type.Undefined, Type.Null -> false
        Type.Boolean -> asBoolean
        Type.String -> asString.isNotEmpty()
        Type.Number -> !(isPositiveZero || isNegativeZero || isNaN)
        Type.Object -> true
    }

    @ECMAImpl("ToNumber", "7.1.14")
    fun toNumber() = when (type) {
        Type.Empty -> unreachable()
        Type.Undefined -> Double.NaN
        Type.Null -> 0.0
        Type.Boolean -> if (asBoolean) 1.0 else 0.0
        Type.String -> TODO()
        Type.Number -> asDouble
        Type.Object -> TODO()
    }

    @ECMAImpl("ToObject", "7.1.18")
    fun toObject(): JSObject {
        TODO()
    }

    enum class Type {
        Empty,
        Undefined,
        Null,
        Boolean,
        String,
        Number,
        Object,
    }
}
