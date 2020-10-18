package me.mattco.jsthing.runtime.environment

import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.primitives.JSUndefined

abstract class EnvRecord(var outerEnv: EnvRecord?) {
    abstract fun hasBinding(name: String): Boolean

    abstract fun createMutableBinding(name: String, canBeDeleted: Boolean)

    abstract fun createImmutableBinding(name: String, throwOnRepeatInitialization: Boolean)

    abstract fun initializeBinding(name: String, value: JSValue)

    abstract fun setMutableBinding(name: String, value: JSValue, throwOnFailure: Boolean)

    abstract fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue

    abstract fun deleteBinding(name: String): Boolean

    abstract fun hasThisBinding(): Boolean

    abstract fun hasSuperBinding(): Boolean

    abstract fun withBaseObject(): JSValue

    data class Binding(
        val immutable: Boolean,
        val deletable: Boolean = false,
        var value: JSValue = JSUndefined,
        var initialized: Boolean = false,
        var strict: Boolean = false,
    )
}
