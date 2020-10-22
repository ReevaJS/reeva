package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSBoolean

class JSBooleanObject private constructor(realm: Realm, val value: JSBoolean) : JSObject(realm, realm.booleanProto) {
    companion object {
        fun create(realm: Realm, value: JSBoolean) = JSBooleanObject(realm, value).also { it.init() }
    }
}
