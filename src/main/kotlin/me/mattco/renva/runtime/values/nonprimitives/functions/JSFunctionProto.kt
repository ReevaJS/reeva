package me.mattco.renva.runtime.values.nonprimitives.functions

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.values.nonprimitives.objects.JSObject

class JSFunctionProto(private val realm: Realm) : JSObject(realm, realm.objectProto) {
    companion object {
        fun create(realm: Realm) = JSFunctionProto(realm).also { it.init() }
    }
}
