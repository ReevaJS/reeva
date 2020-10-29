package me.mattco.reeva.runtime.errors

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject

class JSTypeErrorObject private constructor(realm: Realm, message: String? = null) : JSErrorObject(realm, message, realm.typeErrorProto) {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun create(realm: Realm, message: String? = null) = JSTypeErrorObject(realm, message).also { it.init() }
    }
}

class JSTypeErrorProto private constructor(realm: Realm) : JSObject(realm, realm.errorProto) {
    override fun init() {
        super.init()
        defineOwnProperty("constructor", realm.typeErrorCtor)
    }

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
