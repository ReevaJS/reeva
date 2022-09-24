package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject

class JSRangeErrorObject private constructor(
    realm: Realm,
    message: String? = null,
) : JSErrorObject(realm, message, realm.rangeErrorProto) {
    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) =
            JSRangeErrorObject(realm, message).initialize(realm)
    }
}

class JSRangeErrorProto private constructor(realm: Realm) : JSErrorProto(
    realm.rangeErrorCtor, realm.errorProto, "RangeError"
) {
    override fun init(realm: Realm) {
        super.init(realm)
        defineOwnProperty("constructor", realm.rangeErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm) = JSRangeErrorProto(realm).initialize(realm)
    }
}

class JSRangeErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "RangeError") {
    override fun errorProto(realm: Realm): JSObject = realm.rangeErrorProto

    companion object {
        fun create(realm: Realm) = JSRangeErrorCtor(realm).initialize()
    }
}
