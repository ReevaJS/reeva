package com.reevajs.reeva.core.environment

import com.reevajs.reeva.core.lifecycle.ModuleRecord
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.utils.unreachable

class ModuleEnvRecord(outer: EnvRecord?) : EnvRecord(outer) {
    private val bindings = mutableMapOf<String, Binding>()

    fun setIndirectBinding(localName: String, sourceName: String, sourceModule: ModuleRecord) {
        bindings[localName] = IndirectBinding(sourceName, sourceModule)
    }

    override fun hasBinding(name: String) = name in bindings

    override fun getBinding(name: String): JSValue {
        return when (val binding = bindings[name]!!) {
            is DirectBinding -> binding.value
            is IndirectBinding -> binding.sourceModule.env.getBinding(binding.sourceName)
        }
    }

    override fun setBinding(name: String, value: JSValue) {
        bindings[name] = DirectBinding(value)
    }

    override fun hasBinding(slot: Int) = unreachable()
    override fun getBinding(slot: Int) = unreachable()
    override fun setBinding(slot: Int, value: JSValue) = unreachable()

    sealed class Binding

    class DirectBinding(val value: JSValue) : Binding()

    class IndirectBinding(
        val sourceName: String,
        val sourceModule: ModuleRecord,
    ) : Binding()
}
