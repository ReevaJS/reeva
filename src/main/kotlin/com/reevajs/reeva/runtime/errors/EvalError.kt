package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject

class JSEvalErrorObject private constructor(
    realm: Realm,
    message: String? = null,
) : JSErrorObject(realm, message, realm.evalErrorProto) {
    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) = JSEvalErrorObject(realm, message).initialize()
    }
}

class JSEvalErrorProto private constructor(realm: Realm) : JSErrorProto(
    realm, realm.evalErrorCtor, realm.errorProto, "EvalError"
) {
    override fun init() {
        super.init()
        defineOwnProperty("constructor", realm.evalErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm) = JSEvalErrorProto(realm).initialize()
    }
}

class JSEvalErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "EvalError") {
    override fun errorProto(): JSObject = realm.evalErrorProto

    companion object {
        fun create(realm: Realm) = JSEvalErrorCtor(realm).initialize()
    }
}
