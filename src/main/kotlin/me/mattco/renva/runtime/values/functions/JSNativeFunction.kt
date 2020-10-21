package me.mattco.renva.runtime.values.functions

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.values.JSValue
import me.mattco.renva.runtime.values.objects.JSObject
import me.mattco.renva.utils.shouldThrowError

abstract class JSNativeFunction(realm: Realm, private val name: String) : JSFunction(realm, ThisMode.Global) {
    override fun name() = name

    companion object {
        fun fromLambda(realm: Realm, name: String, lambda: NativeFunctionSignature) = object : JSNativeFunction(realm, name) {
            override fun call(thisValue: JSValue, arguments: List<JSValue>) = lambda(thisValue, arguments)

            override fun construct(arguments: List<JSValue>, newTarget: JSObject): JSValue {
                shouldThrowError()
            }
        }
    }
}
