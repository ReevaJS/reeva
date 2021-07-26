package com.reevajs.reeva.core.environment

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.unreachable

class WithEnvRecord(outer: EnvRecord?, private val bindingObject: JSObject) : EnvRecord(outer) {
    override fun hasBinding(name: String): Boolean {
        return bindingObject.hasProperty(name)
    }

    override fun getBinding(name: String): JSValue {
        return bindingObject.get(name)
    }

    override fun setBinding(name: String, value: JSValue) {
        bindingObject.set(name, value)
    }

    override fun hasBinding(slot: Int) = unreachable()
    override fun getBinding(slot: Int) = unreachable()
    override fun setBinding(slot: Int, value: JSValue) = unreachable()
}
