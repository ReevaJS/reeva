package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSNumber

class JSNumberObject(realm: Realm, val number: JSNumber) : JSObject(realm, realm.numberProto) {
    companion object {
        fun create(realm: Realm, number: JSNumber) = JSNumberObject(realm, number).also { it.init() }
    }
}
