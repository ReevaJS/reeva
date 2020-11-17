package me.mattco.reeva.runtime.errors

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject

class JSURIErrorObject private constructor(realm: Realm, message: String? = null) : JSErrorObject(realm, message, realm.uriErrorProto) {
    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) = JSURIErrorObject(realm, message).initialize()
    }
}

class JSURIErrorProto private constructor(realm: Realm) : JSObject(realm, realm.errorProto) {
    override fun init() {
        super.init()
        defineOwnProperty("constructor", realm.uriErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm) = JSURIErrorProto(realm).initialize()
    }
}

class JSURIErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "URIError") {
    override fun constructErrorObj(): JSErrorObject {
        return JSURIErrorObject.create(realm)
    }

    companion object {
        fun create(realm: Realm) = JSURIErrorCtor(realm).initialize()
    }
}
