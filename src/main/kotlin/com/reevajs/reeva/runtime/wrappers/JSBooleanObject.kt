package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSBoolean

open class JSBooleanObject protected constructor(realm: Realm, value: JSBoolean) : JSObject(realm) {
    val value by slot(SlotName.BooleanData, value)

    override fun init() {
        setPrototype(realm.booleanProto)
        super.init()
    }

    companion object {
        fun create(realm: Realm, value: JSBoolean) = JSBooleanObject(realm, value).initialize()
    }
}
