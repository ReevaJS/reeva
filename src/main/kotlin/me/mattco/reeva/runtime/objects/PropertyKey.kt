package me.mattco.reeva.runtime.objects

import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.runtime.primitives.JSSymbol
import me.mattco.reeva.utils.expect

/**
 * Represents a key in a JSObject's property map. This class is extremely useless
 * at the moment, but will be used later on when Symbol support is added
 */
data class PropertyKey private constructor(internal val value: Any) {
    val isString: Boolean
        get() = value is String
    val isInt: Boolean
        get() = value is Int
    val isLong: Boolean
        get() = value is Long
    val isDouble: Boolean
        get() = value is Double
    val isSymbol: Boolean
        get() = value is JSSymbol

    val asString: String
        get() = value as String
    val asInt: Int
        get() = value as Int
    val asLong: Long
        get() = value as Long
    val asDouble: Double
        get() = when {
            isInt -> asInt.toDouble()
            isLong -> asLong.toDouble()
            else -> value as Double
        }
    val asSymbol: JSSymbol
        get() = value as JSSymbol

    val asValue: JSValue
        get() = when {
            isString -> JSString(asString)
            isSymbol -> asSymbol
            else -> JSNumber(asDouble)
        }

    constructor(value: String) : this(value as Any)
    constructor(value: JSString) : this(value.string)
    constructor(value: JSSymbol) : this(value as Any)
    constructor(value: JSObject.StringOrSymbol) : this(if (value.isString) value.asString else value.asSymbol)
    constructor(value: Number) : this(value as Any) {
        expect(value is Double || value is Int || value is Long)
    }

    override fun equals(other: Any?): Boolean {
        return other is PropertyKey && value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return when {
            isString -> asString
            isInt -> asInt.toString()
            isLong -> asLong.toString()
            isDouble -> asDouble.toString()
            else -> asSymbol.descriptiveString()
        }
    }

    companion object {
        val INVALID_KEY = PropertyKey(0)

        fun from(value: Any): PropertyKey? {
            return when (value) {
                is String -> PropertyKey(value)
                is JSString -> PropertyKey(value)
                is Double -> PropertyKey(value)
                is Int -> PropertyKey(value)
                is JSSymbol -> PropertyKey(value)
                else -> null
            }
        }
    }
}
