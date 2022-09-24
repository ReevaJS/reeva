package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSBoolean

open class JSBooleanObject protected constructor(value: JSBoolean) : JSObject() {
    val value by slot(SlotName.BooleanData, value)

    override fun init(realm: Realm) {
        setPrototype(realm.booleanProto)
        super.init(realm)
    }

    companion object {
        fun create(realm: Realm, value: JSBoolean) = JSBooleanObject(value).initialize(realm)
    }
}
