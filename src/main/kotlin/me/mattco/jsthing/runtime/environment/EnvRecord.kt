package me.mattco.jsthing.runtime.environment

import me.mattco.jsthing.runtime.annotations.ECMAImpl
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.primitives.JSUndefined

abstract class EnvRecord(var outerEnv: EnvRecord?) {
    @ECMAImpl("HasBinding")
    abstract fun hasBinding(name: String): Boolean

    @ECMAImpl("CreateMutableBinding")
    abstract fun createMutableBinding(name: String, canBeDeleted: Boolean)

    @ECMAImpl("CreateImmutableBinding")
    abstract fun createImmutableBinding(name: String, throwOnRepeatInitialization: Boolean)

    @ECMAImpl("InitializeBinding")
    abstract fun initializeBinding(name: String, value: JSValue)

    @ECMAImpl("SetMutableBinding")
    abstract fun setMutableBinding(name: String, value: JSValue, throwOnFailure: Boolean)

    @ECMAImpl("GetBindingValue")
    abstract fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue

    @ECMAImpl("DeleteBinding")
    abstract fun deleteBinding(name: String): Boolean

    @ECMAImpl("HasThisBinding")
    abstract fun hasThisBinding(): Boolean

    @ECMAImpl("HasSuperBinding")
    abstract fun hasSuperBinding(): Boolean

    @ECMAImpl("WithBaseObject")
    abstract fun withBaseObject(): JSValue

    data class Binding(
        val immutable: Boolean,
        val deletable: Boolean = false,
        var value: JSValue = JSUndefined,
        var initialized: Boolean = false,
        var strict: Boolean = false,
    )
}
