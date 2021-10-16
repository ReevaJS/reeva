package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject

class JSInternalErrorObject private constructor(
    realm: Realm,
    message: String? = null,
) : JSErrorObject(realm, message, realm.internalErrorProto) {
    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) = JSInternalErrorObject(realm, message).initialize()
    }
}

class JSInternalErrorProto private constructor(realm: Realm) : JSErrorProto(
    realm, realm.internalErrorCtor, realm.errorProto, "InternalError"
) {
    override fun init() {
        super.init()
        defineOwnProperty("constructor", realm.evalErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm) = JSInternalErrorProto(realm).initialize()
    }
}

class JSInternalErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "InternalError") {
    override fun errorProto(): JSObject = realm.internalErrorProto

    companion object {
        fun create(realm: Realm) = JSInternalErrorCtor(realm).initialize()
    }
}
