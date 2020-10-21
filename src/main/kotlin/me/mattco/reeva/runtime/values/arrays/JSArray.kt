package me.mattco.reeva.runtime.values.arrays

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.objects.JSObject

class JSArray private constructor(realm: Realm) : JSObject(realm, realm.arrayProto) {
    companion object {
        fun create(realm: Realm) = JSArray(realm).also { it.init() }
    }
}
