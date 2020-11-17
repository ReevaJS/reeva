package me.mattco.reeva.runtime.errors

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject

class JSTypeErrorObject private constructor(realm: Realm, message: String? = null) : JSErrorObject(realm, message, realm.typeErrorProto) {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun create(realm: Realm, message: String? = null) = JSTypeErrorObject(realm, message).initialize()
    }
}

class JSTypeErrorProto private constructor(realm: Realm) : JSObject(realm, realm.errorProto) {
    override fun init() {
        super.init()
        defineOwnProperty("constructor", realm.typeErrorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm) = JSTypeErrorProto(realm).initialize()
    }
}

class JSTypeErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "TypeError") {
    override fun constructErrorObj(): JSErrorObject {
        return JSTypeErrorObject.create(realm)
    }

    companion object {
        fun create(realm: Realm) = JSTypeErrorCtor(realm).initialize()
    }
}
