package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Agent
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
        fun create(prototype: JSValue, obj: Any, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSClassInstanceObject(realm, prototype, obj).initialize()

        fun wrap(obj: Any): JSClassInstanceObject {
            val clazz = JSClassObject.create(obj::class.java)
            return create(clazz.clazzProto, obj)
        }
    }
}
