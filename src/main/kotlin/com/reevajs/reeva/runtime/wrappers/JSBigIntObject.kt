package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSBigInt

class JSBigIntObject private constructor(realm: Realm, bigint: JSBigInt) : JSObject(realm, realm.bigIntProto) {
    val bigint by slot(Slot.BigIntData, bigint)

    companion object {
        fun create(value: JSBigInt, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSBigIntObject(realm, value).initialize()
    }
}
