package me.mattco.renva.runtime.values.arrays

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.values.JSValue
import me.mattco.renva.runtime.values.objects.JSObject
import me.mattco.renva.runtime.values.functions.JSNativeFunction

class JSArrayCtor(realm: Realm) : JSNativeFunction(realm, "ArrayConstructor") {
    override fun init() {
        super.init()
    }

    override fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        TODO("Not yet implemented")
    }

    override fun construct(arguments: List<JSValue>, newTarget: JSObject): JSValue {
        TODO("Not yet implemented")
    }
}
