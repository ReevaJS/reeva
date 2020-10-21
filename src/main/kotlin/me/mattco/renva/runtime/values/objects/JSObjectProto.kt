package me.mattco.renva.runtime.values.objects

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.values.objects.JSObject
import me.mattco.renva.runtime.values.primitives.JSNull

class JSObjectProto private constructor(realm: Realm) : JSObject(realm, JSNull) {
    companion object {
        fun create(realm: Realm) = JSObjectProto(realm).also { it.init() }
    }
}