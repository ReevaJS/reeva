package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.toValue

class JSClassInstanceObject private constructor(
    realm: Realm,
    prototype: JSValue,
    val obj: Any,
) : JSObject(realm, prototype) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, (obj::class.qualifiedName ?: "<anonymous>").toValue(), Descriptor.CONFIGURABLE)
    }

    companion object {
        fun create(prototype: JSValue, obj: Any, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSClassInstanceObject(realm, prototype, obj).initialize()

        fun wrap(obj: Any): JSClassInstanceObject {
            val clazz = JSClassObject.create(obj::class.java)
            return create(clazz.clazzProto, obj)
        }
    }
}
