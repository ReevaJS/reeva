package me.mattco.reeva.runtime.values.objects

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.functions.JSNativeFunction

class JSObjectCtor private constructor(realm: Realm) : JSNativeFunction(realm, "ObjectConstructor", 1) {
    override fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        TODO("Not yet implemented")
    }

    override fun construct(arguments: List<JSValue>, newTarget: JSValue): JSValue {
        TODO("Not yet implemented")
    }

    companion object {
        fun create(realm: Realm) = JSObjectCtor(realm).also { it.init() }
    }
}
