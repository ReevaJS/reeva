package me.mattco.reeva.core.environment

import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.utils.unreachable

open class DeclarativeEnvRecord(outer: EnvRecord?, numSlots: Int) : EnvRecord(outer) {
    private val bindings = Array<JSValue>(numSlots) { JSEmpty }

    override fun hasBinding(slot: Int) = bindings[slot] != JSEmpty

    override fun getBinding(slot: Int) = bindings[slot]

    override fun setBinding(slot: Int, value: JSValue) {
        bindings[slot] = value
    }

    override fun hasBinding(name: String) = unreachable()
    override fun getBinding(name: String) = unreachable()
    override fun setBinding(name: String, value: JSValue) = unreachable()
}
