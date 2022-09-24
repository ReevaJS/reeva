package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSBigInt

class JSBigIntObject private constructor(realm: Realm, bigint: JSBigInt) : JSObject(realm.bigIntProto) {
    val bigint by slot(SlotName.BigIntData, bigint)

    companion object {
        fun create(realm: Realm, value: JSBigInt) =
            JSBigIntObject(realm, value).initialize(realm)
    }
}
