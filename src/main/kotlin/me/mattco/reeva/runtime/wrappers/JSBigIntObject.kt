package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSBigInt

class JSBigIntObject private constructor(realm: Realm, val value: JSBigInt) : JSObject(realm, realm.bigIntProto) {
    companion object {
        fun create(realm: Realm, value: JSBigInt) = JSBigIntObject(realm, value).initialize()
    }
}
