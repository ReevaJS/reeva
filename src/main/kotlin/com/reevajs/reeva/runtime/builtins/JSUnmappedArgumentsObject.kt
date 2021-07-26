package com.reevajs.reeva.runtime.builtins

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.SlotName
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined

class JSUnmappedArgumentsObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    var parameterMap by lateinitSlot<JSValue>(SlotName.ParameterMap)

    override fun init() {
        super.init()

        parameterMap = JSUndefined
    }

    companion object {
        fun create(realm: Realm) = JSUnmappedArgumentsObject(realm).initialize()
    }
}
