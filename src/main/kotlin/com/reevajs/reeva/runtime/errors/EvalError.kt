package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject

class JSEvalErrorObject private constructor(
    realm: Realm,
    message: String? = null,
) : JSErrorObject(realm, message, realm.evalErrorProto) {
    companion object {
        @JvmStatic
        fun create(message: String? = null, realm: Realm = Agent.activeAgent.getActiveRealm()) = JSEvalErrorObject(realm, message).initialize()
    }
}

class JSEvalErrorProto private constructor(realm: Realm) : JSErrorProto(
    realm, realm.evalErrorCtor, realm.errorProto, "EvalError"
) {
    override fun init() {
        super.init()
        val realm = Agent.activeAgent.getActiveRealm()
        defineOwnProperty("constructor", realm.evalErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSEvalErrorProto(realm).initialize()
    }
}

class JSEvalErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "EvalError") {
    override fun errorProto(realm: Realm): JSObject = realm.evalErrorProto

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSEvalErrorCtor(realm).initialize()
    }
}
