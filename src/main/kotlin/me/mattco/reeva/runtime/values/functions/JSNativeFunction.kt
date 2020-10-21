package me.mattco.reeva.runtime.values.functions

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.utils.shouldThrowError

abstract class JSNativeFunction protected constructor(realm: Realm, private val name: String) : JSFunction(realm, ThisMode.Global) {
    override fun name() = name

    companion object {
        fun fromLambda(realm: Realm, name: String, lambda: NativeFunctionSignature) = object : JSNativeFunction(realm, name) {
            override fun call(thisValue: JSValue, arguments: List<JSValue>) = lambda(thisValue, arguments)

            override fun construct(arguments: List<JSValue>, newTarget: JSObject): JSValue {
                shouldThrowError()
            }
        }.also { it.init() }
    }
}
