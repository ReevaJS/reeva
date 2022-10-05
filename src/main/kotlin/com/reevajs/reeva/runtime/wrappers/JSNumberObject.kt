package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSNumber

open class JSNumberObject protected constructor(realm: Realm, number: JSNumber) : JSObject(realm) {
    val number by slot(Slot.NumberData, number)

    override fun init() {
        setPrototype(realm.numberProto)
        super.init()
    }

    companion object {
        fun create(number: JSNumber, realm: Realm = Agent.activeAgent.getActiveRealm()) = JSNumberObject(realm, number).initialize()
    }
}
