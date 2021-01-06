package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSBigInt

class JSBigIntObject private constructor(realm: Realm, bigint: JSBigInt) : JSObject(realm, realm.bigIntProto) {
    val bigint by slot(SlotName.BigIntData, bigint)

    companion object {
        fun create(realm: Realm, value: JSBigInt) = JSBigIntObject(realm, value).initialize()
    }
}
