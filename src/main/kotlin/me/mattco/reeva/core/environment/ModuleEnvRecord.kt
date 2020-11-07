package me.mattco.reeva.core.environment

import me.mattco.reeva.core.modules.ModuleRecord
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.throwReferenceError
import me.mattco.reeva.utils.unreachable

class ModuleEnvRecord(
    outerEnv: EnvRecord?
) : DeclarativeEnvRecord(outerEnv) {
    @ECMAImpl("8.1.1.5.1")
    override fun getBindingValue(name: String, throwOnNotFound: Boolean): JSValue {
        ecmaAssert(throwOnNotFound)
        ecmaAssert(hasBinding(name))
        val binding = bindings[name]!!

        if (binding is IndirectBinding)
            return binding.targetModuleRecord.environment!!.getBindingValue(binding.targetName, throwOnNotFound = true)

        if (!binding.initialized)
            throwReferenceError("TODO: message (ModuleEnvRecord getBindingValue)")

        return binding.value
    }

    @ECMAImpl("8.1.1.5.2")
    override fun deleteBinding(name: String): Boolean {
        // "Module Environment Records are only used within strict code and an early error
        // rule prevents the delete operator, in strict code, from being applied to a
        // Reference Record that would resolve to a module Environment Record binding"
        unreachable()
    }

    override fun hasThisBinding() = true

    fun getThisBinding(): JSValue = JSUndefined

    fun createImportBinding(localName: String, targetEnv: ModuleRecord, targetName: String) {
        ecmaAssert(!hasBinding(localName))
        bindings[localName] = IndirectBinding(targetEnv, targetName)
    }

    class IndirectBinding(
        val targetModuleRecord: ModuleRecord,
        val targetName: String,
        value: JSValue = JSUndefined,
    ) : Binding(immutable = true, deletable = true, value, initialized = true, strict = true)
}
