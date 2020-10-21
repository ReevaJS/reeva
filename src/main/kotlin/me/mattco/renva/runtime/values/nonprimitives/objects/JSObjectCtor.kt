package me.mattco.renva.runtime.values.nonprimitives.objects

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.values.JSValue
import me.mattco.renva.runtime.values.nonprimitives.functions.JSNativeFunction

class JSObjectCtor(realm: Realm) : JSNativeFunction(realm, "ObjectConstructor") {
    override fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        TODO("Not yet implemented")
    }

    override fun construct(arguments: List<JSValue>, newTarget: JSObject): JSValue {
        TODO("Not yet implemented")
    }
}
