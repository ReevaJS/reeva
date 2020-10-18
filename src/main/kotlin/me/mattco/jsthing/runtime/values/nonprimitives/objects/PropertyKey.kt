package me.mattco.jsthing.runtime.values.nonprimitives.objects

import me.mattco.jsthing.runtime.values.primitives.JSString
import me.mattco.jsthing.utils.expect

/**
 * Represents a key in a JSObject's property map. This class is extremely useless
 * at the moment, but will be used later on when Symbol support is added
 */
class PropertyKey(value: Any) {
    private val value = value.toString()

    val isString = value is String

    val asString by lazy {
        expect(isString)
        value as String
    }

    val asValue by lazy {
        if (isString) {
            JSString(value as String)
        } else TODO()
    }

    override fun equals(other: Any?): Boolean {
        return other is PropertyKey && value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        if (isString)
            return asString
        TODO()
    }
}
