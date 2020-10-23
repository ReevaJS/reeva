package me.mattco.reeva.runtime.environment

import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.Ref
import me.mattco.reeva.runtime.values.primitives.JSUndefined

abstract class EnvRecord(@JvmField var outerEnv: EnvRecord?) : Ref {
    @ECMAImpl("HasBinding")
    abstract fun hasBinding(name: String): Boolean

    @JSThrows
    @ECMAImpl("CreateMutableBinding")
    abstract fun createMutableBinding(name: String, canBeDeleted: Boolean)

    @ECMAImpl("CreateImmutableBinding")
    abstract fun createImmutableBinding(name: String, throwOnRepeatInitialization: Boolean)

    @JSThrows
    @ECMAImpl("InitializeBinding")
    abstract fun initializeBinding(name: String, value: JSValue)

    @JSThrows
    @ECMAImpl("SetMutableBinding")
    abstract fun setMutableBinding(name: String, value: JSValue, throwOnFailure: Boolean)

    @JSThrows
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
