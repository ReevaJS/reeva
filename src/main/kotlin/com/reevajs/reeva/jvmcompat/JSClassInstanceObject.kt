package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject

class JSClassInstanceObject private constructor(
    realm: Realm,
    prototype: JSValue,
    val obj: Any,
) : JSObject(realm, prototype) {
    companion object {
        fun create(realm: Realm, prototype: JSValue, obj: Any) =
            JSClassInstanceObject(realm, prototype, obj).initialize()

        fun wrap(realm: Realm, obj: Any): JSClassInstanceObject {
            val clazz = JSClassObject.create(realm, obj::class.java)
            return create(realm, clazz.clazzProto, obj)
        }
    }
}
