package me.mattco.reeva.core.environment

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue

/**
 * The runtime-equivalent of the Parser's Scope objects
 */
abstract class EnvRecord(val realm: Realm, val outer: EnvRecord?) : JSValue() {
    abstract fun hasBinding(slot: Int): Boolean

    abstract fun hasBinding(name: String): Boolean

    abstract fun getBinding(slot: Int): JSValue

    abstract fun getBinding(name: String): JSValue

    abstract fun setBinding(slot: Int, value: JSValue)

    abstract fun setBinding(name: String, value: JSValue)
}
