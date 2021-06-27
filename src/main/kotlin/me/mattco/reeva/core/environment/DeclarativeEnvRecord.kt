package me.mattco.reeva.core.environment

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.ecmaAssert

open class DeclarativeEnvRecord(realm: Realm, isStrict: Boolean, outer: EnvRecord?) : EnvRecord(realm, isStrict, outer) {
    private val bindings = mutableMapOf<String, Binding>()

    override fun hasBinding(name: String) = name in bindings

    override fun createMutableBinding(name: String, deletable: Boolean) {
        ecmaAssert(!hasBinding(name))
        bindings[name] = MutableBinding(JSEmpty, deletable)
    }

    override fun createImmutableBinding(name: String, strict: Boolean) {
        ecmaAssert(!hasBinding(name))
        bindings[name] = ImmutableBinding(JSEmpty, strict)
    }

    override fun initializeBinding(name: String, value: JSValue) {
        val binding = bindings[name]
        ecmaAssert(binding != null)
        binding.value = value
    }

    override fun setMutableBinding(name: String, value: JSValue, strict: Boolean) {
        val binding = bindings[name]

        if (binding == null) {
            if (strict)
                Errors.TODO("DeclarativeEnvRecord::setMutableBinding 1").throwReferenceError(realm)

            createMutableBinding(name, deletable = true)
            initializeBinding(name, value)
            return
        }

        val isStrict = if (binding is ImmutableBinding && binding.strict) true else strict

        when {
            binding.value == JSEmpty -> Errors.TODO("DeclarativeEnvRecord::setMutableBinding 2").throwReferenceError(realm)
            binding is MutableBinding -> binding.value = value
            isStrict -> Errors.TODO("DeclarativeEnvRecord::setMutableBinding 3").throwReferenceError(realm)
        }
    }

    override fun getBindingValue(name: String, strict: Boolean): JSValue {
        ecmaAssert(hasBinding(name))
        val binding = bindings[name]!!
        if (binding.value == JSEmpty)
            Errors.TODO("DeclarativeEnvRecord::getBindingValue").throwReferenceError(realm)
        return binding.value
    }

    override fun deleteBinding(name: String): Boolean {
        ecmaAssert(hasBinding(name))
        val binding = bindings[name]!!
        return if (binding is MutableBinding && binding.deletable) {
            bindings.remove(name)
            true
        } else false
    }

    override fun hasThisBinding() = false

    override fun hasSuperBinding() = false

    override fun withBaseObject(): JSObject? = null
}
