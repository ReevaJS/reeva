package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject

class JSClassInstanceObject private constructor(
    realm: Realm,
    private val prototype: JSObject,
    val obj: Any,
) : JSObject(realm, prototype) {
    override fun init() {
        super.init()
        defineOwnProperty("prototype", prototype, Descriptor.HAS_BASIC)
    }

    companion object {
        fun create(realm: Realm, prototype: JSObject, obj: Any) =
            JSClassInstanceObject(realm, prototype, obj).initialize()
    }
}
