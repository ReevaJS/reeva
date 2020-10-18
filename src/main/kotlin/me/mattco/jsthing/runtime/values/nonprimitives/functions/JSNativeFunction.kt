package me.mattco.jsthing.runtime.values.nonprimitives.functions

import me.mattco.jsthing.runtime.Realm
import me.mattco.jsthing.runtime.contexts.ExecutionContext
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject
import me.mattco.jsthing.utils.shouldThrowError

abstract class JSNativeFunction(realm: Realm, private val name: String) : JSFunction(realm, ThisMode.Global) {
    override fun name() = name

    companion object {
        fun fromLambda(realm: Realm, name: String, lambda: NativeFunctionSignature) = object : JSNativeFunction(realm, name) {
            override fun call(context: ExecutionContext, arguments: List<JSValue>) = lambda(context, arguments)

            override fun construct(context: ExecutionContext, newTarget: JSObject, arguments: List<JSValue>): JSValue {
                shouldThrowError()
            }
        }
    }
}
