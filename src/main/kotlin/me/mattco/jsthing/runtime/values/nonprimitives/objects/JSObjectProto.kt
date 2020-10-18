package me.mattco.jsthing.runtime.values.nonprimitives.objects

import me.mattco.jsthing.runtime.Realm
import me.mattco.jsthing.runtime.values.primitives.JSNull

class JSObjectProto private constructor(private val realm: Realm) : JSObject(realm, JSNull) {
    companion object {
        fun create(realm: Realm) = JSObjectProto(realm).also { it.init() }
    }
}
