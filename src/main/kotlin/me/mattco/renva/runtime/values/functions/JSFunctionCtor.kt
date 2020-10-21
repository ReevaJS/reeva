package me.mattco.renva.runtime.values.functions

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.values.JSValue
import me.mattco.renva.runtime.values.objects.JSObject

class JSFunctionCtor private constructor(realm: Realm) : JSNativeFunction(realm, "FunctionConstructor") {
    override fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        TODO("Not yet implemented")
    }

    override fun construct(arguments: List<JSValue>, newTarget: JSObject): JSValue {
        TODO("Not yet implemented")
    }

    companion object {
        fun create(realm: Realm) = JSFunctionCtor(realm).also { it.init() }
    }
}
