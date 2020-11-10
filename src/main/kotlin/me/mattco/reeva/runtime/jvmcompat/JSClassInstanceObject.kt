package me.mattco.reeva.runtime.jvmcompat

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject

class JSClassInstanceObject private constructor(realm: Realm, prototype: JSObject, val obj: Any) : JSObject(realm, prototype) {
    companion object {
        fun create(realm: Realm, prototype: JSObject, obj: Any) = JSClassInstanceObject(realm, prototype, obj).also { it.init() }
    }
}
