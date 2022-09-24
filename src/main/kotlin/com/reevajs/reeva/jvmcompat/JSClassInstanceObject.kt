package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.JSObject

class JSClassInstanceObject private constructor(
    prototype: JSValue,
    val obj: Any,
) : JSObject(prototype) {
    companion object {
        fun create(realm: Realm, prototype: JSValue, obj: Any) = JSClassInstanceObject(prototype, obj).initialize(realm)

        fun wrap(realm: Realm, obj: Any): JSClassInstanceObject {
            val clazz = JSClassObject.create(realm, obj::class.java)
            return create(realm, clazz.clazzProto, obj)
        }
    }
}
