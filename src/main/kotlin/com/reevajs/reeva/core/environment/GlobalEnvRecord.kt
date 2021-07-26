package com.reevajs.reeva.core.environment

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.unreachable

class GlobalEnvRecord(thisValue: JSObject) : EnvRecord(null) {
    private val withRecord = WithEnvRecord(null, thisValue)

    override fun hasBinding(name: String) = withRecord.hasBinding(name)
    override fun getBinding(name: String) = withRecord.getBinding(name)
    override fun setBinding(name: String, value: JSValue) = withRecord.setBinding(name, value)

    override fun hasBinding(slot: Int) = unreachable()
    override fun getBinding(slot: Int) = unreachable()
    override fun setBinding(slot: Int, value: JSValue) = unreachable()
}
