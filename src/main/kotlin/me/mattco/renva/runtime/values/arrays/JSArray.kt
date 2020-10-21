package me.mattco.renva.runtime.values.arrays

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.values.objects.JSObject

class JSArray private constructor(realm: Realm) : JSObject(realm, realm.arrayProto) {
    companion object {
        fun create(realm: Realm) = JSArray(realm).also { it.init() }
    }
}
