package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSUndefined

class JSUnmappedArgumentsObject private constructor(realm: Realm) : JSObject(realm.objectProto) {
    var parameterMap by lateinitSlot(SlotName.UnmappedParameterMap)

    override fun init(realm: Realm) {
        super.init(realm)
        parameterMap = JSUndefined
    }

    companion object {
        fun create(realm: Realm) = JSUnmappedArgumentsObject(realm).initialize(realm)
    }
}
