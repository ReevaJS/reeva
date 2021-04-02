package me.mattco.reeva.runtime.objects

import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.runtime.primitives.JSSymbol
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable

/**
 * Represents a key in a JSObject's property map. This class is extremely useless
 * at the moment, but will be used later on when Symbol support is added
 */
data class PropertyKey private constructor(internal val value: Any) {
    val isString: Boolean
        get() = value is String
    val isSymbol: Boolean
        get() = value is JSSymbol
    val isInt: Boolean
        get() = value is Int
    val isLong: Boolean
        get() = value is Long

    val asString: String
        get() = value as String
    val asSymbol: JSSymbol
        get() = value as JSSymbol
    val asInt: Int
        get() = value as Int
    val asLong: Long
        get() = value as Long

    val asValue: JSValue
        get() = when {
            isString -> JSString(asString)
            isSymbol -> asSymbol
            isInt -> JSNumber(asInt)
            isLong -> JSNumber(asLong)
            else -> unreachable()
        }

    override fun equals(other: Any?): Boolean {
        return other is PropertyKey && value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    fun toStringOrSymbol(): JSObject.StringOrSymbol {
        expect(isString || isSymbol)
        return if (isString) {
            JSObject.StringOrSymbol(asString)
        } else JSObject.StringOrSymbol(asSymbol)
    }

    override fun toString(): String {
        return when {
            isString -> asString
            isInt -> asInt.toString()
            isLong -> asLong.toString()
            else -> asSymbol.descriptiveString()
        }
    }

    companion object {
        fun from(value: Any): PropertyKey {
            if (value is String || value is JSString) {
                val string = (value as? JSString)?.string ?: value as String
                val long = string.toLongOrNull()
                if (long != null) {
                    if (long in 0..Int.MAX_VALUE)
                        return PropertyKey(long.toInt())
                    return PropertyKey(long)
                }
                return PropertyKey(string)
            }

            return when (value) {
                is Int -> if (value < 0) {
                    PropertyKey(value.toString())
                } else PropertyKey(value)
                is Long -> when {
                    value < 0L -> PropertyKey(value.toString())
                    value <= Int.MAX_VALUE -> PropertyKey(value.toInt())
                    else -> PropertyKey(value)
                }
                is Double -> PropertyKey(value.toString())
                is JSSymbol -> PropertyKey(value)
                is JSObject.StringOrSymbol -> if (value.isString) {
                    from(value.asString)
                } else from(value.asSymbol)
                else -> unreachable()
            }
        }
    }
}
