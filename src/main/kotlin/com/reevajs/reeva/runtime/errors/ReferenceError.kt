package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject

class JSReferenceErrorObject private constructor(
    realm: Realm,
    message: String? = null,
) : JSErrorObject(realm, message, realm.referenceErrorProto) {
    companion object {
        @JvmStatic
        fun create(message: String? = null, realm: Realm = Agent.activeAgent.getActiveRealm()) = JSReferenceErrorObject(realm, message).initialize()
    }
}

class JSReferenceErrorProto private constructor(realm: Realm) : JSErrorProto(
    realm, realm.referenceErrorCtor, realm.errorProto, "ReferenceError"
) {
    override fun init() {
        super.init()
        defineOwnProperty("constructor", realm.referenceErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSReferenceErrorProto(realm).initialize()
    }
}

class JSReferenceErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "ReferenceError") {
    override fun errorProto(): JSObject = realm.referenceErrorProto

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSReferenceErrorCtor(realm).initialize()
    }
}
