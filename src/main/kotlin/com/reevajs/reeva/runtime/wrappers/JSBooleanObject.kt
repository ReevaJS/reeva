package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSBoolean

open class JSBooleanObject protected constructor(
    realm: Realm,
    value: JSBoolean,
) : JSObject(realm) {
    val value by slot(SlotName.BooleanData, value)

    override fun init() {
        val realm = Agent.activeAgent.getActiveRealm()
        setPrototype(realm.booleanProto)
        super.init()
    }

    companion object {
        fun create(value: JSBoolean, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSBooleanObject(realm, value).initialize()
    }
}
