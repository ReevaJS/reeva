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
        fun create(message: String? = null, realm: Realm = Agent.activeAgent.getActiveRealm()) = JSRangeErrorObject(realm, message).initialize()
    }
}

class JSRangeErrorProto private constructor(realm: Realm) : JSErrorProto(
    realm, realm.rangeErrorCtor, realm.errorProto, "RangeError"
) {
    override fun init() {
        super.init()
        val realm = Agent.activeAgent.getActiveRealm()
        defineOwnProperty("constructor", realm.rangeErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSRangeErrorProto(realm).initialize()
    }
}

class JSRangeErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "RangeError") {
    override fun errorProto(realm: Realm): JSObject = realm.rangeErrorProto

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSRangeErrorCtor(realm).initialize()
    }
}
