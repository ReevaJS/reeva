package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject

class JSUnmappedArgumentsObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    companion object {
        fun create(realm: Realm) = JSUnmappedArgumentsObject(realm).also { it.init() }
    }
}
