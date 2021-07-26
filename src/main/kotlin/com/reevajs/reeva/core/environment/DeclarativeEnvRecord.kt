package com.reevajs.reeva.core.environment

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.utils.unreachable

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
