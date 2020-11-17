package me.mattco.reeva.runtime.errors

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.utils.toValue

open class JSErrorObject protected constructor(
    realm: Realm,
    val message: String? = null,
    errorProto: JSObject = realm.errorProto
) : JSObject(realm, errorProto) {
    override fun init() {
        if (message != null)
            defineOwnProperty("message", message.toValue(), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) = JSErrorObject(realm, message).initialize()
    }
}
