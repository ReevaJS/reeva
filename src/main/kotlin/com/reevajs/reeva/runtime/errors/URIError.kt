package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject

class JSURIErrorObject private constructor(
    realm: Realm,
    message: String? = null,
) : JSErrorObject(realm, message, realm.uriErrorProto) {
    companion object {
        @JvmStatic
        fun create(message: String? = null, realm: Realm = Agent.activeAgent.getActiveRealm()) = JSURIErrorObject(realm, message).initialize()
    }
}

class JSURIErrorProto private constructor(realm: Realm) : JSErrorProto(
    realm, realm.uriErrorCtor, realm.errorProto, "URIError"
) {
    override fun init() {
        super.init()
        val realm = Agent.activeAgent.getActiveRealm()
        defineOwnProperty("constructor", realm.uriErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSURIErrorProto(realm).initialize()
    }
}

class JSURIErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "URIError") {
    override fun errorProto(realm: Realm): JSObject = realm.uriErrorProto

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSURIErrorCtor(realm).initialize()
    }
}
