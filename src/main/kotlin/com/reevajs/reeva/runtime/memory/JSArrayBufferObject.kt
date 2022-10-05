package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSUndefined

class JSArrayBufferObject private constructor(realm: Realm) : JSObject(realm, realm.arrayBufferProto) {
    val data by lateinitSlot(Slot.ArrayBufferData)
    val byteLength by lateinitSlot(Slot.ArrayBufferByteLength)
    val detachKey by slot(Slot.ArrayBufferDetachKey, JSUndefined)

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSArrayBufferObject(realm).initialize()
    }
}
