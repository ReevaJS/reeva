package me.mattco.reeva.runtime.values.errors

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.objects.Attributes
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.utils.toValue

open class JSErrorObject protected constructor(
    realm: Realm,
    val message: String? = null,
    errorProto: JSObject = realm.errorProto
) : JSObject(realm, errorProto) {
    override fun init() {
        if (message != null)
            defineOwnProperty("message", Descriptor(message.toValue(), Attributes(Attributes.CONFIGURABLE or Attributes.WRITABLE)))
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) = JSErrorObject(realm, message).also { it.init() }
    }
}
