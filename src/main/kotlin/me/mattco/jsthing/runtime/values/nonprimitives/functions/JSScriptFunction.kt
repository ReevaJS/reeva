package me.mattco.jsthing.runtime.values.nonprimitives.functions

import me.mattco.jsthing.runtime.Realm
import me.mattco.jsthing.runtime.contexts.ExecutionContext
import me.mattco.jsthing.runtime.environment.EnvRecord
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject

class JSScriptFunction(
    realm: Realm,
    thisMode: ThisMode,
    val strict: Boolean,
    val isClassCtor: Boolean,
    val homeObject: JSObject,
    private val name: String,
) : JSFunction(realm, thisMode) {
    override fun name() = name

    override fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        TODO("Not yet implemented")
    }

    override fun construct(arguments: List<JSValue>, newTarget: JSObject): JSValue {
        TODO("Not yet implemented")
    }
}
