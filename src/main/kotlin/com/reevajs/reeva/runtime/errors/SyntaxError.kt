package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject

class JSSyntaxErrorObject private constructor(
    realm: Realm,
    message: String? = null,
) : JSErrorObject(realm, message, realm.syntaxErrorProto) {
    companion object {
        @JvmStatic
        fun create(message: String? = null, realm: Realm = Agent.activeAgent.getActiveRealm()) = JSSyntaxErrorObject(realm, message).initialize()
    }
}

class JSSyntaxErrorProto private constructor(realm: Realm) : JSErrorProto(
    realm, realm.syntaxErrorCtor, realm.errorProto, "SyntaxError"
) {
    override fun init() {
        super.init()
        val realm = Agent.activeAgent.getActiveRealm()
        defineOwnProperty("constructor", realm.syntaxErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSSyntaxErrorProto(realm).initialize()
    }
}

class JSSyntaxErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "SyntaxError") {
    override fun errorProto(): JSObject = realm.syntaxErrorProto

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSSyntaxErrorCtor(realm).initialize()
    }
}
