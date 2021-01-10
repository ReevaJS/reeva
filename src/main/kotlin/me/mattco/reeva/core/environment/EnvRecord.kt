package me.mattco.reeva.core.environment

import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Ref
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.primitives.JSUndefined

abstract class EnvRecord(@JvmField var outerEnv: EnvRecord?) : Ref {
    abstract val isStrict: Boolean

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

    open class Binding(
        val immutable: Boolean,
        val deletable: Boolean = false,
        var value: JSValue = JSUndefined,
        var initialized: Boolean = false,
        var strict: Boolean = false,
    )
}
