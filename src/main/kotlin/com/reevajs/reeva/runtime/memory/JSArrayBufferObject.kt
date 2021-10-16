package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSUndefined

class JSArrayBufferObject private constructor(realm: Realm) : JSObject(realm, realm.arrayBufferProto) {
    val data by lateinitSlot<DataBlock>(SlotName.ArrayBufferData)
    val byteLength by lateinitSlot<Int>(SlotName.ArrayBufferByteLength)
    val detachKey by slot<JSValue>(SlotName.ArrayBufferDetachKey, JSUndefined)

    companion object {
        fun create(realm: Realm) = JSArrayBufferObject(realm).initialize()
    }
}
