package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSBoolean

open class JSBooleanObject protected constructor(
    realm: Realm,
    value: JSBoolean,
) : JSObject(realm) {
    val value by slot(Slot.BooleanData, value)

    override fun init() {
        setPrototype(realm.booleanProto)
        super.init()
    }

    companion object {
        fun create(value: JSBoolean, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSBooleanObject(realm, value).initialize()
    }
}
