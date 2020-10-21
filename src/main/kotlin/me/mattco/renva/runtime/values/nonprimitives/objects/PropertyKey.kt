package me.mattco.renva.runtime.values.nonprimitives.objects

import me.mattco.renva.runtime.values.primitives.JSString
import me.mattco.renva.runtime.values.primitives.JSSymbol
import me.mattco.renva.utils.expect

/**
 * Represents a key in a JSObject's property map. This class is extremely useless
 * at the moment, but will be used later on when Symbol support is added
 */
class PropertyKey private constructor(private val value: Any) {
    val isString = value is String
    val isSymbol = value is JSSymbol

    val asString by lazy {
        expect(isString)
        value as String
    }

    val asSymbol by lazy {
        expect(isSymbol)
        value as JSSymbol
    }

    val asValue by lazy {
        if (isString) JSString(asString) else asSymbol
    }

    constructor(value: String) : this(value as Any)
    constructor(value: JSString) : this(value.string)
    constructor(value: JSSymbol) : this(value as Any)

    override fun equals(other: Any?): Boolean {
        return other is PropertyKey && value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        if (isString)
            return asString
        return asSymbol.toString()
    }
}
