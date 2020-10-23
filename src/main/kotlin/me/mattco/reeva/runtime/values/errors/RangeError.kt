package me.mattco.reeva.runtime.values.errors

import me.mattco.reeva.runtime.Realm

class JSRangeErrorObject private constructor(realm: Realm) : JSErrorObject(realm, realm.rangeErrorProto) {
    companion object {
        fun create(realm: Realm) = JSRangeErrorObject(realm).also { it.init() }
    }
}

class JSRangeErrorProto private constructor(realm: Realm) : JSErrorProto(realm, "RangeError") {
    companion object {
        fun create(realm: Realm) = JSRangeErrorProto(realm).also { it.init() }
    }
}

class JSRangeErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "RangeError") {
    override fun constructErrorObj(): JSErrorObject {
        return JSRangeErrorObject.create(realm)
    }

    companion object {
        fun create(realm: Realm) = JSRangeErrorCtor(realm).also { it.init() }
    }
}
