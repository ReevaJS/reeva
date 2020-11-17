package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSBoolean

open class JSBooleanObject protected constructor(realm: Realm, val value: JSBoolean) : JSObject(realm) {
    override fun init() {
        setPrototype(realm.booleanProto)
        super.init()
    }

    companion object {
        fun create(realm: Realm, value: JSBoolean) = JSBooleanObject(realm, value).initialize()
    }
}
