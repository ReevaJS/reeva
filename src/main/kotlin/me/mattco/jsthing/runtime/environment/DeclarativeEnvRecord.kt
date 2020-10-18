package me.mattco.jsthing.runtime.environment

import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.primitives.JSUndefined

class DeclarativeEnvRecord(outerEnv: EnvRecord?) : EnvRecord(outerEnv) {
    protected val bindings = mutableMapOf<String, Binding>()

    override fun hasBinding(name: String) = name in bindings

    override fun createMutableBinding(name: String, canBeDeleted: Boolean) {
        if (hasBinding(name))
            throw IllegalStateException("Binding already exists for name $name")

        bindings[name] = Binding(
            immutable = false,
            deletable = canBeDeleted,
            initialized = false,
        )
    }

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

    override fun initializeBinding(name: String, value: JSValue) {
        if (!hasBinding(name))
            throw IllegalStateException("Binding does not exist for name $name")

        val binding = bindings[name]!!
        if (binding.initialized)
            throw IllegalStateException("Attempt to initialize already-initialized binding $name")

        binding.value = value
        binding.initialized = true
    }

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

    override fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue {
        if (!hasBinding(name))
            throw IllegalStateException("No binding for $name found")

        val binding = bindings[name]!!
        if (!binding.initialized)
            TODO("Throw ReferenceError")
        return binding.value
    }

    override fun deleteBinding(name: String): Boolean {
        if (!hasBinding(name))
            throw IllegalStateException("No binding for $name found")

        val binding = bindings[name]!!
        if (!binding.deletable)
            return false
        bindings.remove(name)
        return true
    }

    override fun hasThisBinding() = false

    override fun hasSuperBinding() = false

    override fun withBaseObject() = JSUndefined
}
