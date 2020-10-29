package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.utils.argument

class JSBooleanCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Boolean", 1) {
    init {
        isConstructable = true
    }

    override fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        return Operations.toBoolean(arguments.argument(0))
    }

    override fun construct(arguments: List<JSValue>, newTarget: JSValue): JSValue {
        return JSBooleanObject.create(realm, Operations.toBoolean(arguments.argument(0)))
    }

    companion object {
        fun create(realm: Realm) = JSBooleanCtor(realm).also { it.init() }
    }
}
