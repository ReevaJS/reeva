package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject

class JSTypeErrorObject private constructor(
    realm: Realm,
    message: String? = null,
) : JSErrorObject(realm, message, realm.typeErrorProto) {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun create(message: String? = null, realm: Realm = Agent.activeAgent.getActiveRealm()) = JSTypeErrorObject(realm, message).initialize()
    }
}

class JSTypeErrorProto private constructor(realm: Realm) : JSErrorProto(
    realm, realm.typeErrorCtor, realm.errorProto, "TypeError"
) {
    override fun init() {
        super.init()
        val realm = Agent.activeAgent.getActiveRealm()
        defineOwnProperty("constructor", realm.typeErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSTypeErrorProto(realm).initialize()
    }
}

class JSTypeErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "TypeError") {
    override fun errorProto(realm: Realm): JSObject = realm.typeErrorProto

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSTypeErrorCtor(realm).initialize()
    }
}
