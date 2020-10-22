package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSBoolean

open class JSBooleanObject protected constructor(realm: Realm, val value: JSBoolean) : JSObject(realm) {
    override fun init() {
        internalSetPrototype(realm.booleanProto)
        super.init()
    }

    companion object {
        fun create(realm: Realm, value: JSBoolean) = JSBooleanObject(realm, value).also { it.init() }
    }
}
