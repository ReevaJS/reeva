package me.mattco.reeva.runtime.values.objects

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.functions.JSNativeFunction
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument

class JSObjectCtor private constructor(realm: Realm) : JSNativeFunction(realm, "ObjectConstructor", 1) {
    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        val value = arguments.argument(0)
        if (value.isUndefined || value.isNull)
            return JSObject.create(realm)
        return Operations.toObject(value)
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        TODO("Not yet implemented")
    }

    companion object {
        // Special object: do not init
        fun create(realm: Realm) = JSObjectCtor(realm)
    }
}
