package me.mattco.reeva.jvmcompat

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject

class JSClassInstanceObject private constructor(realm: Realm, private val prototype: JSObject, val obj: Any) : JSObject(realm, prototype) {
    override fun init() {
        super.init()

        defineOwnProperty("prototype", prototype, 0)
    }

    companion object {
        fun create(realm: Realm, prototype: JSObject, obj: Any) = JSClassInstanceObject(realm, prototype, obj).initialize()
    }
}
