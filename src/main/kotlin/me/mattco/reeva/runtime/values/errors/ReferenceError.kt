package me.mattco.reeva.runtime.values.errors

import me.mattco.reeva.runtime.Realm

class JSReferenceErrorObject private constructor(realm: Realm, message: String? = null) : JSErrorObject(realm, message, realm.referenceErrorProto) {
    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) = JSReferenceErrorObject(realm, message).also { it.init() }
    }
}

class JSReferenceErrorProto private constructor(realm: Realm) : JSErrorProto(realm, "ReferenceError") {
    companion object {
        fun create(realm: Realm) = JSReferenceErrorProto(realm).also { it.init() }
    }
}

class JSReferenceErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "ReferenceError") {
    override fun constructErrorObj(): JSErrorObject {
        return JSReferenceErrorObject.create(realm)
    }

    companion object {
        fun create(realm: Realm) = JSReferenceErrorCtor(realm).also { it.init() }
    }
}
