package me.mattco.reeva.test262

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.objects.JSObject

class Test262GlobalObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    companion object {
        fun create(realm: Realm) = Test262GlobalObject(realm).also { it.init() }
    }
}
