package me.mattco.reeva.runtime.jvmcompat

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject

class JSClassProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    companion object {
        fun create(realm: Realm) = JSClassProto(realm).also { it.init() }
    }
}
