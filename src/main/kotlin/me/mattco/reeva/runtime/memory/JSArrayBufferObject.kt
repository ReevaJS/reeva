package me.mattco.reeva.runtime.memory

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined

class JSArrayBufferObject private constructor(realm: Realm) : JSObject(realm, realm.arrayBufferProto) {
    val data by lateinitSlot<DataBlock>(SlotName.ArrayBufferData)
    val byteLength by lateinitSlot<Int>(SlotName.ArrayBufferByteLength)
    val detachKey by slot<JSValue>(SlotName.ArrayBufferDetachKey, JSUndefined)

    companion object {
        fun create(realm: Realm) = JSArrayBufferObject(realm).initialize()
    }
}
