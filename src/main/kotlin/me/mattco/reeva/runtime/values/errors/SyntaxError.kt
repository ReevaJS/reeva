package me.mattco.reeva.runtime.values.errors

import me.mattco.reeva.runtime.Realm

class JSSyntaxErrorObject private constructor(realm: Realm, message: String? = null) : JSErrorObject(realm, message, realm.syntaxErrorProto) {
    override val name = "SyntaxError"

    companion object {
        @JvmStatic
        fun create(realm: Realm, message: String? = null) = JSSyntaxErrorObject(realm, message).also { it.init() }
    }
}

class JSSyntaxErrorProto private constructor(realm: Realm) : JSErrorProto(realm, "SyntaxError") {
    companion object {
        fun create(realm: Realm) = JSSyntaxErrorProto(realm).also { it.init() }
    }
}

class JSSyntaxErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "SyntaxError") {
    override fun constructErrorObj(): JSErrorObject {
        return JSSyntaxErrorObject.create(realm)
    }

    companion object {
        fun create(realm: Realm) = JSSyntaxErrorCtor(realm).also { it.init() }
    }
}
