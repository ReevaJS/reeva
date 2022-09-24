package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject

class JSReferenceErrorObject private constructor(
    realm: Realm,
    message: String? = null,
) : JSErrorObject(realm, message, realm.referenceErrorProto) {
    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) = JSReferenceErrorObject(realm, message).initialize(realm)
    }
}

class JSReferenceErrorProto private constructor(realm: Realm) : JSErrorProto(
    realm.referenceErrorCtor, realm.errorProto, "ReferenceError"
) {
    override fun init(realm: Realm) {
        super.init(realm)
        defineOwnProperty("constructor", realm.referenceErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm) = JSReferenceErrorProto(realm).initialize(realm)
    }
}

class JSReferenceErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "ReferenceError") {
    override fun errorProto(realm: Realm): JSObject = realm.referenceErrorProto

    companion object {
        fun create(realm: Realm) = JSReferenceErrorCtor(realm).initialize()
    }
}
