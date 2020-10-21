package me.mattco.reeva.runtime.values.objects

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.primitives.JSNull

class JSObjectProto private constructor(realm: Realm) : JSObject(realm, JSNull) {
    companion object {
        fun create(realm: Realm) = JSObjectProto(realm).also { it.init() }
    }
}
