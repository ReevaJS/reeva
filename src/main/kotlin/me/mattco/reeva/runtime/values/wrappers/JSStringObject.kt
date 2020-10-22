package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSString

open class JSStringObject protected constructor(realm: Realm, val string: JSString) : JSObject(realm) {
    override fun init() {
        internalSetPrototype(realm.stringProto)
        super.init()
    }

    companion object {
        fun create(realm: Realm, string: JSString) = JSStringObject(realm, string).also { it.init() }
    }
}
