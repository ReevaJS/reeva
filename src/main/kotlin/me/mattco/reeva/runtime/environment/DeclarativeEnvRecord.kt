package me.mattco.reeva.runtime.environment

import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSReferenceErrorObject
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.throwError

open class DeclarativeEnvRecord(outerEnv: EnvRecord?) : EnvRecord(outerEnv) {
    protected val bindings = mutableMapOf<String, Binding>()

    @ECMAImpl("HasBinding", "8.1.1.1.1")
    override fun hasBinding(name: String) = name in bindings

    @ECMAImpl("CreateMutableBinding", "8.1.1.1.2")
    override fun createMutableBinding(name: String, canBeDeleted: Boolean) {
        ecmaAssert(!hasBinding(name))

        bindings[name] = Binding(
            immutable = false,
            deletable = canBeDeleted,
            initialized = false,
        )
    }

    @ECMAImpl("CreateImmutableBinding", "8.1.1.1.3")
    override fun createImmutableBinding(name: String, strict: Boolean) {
        ecmaAssert(!hasBinding(name))

        bindings[name] = Binding(
            immutable = true,
            deletable = false,
            initialized = false,
            strict = strict,
        )
    }

    @ECMAImpl("InitializeBinding", "8.1.1.1.4")
    override fun initializeBinding(name: String, value: JSValue) {
        ecmaAssert(hasBinding(name))

        val binding = bindings[name]!!
        ecmaAssert(!binding.initialized)

        binding.value = value
        binding.initialized = true
    }

    @JSThrows
    @ECMAImpl("SetMutableBinding", "8.1.1.1.5")
    override fun setMutableBinding(name: String, value: JSValue, throwOnFailure: Boolean) {
        if (!hasBinding(name)) {
            if (throwOnFailure) {
                throwError<JSReferenceErrorObject>("variable $name not found")
                return
            }
            createMutableBinding(name, canBeDeleted = true)
            initializeBinding(name, value)
            return
        }

        var shouldThrow = throwOnFailure

        val binding = bindings[name]!!
        if (binding.strict)
            shouldThrow = true

        if (!binding.initialized) {
            throwError<JSReferenceErrorObject>("variable $name has not been initialized")
            return
        }

        if (!binding.immutable) {
            binding.value = value
        } else if (shouldThrow) {
            throwError<JSTypeErrorObject>("cannot set value of $name; it is constant")
        }
    }

    @JSThrows
    @ECMAImpl("GetBindingValue", "8.1.1.1.6")
    override fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue {
        ecmaAssert(hasBinding(name))

        val binding = bindings[name]!!
        if (!binding.initialized) {
            throwError<JSReferenceErrorObject>("variable $name has not been initialized")
            return JSValue.INVALID_VALUE
        }
        return binding.value
    }

    @ECMAImpl("DeleteBinding", "8.1.1.1.7")
    override fun deleteBinding(name: String): Boolean {
        ecmaAssert(hasBinding(name))

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
