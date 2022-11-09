package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.utils.toValue

open class JSErrorObject protected constructor(
    realm: Realm,
    val message: String? = null,
    errorProto: JSObject = realm.errorProto
) : JSObject(realm, errorProto) {
    init {
        addSlot(Slot.ErrorData, Unit)
    }

    override fun init() {
        defineOwnProperty("message", message.orEmpty().toValue(), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        @JvmStatic
        fun create(message: String? = null, realm: Realm = Agent.activeAgent.getActiveRealm()) = JSErrorObject(realm, message).initialize()
    }
}
