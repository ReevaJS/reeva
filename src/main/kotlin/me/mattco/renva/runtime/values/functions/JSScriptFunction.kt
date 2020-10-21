package me.mattco.renva.runtime.values.functions

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.values.JSValue
import me.mattco.renva.runtime.values.objects.JSObject

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
