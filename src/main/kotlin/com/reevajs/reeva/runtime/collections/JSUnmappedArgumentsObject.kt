package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSUndefined

class JSUnmappedArgumentsObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    var parameterMap by lateinitSlot(Slot.UnmappedParameterMap)

    override fun init() {
        super.init()
        parameterMap = JSUndefined
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSUnmappedArgumentsObject(realm).initialize()
    }
}
