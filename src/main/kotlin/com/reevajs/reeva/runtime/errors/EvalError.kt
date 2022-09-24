package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject

class JSEvalErrorObject private constructor(
    realm: Realm,
    message: String? = null,
) : JSErrorObject(realm, message, realm.evalErrorProto) {
    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) = JSEvalErrorObject(realm, message).initialize(realm)
    }
}

class JSEvalErrorProto private constructor(realm: Realm) : JSErrorProto(
    realm.evalErrorCtor, realm.errorProto, "EvalError"
) {
    override fun init(realm: Realm) {
        super.init(realm)
        defineOwnProperty("constructor", realm.evalErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm) = JSEvalErrorProto(realm).initialize(realm)
    }
}

class JSEvalErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "EvalError") {
    override fun errorProto(realm: Realm): JSObject = realm.evalErrorProto

    companion object {
        fun create(realm: Realm) = JSEvalErrorCtor(realm).initialize()
    }
}
