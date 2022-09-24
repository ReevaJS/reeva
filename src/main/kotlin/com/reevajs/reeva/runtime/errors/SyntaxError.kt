package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject

class JSSyntaxErrorObject private constructor(
    realm: Realm,
    message: String? = null,
) : JSErrorObject(realm, message, realm.syntaxErrorProto) {
    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) = JSSyntaxErrorObject(realm, message).initialize(realm)
    }
}

class JSSyntaxErrorProto private constructor(realm: Realm) : JSErrorProto(
    realm.syntaxErrorCtor, realm.errorProto, "SyntaxError"
) {
    override fun init(realm: Realm) {
        super.init(realm)
        defineOwnProperty("constructor", realm.syntaxErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm) = JSSyntaxErrorProto(realm).initialize(realm)
    }
}

class JSSyntaxErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "SyntaxError") {
    override fun errorProto(realm: Realm): JSObject = realm.syntaxErrorProto

    companion object {
        fun create(realm: Realm) = JSSyntaxErrorCtor(realm).initialize()
    }
}
