package me.mattco.jsthing.runtime.values.nonprimitives.functions

import me.mattco.jsthing.runtime.Realm
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject

class JSFunctionProto(private val realm: Realm) : JSObject(realm, realm.functionProto) {
    companion object {
        fun create(realm: Realm) = JSFunctionProto(realm).also { it.init() }
    }
}
