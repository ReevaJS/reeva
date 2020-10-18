package me.mattco.jsthing.runtime.values.nonprimitives.arrays

import me.mattco.jsthing.runtime.Realm
import me.mattco.jsthing.runtime.contexts.ExecutionContext
import me.mattco.jsthing.runtime.environment.EnvRecord
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSNativeFunction

class JSArrayCtor(realm: Realm) : JSNativeFunction(realm, "ArrayConstructor") {
    override fun init() {
        super.init()
    }

    override fun call(context: ExecutionContext, arguments: List<JSValue>): JSValue {
        TODO("Not yet implemented")
    }

    override fun construct(context: ExecutionContext, newTarget: JSObject, arguments: List<JSValue>): JSValue {
        TODO("Not yet implemented")
    }
}
