package me.mattco.reeva.runtime.values.errors

import me.mattco.reeva.runtime.Realm

class JSEvalErrorObject private constructor(realm: Realm) : JSErrorObject(realm, realm.evalErrorProto) {
    companion object {
        fun create(realm: Realm) = JSEvalErrorObject(realm).also { it.init() }
    }
}

class JSEvalErrorProto private constructor(realm: Realm) : JSErrorProto(realm, "EvalError") {
    override fun init() {
        super.init()
    }

    companion object {
        fun create(realm: Realm) = JSEvalErrorProto(realm).also { it.init() }
    }
}

class JSEvalErrorCtor private constructor(realm: Realm) : JSErrorCtor(realm, "EvalError") {
    override fun constructErrorObj(): JSErrorObject {
        return JSEvalErrorObject.create(realm)
    }

    companion object {
        fun create(realm: Realm) = JSEvalErrorCtor(realm).also { it.init() }
    }
}
