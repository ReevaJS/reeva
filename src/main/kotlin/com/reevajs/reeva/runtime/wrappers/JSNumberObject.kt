package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSNumber

open class JSNumberObject protected constructor(number: JSNumber) : JSObject() {
    val number by slot(SlotName.NumberData, number)

    override fun init(realm: Realm) {
        setPrototype(realm.numberProto)
        super.init(realm)
    }

    companion object {
        fun create(realm: Realm, number: JSNumber) = JSNumberObject(number).initialize(realm)
    }
}
