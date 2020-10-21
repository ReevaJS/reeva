package me.mattco.renva.runtime.environment

import me.mattco.renva.runtime.annotations.ECMAImpl
import me.mattco.renva.runtime.values.JSValue
import me.mattco.renva.runtime.values.primitives.JSUndefined

open class DeclarativeEnvRecord(outerEnv: EnvRecord?) : EnvRecord(outerEnv) {
    protected val bindings = mutableMapOf<String, Binding>()

    @ECMAImpl("HasBinding", "8.1.1.1.1")
    override fun hasBinding(name: String) = name in bindings

    @ECMAImpl("CreateMutableBinding", "8.1.1.1.2")
    override fun createMutableBinding(name: String, canBeDeleted: Boolean) {
        if (hasBinding(name))
            throw IllegalStateException("Binding already exists for name $name")

        bindings[name] = Binding(
            immutable = false,
            deletable = canBeDeleted,
            initialized = false,
        )
    }

    @ECMAImpl("CreateImmutableBinding", "8.1.1.1.3")
    override fun createImmutableBinding(name: String, strict: Boolean) {
        if (hasBinding(name))
            throw IllegalStateException("Binding already exists for name $name")

        bindings[name] = Binding(
            immutable = true,
            deletable = false,
            initialized = false,
            strict = strict,
        )
    }

    @ECMAImpl("InitializeBinding", "8.1.1.1.4")
    override fun initializeBinding(name: String, value: JSValue) {
        if (!hasBinding(name))
            throw IllegalStateException("Binding does not exist for name $name")

        val binding = bindings[name]!!
        if (binding.initialized)
            throw IllegalStateException("Attempt to initialize already-initialized binding $name")

        binding.value = value
        binding.initialized = true
    }

    @ECMAImpl("SetMutableBinding", "8.1.1.1.5")
    override fun setMutableBinding(name: String, value: JSValue, throwOnFailure: Boolean) {
        if (!hasBinding(name)) {
            if (throwOnFailure)
                TODO("Throw ReferenceError")
            createMutableBinding(name, canBeDeleted = true)
            initializeBinding(name, value)
            return
        }

        var shouldThrow = throwOnFailure

        val binding = bindings[name]!!
        if (binding.strict)
            shouldThrow = true

        if (!binding.initialized)
            TODO("Throw ReferenceError")

        if (!binding.immutable) {
            binding.value = value
        } else if (shouldThrow) {
            TODO("Throw TypeError")
        }
    }

    @ECMAImpl("GetBindingValue", "8.1.1.1.6")
    override fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue {
        if (!hasBinding(name))
            throw IllegalStateException("No binding for $name found")

        val binding = bindings[name]!!
        if (!binding.initialized)
            TODO("Throw ReferenceError")
        return binding.value
    }

    @ECMAImpl("DeleteBinding", "8.1.1.1.7")
    override fun deleteBinding(name: String): Boolean {
        if (!hasBinding(name))
            throw IllegalStateException("No binding for $name found")

        val binding = bindings[name]!!
        if (!binding.deletable)
            return false
        bindings.remove(name)
        return true
    }

    @ECMAImpl("HasThisBinding", "8.1.1.1.8")
    override fun hasThisBinding() = false

    @ECMAImpl("HasSuperBinding", "8.1.1.1.9")
    override fun hasSuperBinding() = false

    @ECMAImpl("WithBaseObject", "8.1.1.1.10")
    override fun withBaseObject() = JSUndefined

    companion object {
        @JvmStatic @ECMAImpl("NewDeclarativeEnvironment", "8.1.2.2")
        fun create(old: EnvRecord?): DeclarativeEnvRecord {
            return DeclarativeEnvRecord(old)
        }
    }
}
