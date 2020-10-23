package me.mattco.reeva.runtime.values.errors

import me.mattco.reeva.runtime.Realm

class JSTypeErrorObject private constructor(realm: Realm) : JSErrorObject(realm, realm.typeErrorProto) {
    companion object {
        fun create(realm: Realm) = JSTypeErrorObject(realm).also { it.init() }
    }
}

class JSTypeErrorProto private constructor(realm: Realm) : JSErrorProto(realm, "TypeError") {
    companion object {
        fun create(realm: Realm) = JSTypeErrorProto(realm).also { it.init() }
    }
}

class JSTypeErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "TypeError") {
    override fun constructErrorObj(): JSErrorObject {
        return JSTypeErrorObject.create(realm)
    }

    companion object {
        fun create(realm: Realm) = JSTypeErrorCtor(realm).also { it.init() }
    }
}
