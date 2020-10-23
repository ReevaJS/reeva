package me.mattco.reeva.runtime.values.errors

import me.mattco.reeva.runtime.Realm

class JSURIErrorObject private constructor(realm: Realm, message: String? = null) : JSErrorObject(realm, message, realm.uriErrorProto) {
    override val name = "URIError"

    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) = JSURIErrorObject(realm, message).also { it.init() }
    }
}

class JSURIErrorProto private constructor(realm: Realm) : JSErrorProto(realm, "URIError") {
    companion object {
        fun create(realm: Realm) = JSURIErrorProto(realm).also { it.init() }
    }
}

class JSURIErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "URIError") {
    override fun constructErrorObj(): JSErrorObject {
        return JSURIErrorObject.create(realm)
    }

    companion object {
        fun create(realm: Realm) = JSURIErrorCtor(realm).also { it.init() }
    }
}
