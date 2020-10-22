package me.mattco.reeva.runtime.values.functions

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.objects.JSObject

class JSFunctionProto private constructor(private val realm: Realm) : JSObject(realm, realm.objectProto) {
    companion object {
        // Special object: do not initialize
        fun create(realm: Realm) = JSFunctionProto(realm)
    }
}
