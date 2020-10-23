package me.mattco.reeva.runtime.values.errors

import me.mattco.reeva.runtime.Realm

class JSSyntaxErrorObject private constructor(realm: Realm) : JSErrorObject(realm, realm.syntaxErrorProto) {
    companion object {
        fun create(realm: Realm) = JSSyntaxErrorObject(realm).also { it.init() }
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
