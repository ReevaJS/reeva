package me.mattco.reeva.runtime.errors

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject

class JSRangeErrorObject private constructor(realm: Realm, message: String? = null) : JSErrorObject(realm, message, realm.rangeErrorProto) {
    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) = JSRangeErrorObject(realm, message).also { it.init() }
    }
}

class JSRangeErrorProto private constructor(realm: Realm) : JSObject(realm, realm.errorProto) {
    override fun init() {
        super.init()
        defineOwnProperty("constructor", realm.rangeErrorCtor)
    }

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
