package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSNumber

open class JSNumberObject protected constructor(realm: Realm, val number: JSNumber) : JSObject(realm) {
    override fun init() {
        internalSetPrototype(realm.numberProto)
        super.init()
    }

    companion object {
        fun create(realm: Realm, number: JSNumber) = JSNumberObject(realm, number).also { it.init() }
    }
}
