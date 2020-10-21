package me.mattco.renva.runtime.values.functions

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.values.objects.JSObject

class JSFunctionProto private constructor(private val realm: Realm) : JSObject(realm, realm.objectProto) {
    companion object {
        fun create(realm: Realm) = JSFunctionProto(realm).also { it.init() }
    }
}
