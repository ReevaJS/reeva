package me.mattco.reeva.runtime.memory

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject

class JSTypedArrayProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    companion object {
        fun create(realm: Realm) = JSTypedArrayProto(realm).initialize()
    }
}
