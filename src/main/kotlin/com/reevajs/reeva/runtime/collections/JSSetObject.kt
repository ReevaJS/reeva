package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSEmpty

class JSSetObject private constructor(realm: Realm) : JSObject(realm, realm.setProto) {
    val setData by slot(Slot.SetData, SetData())

    data class SetData(
        val set: MutableSet<JSValue> = mutableSetOf(),
        val insertionOrder: MutableList<JSValue> = mutableListOf()
    ) {
        var iterationCount = 0
            set(value) {
                if (value == 0)
                    insertionOrder.removeIf { it == JSEmpty }
                field = value
            }
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSSetObject(realm).initialize()
    }
}
