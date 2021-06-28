package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined

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
