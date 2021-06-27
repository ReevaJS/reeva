package me.mattco.reeva.core.environment

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject

/**
 * The runtime-equivalent of the Parser's Scope objects
 */
abstract class EnvRecord(val realm: Realm, var isStrict: Boolean, val outer: EnvRecord?) {
    abstract fun hasBinding(name: String): Boolean

    abstract fun createMutableBinding(name: String, deletable: Boolean)

    abstract fun createImmutableBinding(name: String, strict: Boolean)

    abstract fun initializeBinding(name: String, value: JSValue)

    abstract fun setMutableBinding(name: String, value: JSValue, strict: Boolean)

    abstract fun getBindingValue(name: String, strict: Boolean): JSValue

    abstract fun deleteBinding(name: String): Boolean

    abstract fun hasThisBinding(): Boolean

    abstract fun hasSuperBinding(): Boolean

    abstract fun withBaseObject(): JSObject?

    sealed class Binding(var value: JSValue)

    class MutableBinding(value: JSValue, val deletable: Boolean) : Binding(value)

    class ImmutableBinding(value: JSValue, val strict: Boolean) : Binding(value)
}
